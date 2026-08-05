#!/usr/bin/env python3
"""Validate spec-dev behavioral eval cases and optional recorded runs."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

try:
    from jsonschema import Draft202012Validator, FormatChecker
except ImportError:  # pragma: no cover - reported as a tool error in main
    Draft202012Validator = None
    FormatChecker = None


SCHEMA_ROOT = Path(__file__).resolve().parent.parent / "schemas"
SKILL_ROOT = Path(__file__).resolve().parent.parent


FORMAT_CHECKER = FormatChecker() if FormatChecker is not None else None


if FORMAT_CHECKER is not None:
    @FORMAT_CHECKER.checks("date-time")
    def is_rfc3339_date_time(value: object) -> bool:
        if not isinstance(value, str) or "T" not in value.upper():
            return False
        normalized = f"{value[:-1]}+00:00" if value.upper().endswith("Z") else value
        try:
            parsed = datetime.fromisoformat(normalized)
        except ValueError:
            return False
        return parsed.tzinfo is not None and parsed.utcoffset() is not None


def load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def schema_errors(data: Any, schema_name: str, source: str) -> list[str]:
    if Draft202012Validator is None:
        return ["The 'jsonschema' Python package is required; schema validation was not run"]
    schema = load(SCHEMA_ROOT / schema_name)
    validator = Draft202012Validator(schema, format_checker=FORMAT_CHECKER)
    return [
        f"{source}: {'.'.join(str(part) for part in error.absolute_path) or '<root>'}: {error.message}"
        for error in sorted(validator.iter_errors(data), key=lambda item: list(item.absolute_path))
    ]


def canonical_digest(data: Any) -> str:
    encoded = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def candidate_identity(skill_root: Path = SKILL_ROOT) -> tuple[str, int]:
    digest = hashlib.sha256()
    files: list[Path] = []
    for path in skill_root.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(skill_root)
        parts = relative.parts
        if "__pycache__" in parts or path.suffix == ".pyc":
            continue
        if len(parts) >= 2 and parts[0] == "evals" and parts[1] in {"runs", "traces"}:
            continue
        files.append(path)
    for path in sorted(files, key=lambda item: item.relative_to(skill_root).as_posix()):
        relative = path.relative_to(skill_root).as_posix().encode("utf-8")
        digest.update(relative)
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest(), len(files)


def validate_case(data: Any, source: str) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return [f"{source}: case must be an object"]
    required = ["id", "prompt", "expectedInvariants", "prohibitedBehaviors", "tags"]
    for key in required:
        if key not in data:
            errors.append(f"{source}: missing {key}")
    if not isinstance(data.get("id"), str) or not data.get("id", "").strip():
        errors.append(f"{source}: id must be a non-empty string")
    if not isinstance(data.get("prompt"), str) or not data.get("prompt", "").strip():
        errors.append(f"{source}: prompt must be a non-empty string")
    all_assertion_ids: set[str] = set()
    for key in ("expectedInvariants", "prohibitedBehaviors"):
        value = data.get(key)
        if not isinstance(value, list):
            errors.append(f"{source}: {key} must be an array")
            continue
        for index, item in enumerate(value):
            if not isinstance(item, dict) or not str(item.get("id", "")).strip() or not str(item.get("statement", "")).strip():
                errors.append(f"{source}: {key}[{index}] requires id and statement")
                continue
            if set(item) - {"id", "statement"}:
                errors.append(f"{source}: {key}[{index}] has unknown fields")
            if item["id"] in all_assertion_ids:
                errors.append(f"{source}: duplicate assertion id {item['id']}")
            all_assertion_ids.add(item["id"])
    tags = data.get("tags")
    if not isinstance(tags, list) or any(not isinstance(item, str) or not item.strip() for item in tags):
        errors.append(f"{source}: tags must be an array of non-empty strings")
    if isinstance(data.get("expectedInvariants"), list) and not data["expectedInvariants"]:
        errors.append(f"{source}: expectedInvariants must not be empty")
    if isinstance(data.get("tags"), list) and not data["tags"]:
        errors.append(f"{source}: tags must not be empty")
    allowed = {"id", "prompt", "fixture", "expectedInvariants", "prohibitedBehaviors", "tags"}
    unknown = sorted(set(data) - allowed)
    if unknown:
        errors.append(f"{source}: unknown fields: {', '.join(unknown)}")
    return errors


def validate_run(data: Any, source: str, cases: dict[str, dict[str, Any]], skill_root: Path) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return [f"{source}: run must be an object"]
    case = cases.get(data.get("caseId"))
    if case is None:
        errors.append(f"{source}: unknown caseId {data.get('caseId')}")
    elif data.get("caseDigest") != canonical_digest(case):
        errors.append(f"{source}: caseDigest does not match the recorded case content")

    expected_candidate_digest, expected_file_count = candidate_identity(skill_root)
    candidate = data.get("candidate")
    if isinstance(candidate, dict):
        if candidate.get("digest") != expected_candidate_digest:
            errors.append(f"{source}: candidate digest does not match the current skill")
        if candidate.get("fileCount") != expected_file_count:
            errors.append(f"{source}: candidate fileCount does not match the current skill")

    trace = data.get("trace")
    if isinstance(trace, str):
        trace_path = skill_root / trace
        try:
            trace_path.resolve().relative_to(skill_root.resolve())
        except ValueError:
            errors.append(f"{source}: trace escapes the skill directory")
        else:
            if not trace_path.is_file():
                errors.append(f"{source}: trace artifact does not exist: {trace}")
            else:
                try:
                    trace_bytes = trace_path.read_bytes()
                    trace_bytes.decode("utf-8")
                except (OSError, UnicodeError) as exc:
                    errors.append(f"{source}: trace artifact is unreadable: {exc}")
                else:
                    trace_digest = hashlib.sha256(trace_bytes).hexdigest()
                    if data.get("traceDigest") != trace_digest:
                        errors.append(f"{source}: traceDigest does not match the durable trace artifact")

    assertions = data.get("assertions")
    if isinstance(assertions, list):
        seen: set[str] = set()
        for index, assertion in enumerate(assertions):
            if not isinstance(assertion, dict):
                continue
            assertion_id = assertion.get("id")
            if isinstance(assertion_id, str) and assertion_id in seen:
                errors.append(f"{source}: duplicate assertion id {assertion['id']}")
            elif isinstance(assertion_id, str):
                seen.add(assertion_id)
        if case:
            expected = {
                item["id"]
                for key in ("expectedInvariants", "prohibitedBehaviors")
                for item in case[key]
            }
            missing = sorted(expected - seen)
            unknown = sorted(seen - expected)
            if missing:
                errors.append(f"{source}: missing assertions: {', '.join(missing)}")
            if unknown:
                errors.append(f"{source}: unknown assertions: {', '.join(unknown)}")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", default=str(Path(__file__).resolve().parent.parent / "evals" / "cases"))
    parser.add_argument("--runs", help="Optional directory containing recorded run JSON files")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)

    if Draft202012Validator is None:
        payload = {
            "schemaVersion": 1,
            "valid": False,
            "error": "The 'jsonschema' Python package is required; evaluation schemas cannot be skipped",
        }
        if args.json:
            print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
        else:
            print(payload["error"])
        return 2

    case_dir = Path(args.cases).resolve()
    errors: list[str] = []
    cases: dict[str, dict[str, Any]] = {}
    case_files = sorted(case_dir.glob("*.json"))
    if not case_files:
        errors.append(f"No eval cases found in {case_dir}")
    for path in case_files:
        try:
            data = load(path)
            errors.extend(schema_errors(data, "eval-case.schema.json", str(path)))
            errors.extend(validate_case(data, str(path)))
            case_id = data.get("id") if isinstance(data, dict) else None
            if isinstance(case_id, str):
                if case_id in cases:
                    errors.append(f"{path}: duplicate case id {case_id}")
                cases[case_id] = data
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"{path}: {exc}")

    run_count = 0
    if args.runs:
        for path in sorted(Path(args.runs).resolve().glob("*.json")):
            run_count += 1
            try:
                data = load(path)
                errors.extend(schema_errors(data, "eval-run.schema.json", str(path)))
                errors.extend(validate_run(data, str(path), cases, SKILL_ROOT))
            except (OSError, json.JSONDecodeError) as exc:
                errors.append(f"{path}: {exc}")

    payload = {
        "schemaVersion": 1,
        "caseDirectory": str(case_dir),
        "cases": len(case_files),
        "runs": run_count,
        "valid": not errors,
        "errors": errors,
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        print(f"Eval cases: {payload['cases']}; runs: {run_count}; valid: {payload['valid']}")
        for error in errors:
            print(f"- {error}")
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
