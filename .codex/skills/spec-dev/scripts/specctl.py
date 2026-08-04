#!/usr/bin/env python3
"""Read-only structural checks for outcome-focused spec changes."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

try:
    from jsonschema import Draft202012Validator, FormatChecker
except ImportError:  # pragma: no cover - built-in checks remain available
    Draft202012Validator = None
    FormatChecker = None


SCHEMA_VERSION = 1
CHANGE_ID = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
OUTCOME_ID = re.compile(r"^\S+$")
KINDS = {"feature", "bugfix", "refactor", "tooling", "documentation"}
OPERATIONS = {"added", "modified", "removed", "unchanged"}
RESULTS = {"passed", "failed", "partial", "not_run"}
SEVERITIES = {"critical", "high", "medium", "low", "info"}
FINDING_STATUS = {"open", "resolved", "accepted-risk"}
SCHEMA_ROOT = Path(__file__).resolve().parent.parent / "schemas"


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


@dataclass(frozen=True)
class LocatedChange:
    change_id: str
    directory: Path
    metadata_path: Path


class InputError(RuntimeError):
    pass


def finding(
    rule_id: str,
    message: str,
    *,
    severity: str = "high",
    blocking: bool = True,
    owner: str = "repository",
    artifact: str | None = None,
    outcome_ids: Iterable[str] | None = None,
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "id": rule_id,
        "check": rule_id,
        "severity": severity,
        "blocking": blocking,
        "owner": owner,
        "message": message,
        "status": "open",
    }
    if artifact:
        result["artifact"] = artifact
    if outcome_ids:
        result["outcomeIds"] = sorted(set(outcome_ids))
    return result


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise InputError(f"Missing file: {path}") from exc
    except json.JSONDecodeError as exc:
        raise InputError(f"Invalid JSON in {path}: {exc}") from exc


def schema_findings(
    payload: Any, schema_name: str, *, artifact: str, owner: str = "repository"
) -> list[dict[str, Any]]:
    if Draft202012Validator is None:
        raise InputError(
            "The 'jsonschema' Python package is required for specctl schema validation. "
            "Install it in the execution environment before running this command."
        )
    schema = load_json(SCHEMA_ROOT / schema_name)
    validator = Draft202012Validator(schema, format_checker=FORMAT_CHECKER)
    findings: list[dict[str, Any]] = []
    for index, error in enumerate(sorted(validator.iter_errors(payload), key=lambda item: list(item.absolute_path))):
        location = ".".join(str(part) for part in error.absolute_path) or "<root>"
        findings.append(
            finding(
                f"schema.{schema_name}.{index}",
                f"{location}: {error.message}",
                owner=owner,
                artifact=artifact,
            )
        )
    return findings


def inside(base: Path, candidate: Path) -> bool:
    base_absolute = Path(os.path.abspath(base))
    candidate_absolute = Path(os.path.abspath(candidate))
    try:
        candidate_absolute.relative_to(base_absolute)
    except ValueError:
        return False
    resolved_candidate = candidate.resolve()
    resolved_base = base.resolve()
    try:
        resolved_candidate.relative_to(resolved_base)
        return True
    except ValueError:
        if not base.exists():
            return False
        for ancestor in (resolved_candidate, *resolved_candidate.parents):
            try:
                if ancestor.exists() and os.path.samefile(ancestor, base):
                    return True
            except OSError:
                continue
        return False


def display_path(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        resolved = path.resolve()
        if root.exists():
            for ancestor in (resolved, *resolved.parents):
                try:
                    if ancestor.exists() and os.path.samefile(ancestor, root):
                        return str(resolved.relative_to(ancestor))
                except OSError:
                    continue
        return str(path)


def find_spec_root(root: Path, explicit: str | None) -> Path:
    return (root / explicit).resolve() if explicit else (root / "docs" / "spec").resolve()


def locate_change(spec_root: Path, change_id: str) -> LocatedChange:
    candidates = [spec_root / "changes" / change_id, spec_root / change_id]
    for directory in candidates:
        metadata = directory / "change.json"
        if metadata.is_file():
            return LocatedChange(change_id, directory.resolve(), metadata.resolve())
    raise InputError(f"Change '{change_id}' was not found under {spec_root}")


def discover_changes(spec_root: Path) -> list[LocatedChange]:
    found: dict[str, LocatedChange] = {}
    active_root = spec_root / "changes"
    if active_root.is_dir():
        for metadata in active_root.glob("*/change.json"):
            found[metadata.parent.name] = LocatedChange(
                metadata.parent.name, metadata.parent.resolve(), metadata.resolve()
            )
    if spec_root.is_dir():
        for metadata in spec_root.glob("*/change.json"):
            if metadata.parent.name in {"current", "changes", "archive"}:
                continue
            found.setdefault(
                metadata.parent.name,
                LocatedChange(metadata.parent.name, metadata.parent.resolve(), metadata.resolve()),
            )
    return [found[key] for key in sorted(found)]


def require_type(
    obj: dict[str, Any], key: str, expected: type, issues: list[dict[str, Any]], artifact: str
) -> Any:
    value = obj.get(key)
    if not isinstance(value, expected):
        issues.append(
            finding(
                f"metadata.{key}",
                f"'{key}' must be {expected.__name__}",
                artifact=artifact,
            )
        )
        return None
    return value


def validate_change(located: LocatedChange, root: Path, strict: bool) -> dict[str, Any]:
    issues: list[dict[str, Any]] = []
    artifact = display_path(located.metadata_path, root)
    try:
        data = load_json(located.metadata_path)
    except InputError as exc:
        return {"changeId": located.change_id, "metadata": None, "findings": [finding("metadata.json", str(exc), artifact=artifact)]}

    if not isinstance(data, dict):
        return {"changeId": located.change_id, "metadata": None, "findings": [finding("metadata.object", "change.json must contain an object", artifact=artifact)]}

    issues.extend(schema_findings(data, "change.schema.json", artifact=artifact))

    if data.get("schemaVersion") != SCHEMA_VERSION:
        issues.append(finding("metadata.schemaVersion", "schemaVersion must be 1", artifact=artifact))

    change_id = require_type(data, "id", str, issues, artifact)
    if isinstance(change_id, str):
        if not CHANGE_ID.fullmatch(change_id):
            issues.append(finding("metadata.id-format", "id must be lowercase kebab-case", artifact=artifact))
        if change_id != located.change_id:
            issues.append(finding("metadata.id-directory", f"id '{change_id}' does not match directory '{located.change_id}'", artifact=artifact))

    kind = require_type(data, "kind", str, issues, artifact)
    if isinstance(kind, str) and kind not in KINDS:
        issues.append(finding("metadata.kind", f"Unsupported kind: {kind}", artifact=artifact))

    impact = require_type(data, "specImpact", str, issues, artifact)
    if isinstance(impact, str) and impact not in {"delta", "none"}:
        issues.append(finding("metadata.specImpact", "specImpact must be 'delta' or 'none'", artifact=artifact))

    artifacts = require_type(data, "artifacts", dict, issues, artifact) or {}
    if impact == "delta" and "delta" not in artifacts:
        issues.append(finding("metadata.delta-required", "specImpact 'delta' requires artifacts.delta", owner="delta", artifact=artifact))
    if impact == "none" and not str(data.get("specImpactRationale", "")).strip():
        issues.append(finding("metadata.impact-rationale", "specImpact 'none' requires specImpactRationale", artifact=artifact))

    for name, raw_path in artifacts.items():
        if not isinstance(name, str) or not isinstance(raw_path, str) or not raw_path.strip():
            issues.append(finding("artifact.path-type", "Artifact names and paths must be non-empty strings", artifact=artifact))
            continue
        candidate = located.directory / raw_path
        if not inside(located.directory, candidate):
            issues.append(finding("artifact.path-escape", f"Artifact '{name}' escapes the change directory", artifact=raw_path))
        elif not candidate.is_file():
            issues.append(finding("artifact.missing", f"Artifact '{name}' is missing: {raw_path}", artifact=raw_path))

    current_refs = data.get("currentRefs", [])
    if not isinstance(current_refs, list) or any(not isinstance(item, str) for item in current_refs):
        issues.append(finding("metadata.currentRefs", "currentRefs must be an array of repository-relative paths", artifact=artifact))
        current_refs = []
    current_documents: list[tuple[str, str]] = []
    for raw_path in current_refs:
        candidate = root / raw_path
        if not inside(root, candidate):
            issues.append(finding("current-ref.path-escape", f"Current specification reference escapes the repository: {raw_path}", owner="current-spec", artifact=raw_path))
        elif not candidate.is_file():
            issues.append(
                finding(
                    "current-ref.missing",
                    f"Current specification reference is missing: {raw_path}",
                    severity="high" if strict or impact == "delta" else "medium",
                    blocking=bool(strict or impact == "delta"),
                    owner="current-spec",
                    artifact=raw_path,
                )
            )
        else:
            try:
                current_documents.append((raw_path, candidate.read_text(encoding="utf-8")))
            except (OSError, UnicodeError) as exc:
                issues.append(
                    finding(
                        "current-ref.unreadable",
                        f"Current specification reference cannot be read: {raw_path}: {exc}",
                        severity="high",
                        blocking=True,
                        owner="current-spec",
                        artifact=raw_path,
                    )
                )

    outcomes = require_type(data, "outcomes", list, issues, artifact) or []
    outcome_ids: set[str] = set()
    changed_outcomes = 0
    for index, outcome in enumerate(outcomes):
        if not isinstance(outcome, dict):
            issues.append(finding("outcome.object", f"outcomes[{index}] must be an object", artifact=artifact))
            continue
        outcome_id = outcome.get("id")
        if not isinstance(outcome_id, str) or not OUTCOME_ID.fullmatch(outcome_id):
            issues.append(finding("outcome.id", f"outcomes[{index}].id must be a stable non-whitespace identifier", artifact=artifact))
            continue
        if outcome_id in outcome_ids:
            issues.append(finding("outcome.duplicate", f"Duplicate outcome id: {outcome_id}", artifact=artifact, outcome_ids=[outcome_id]))
        outcome_ids.add(outcome_id)
        if not str(outcome.get("summary", "")).strip():
            issues.append(finding("outcome.summary", f"Outcome {outcome_id} requires a summary", artifact=artifact, outcome_ids=[outcome_id]))
        operation = outcome.get("operation")
        if operation not in OPERATIONS:
            issues.append(finding("outcome.operation", f"Outcome {outcome_id} has unsupported operation: {operation}", artifact=artifact, outcome_ids=[outcome_id]))
        if operation in {"added", "modified", "removed"}:
            changed_outcomes += 1
        if operation in {"modified", "removed"}:
            current_id = outcome.get("currentOutcomeId")
            if not isinstance(current_id, str) or not current_id.strip():
                issues.append(finding("outcome.current-id", f"Outcome {outcome_id} requires currentOutcomeId for {operation}", owner="delta", artifact=artifact, outcome_ids=[outcome_id]))
            else:
                if not current_refs:
                    issues.append(finding("outcome.current-ref-required", f"Outcome {outcome_id} requires at least one currentRefs entry for {operation}", owner="current-spec", artifact=artifact, outcome_ids=[outcome_id]))
                identifier = re.compile(rf"(?<![A-Za-z0-9_-]){re.escape(current_id)}(?![A-Za-z0-9_-])")
                if current_documents and not any(identifier.search(text) for _, text in current_documents):
                    issues.append(
                        finding(
                            "outcome.current-id-not-found",
                            f"Current outcome {current_id} for {outcome_id} was not found in any declared currentRefs document",
                            severity="high",
                            blocking=True,
                            owner="current-spec",
                            artifact=artifact,
                            outcome_ids=[outcome_id],
                        )
                    )

    if impact == "none" and changed_outcomes:
        issues.append(finding("metadata.no-impact-changes", "specImpact 'none' permits only unchanged outcomes", owner="delta", artifact=artifact))
    if impact == "delta" and not changed_outcomes:
        issues.append(finding("metadata.delta-outcome-required", "specImpact 'delta' requires at least one added, modified, or removed outcome", owner="delta", artifact=artifact))

    evidence_data, evidence_findings = validate_evidence(located, data, outcome_ids, root)
    stored_findings, finding_findings = validate_findings(located, data, outcome_ids, root)
    issues.extend(evidence_findings)
    issues.extend(finding_findings)
    return {
        "changeId": located.change_id,
        "metadata": data,
        "findings": issues,
        "evidence": evidence_data,
        "storedFindings": stored_findings,
    }


def optional_artifact_path(located: LocatedChange, data: dict[str, Any], name: str, default: str) -> Path | None:
    artifacts = data.get("artifacts", {})
    if not isinstance(artifacts, dict):
        return None
    raw = artifacts.get(name)
    candidate = located.directory / (raw if isinstance(raw, str) else default)
    return candidate if candidate.is_file() else None


def validate_evidence(
    located: LocatedChange, data: dict[str, Any], outcome_ids: set[str], root: Path
) -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    path = optional_artifact_path(located, data, "evidence", "evidence.json")
    if path is None:
        return None, []
    artifact = display_path(path, root)
    issues: list[dict[str, Any]] = []
    try:
        payload = load_json(path)
    except InputError as exc:
        return None, [finding("evidence.json", str(exc), owner="tests", artifact=artifact)]
    issues.extend(schema_findings(payload, "evidence.schema.json", artifact=artifact, owner="tests"))
    if not isinstance(payload, dict) or payload.get("schemaVersion") != 1 or payload.get("changeId") != data.get("id"):
        issues.append(finding("evidence.header", "evidence.json must have schemaVersion 1 and the matching changeId", owner="tests", artifact=artifact))
        return payload if isinstance(payload, dict) else None, issues
    entries = payload.get("evidence")
    if not isinstance(entries, list):
        issues.append(finding("evidence.entries", "evidence must be an array", owner="tests", artifact=artifact))
        return payload, issues
    seen: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            issues.append(finding("evidence.object", f"evidence[{index}] must be an object", owner="tests", artifact=artifact))
            continue
        evidence_id = entry.get("id")
        if not isinstance(evidence_id, str) or not evidence_id.strip():
            issues.append(finding("evidence.id", f"evidence[{index}] requires an id", owner="tests", artifact=artifact))
        elif evidence_id in seen:
            issues.append(finding("evidence.duplicate", f"Duplicate evidence id: {evidence_id}", owner="tests", artifact=artifact))
        else:
            seen.add(evidence_id)
        refs = entry.get("outcomeIds")
        if not isinstance(refs, list) or not refs or any(not isinstance(item, str) for item in refs):
            issues.append(finding("evidence.outcomes", f"Evidence {evidence_id or index} requires outcomeIds", owner="tests", artifact=artifact))
            continue
        unknown = sorted(set(refs) - outcome_ids)
        if unknown:
            issues.append(finding("evidence.unknown-outcome", f"Evidence {evidence_id or index} references unknown outcomes: {', '.join(unknown)}", owner="tests", artifact=artifact, outcome_ids=unknown))
        if entry.get("result") not in RESULTS:
            issues.append(finding("evidence.result", f"Evidence {evidence_id or index} has an unsupported result", owner="tests", artifact=artifact, outcome_ids=refs))
        if not str(entry.get("summary", "")).strip():
            issues.append(finding("evidence.summary", f"Evidence {evidence_id or index} requires a summary", owner="tests", artifact=artifact, outcome_ids=refs))
    return payload, issues


def validate_findings(
    located: LocatedChange, data: dict[str, Any], outcome_ids: set[str], root: Path
) -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    path = optional_artifact_path(located, data, "findings", "findings.json")
    if path is None:
        return None, []
    artifact = display_path(path, root)
    issues: list[dict[str, Any]] = []
    try:
        payload = load_json(path)
    except InputError as exc:
        return None, [finding("findings.json", str(exc), artifact=artifact)]
    issues.extend(schema_findings(payload, "finding.schema.json", artifact=artifact))
    if not isinstance(payload, dict) or payload.get("schemaVersion") != 1 or payload.get("changeId") != data.get("id"):
        issues.append(finding("findings.header", "findings.json must have schemaVersion 1 and the matching changeId", artifact=artifact))
        return payload if isinstance(payload, dict) else None, issues
    entries = payload.get("findings")
    if not isinstance(entries, list):
        issues.append(finding("findings.entries", "findings must be an array", artifact=artifact))
        return payload, issues
    seen: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            issues.append(finding("finding.object", f"findings[{index}] must be an object", artifact=artifact))
            continue
        finding_id = entry.get("id")
        if not isinstance(finding_id, str) or not finding_id.strip():
            issues.append(finding("finding.id", f"findings[{index}] requires an id", artifact=artifact))
        elif finding_id in seen:
            issues.append(finding("finding.duplicate", f"Duplicate finding id: {finding_id}", artifact=artifact))
        else:
            seen.add(finding_id)
        if entry.get("severity") not in SEVERITIES:
            issues.append(finding("finding.severity", f"Finding {finding_id or index} has unsupported severity", artifact=artifact))
        if entry.get("status") not in FINDING_STATUS:
            issues.append(finding("finding.status", f"Finding {finding_id or index} has unsupported status", artifact=artifact))
        refs = entry.get("outcomeIds", [])
        if not isinstance(refs, list) or any(not isinstance(item, str) for item in refs):
            issues.append(finding("finding.outcomes", f"Finding {finding_id or index} outcomeIds must be an array", artifact=artifact))
        else:
            unknown = sorted(set(refs) - outcome_ids)
            if unknown:
                issues.append(finding("finding.unknown-outcome", f"Finding {finding_id or index} references unknown outcomes: {', '.join(unknown)}", artifact=artifact, outcome_ids=unknown))
    return payload, issues


def conflict_findings(results: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    owners: dict[tuple[str, str], list[tuple[str, str]]] = {}
    for result in results:
        metadata = result.get("metadata") or {}
        current_refs = metadata.get("currentRefs") or ["<unspecified-current-spec>"]
        for outcome in metadata.get("outcomes", []):
            if not isinstance(outcome, dict) or outcome.get("operation") not in {"modified", "removed"}:
                continue
            current_id = outcome.get("currentOutcomeId")
            delta_id = outcome.get("id")
            if isinstance(current_id, str) and current_id and isinstance(delta_id, str) and delta_id:
                for current_ref in current_refs:
                    if isinstance(current_ref, str):
                        owners.setdefault((current_ref, current_id), []).append((result["changeId"], delta_id))
    conflicts: dict[str, list[dict[str, Any]]] = {result["changeId"]: [] for result in results}
    for (current_ref, current_id), references in owners.items():
        unique = sorted({change_id for change_id, _ in references})
        if len(unique) < 2:
            continue
        scope_id = re.sub(r"[^A-Za-z0-9_-]+", "_", current_ref).strip("_") or "unspecified"
        for change_id in unique:
            others = [item for item in unique if item != change_id]
            affected_delta_ids = sorted(
                {delta_id for owner_id, delta_id in references if owner_id == change_id}
            )
            conflicts[change_id].append(
                finding(
                    f"change.conflict.{scope_id}.{current_id}",
                    f"Current outcome {current_id} in {current_ref} is also changed by: {', '.join(others)}",
                    owner="delta",
                    outcome_ids=affected_delta_ids,
                )
            )
    return conflicts


def open_blocking_findings(result: dict[str, Any]) -> list[dict[str, Any]]:
    stored = (result.get("storedFindings") or {}).get("findings", [])
    return [entry for entry in stored if isinstance(entry, dict) and entry.get("status") == "open" and entry.get("blocking") is True]


def evidence_gaps(result: dict[str, Any]) -> list[dict[str, Any]]:
    metadata = result.get("metadata") or {}
    required = {
        outcome.get("id")
        for outcome in metadata.get("outcomes", [])
        if isinstance(outcome, dict) and outcome.get("evidenceRequired", True)
    }
    coverage: set[str] = set()
    nonpassing: dict[str, set[str]] = {}
    for entry in (result.get("evidence") or {}).get("evidence", []):
        if not isinstance(entry, dict):
            continue
        refs = {item for item in entry.get("outcomeIds", []) if isinstance(item, str)}
        if entry.get("result") == "passed":
            coverage.update(refs)
        else:
            for outcome_id in refs:
                nonpassing.setdefault(outcome_id, set()).add(str(entry.get("result")))
    gaps: list[dict[str, Any]] = []
    for outcome_id in sorted(required - coverage):
        detail = ""
        if outcome_id in nonpassing:
            detail = f"; recorded results: {', '.join(sorted(nonpassing[outcome_id]))}"
        gaps.append(finding(f"evidence.gap.{outcome_id}", f"Outcome {outcome_id} has no passed evidence{detail}", owner="tests", outcome_ids=[outcome_id]))
    return gaps


def summarize_status(result: dict[str, Any]) -> dict[str, Any]:
    structural = result.get("findings", [])
    blockers = open_blocking_findings(result)
    gaps = evidence_gaps(result)
    if any(item.get("blocking") for item in structural):
        state = "invalid"
    elif blockers:
        state = "blocked"
    elif gaps:
        state = "verifying"
    else:
        state = "candidate"
    return {
        "changeId": result["changeId"],
        "state": state,
        "structuralFindings": len(structural),
        "openBlockingFindings": len(blockers),
        "evidenceGaps": len(gaps),
        "semanticReviewRequired": state == "candidate",
    }


def render(payload: dict[str, Any], as_json: bool) -> None:
    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
        return
    print(f"specctl {payload['command']}")
    for result in payload.get("results", []):
        state = result.get("state") or result.get("result") or result.get("status") or ("valid" if result.get("valid") else "invalid")
        print(f"- {result.get('changeId', '?')}: {state}")
        for item in result.get("findings", []):
            print(f"  [{item.get('severity', 'info')}] {item.get('message', '')}")


def selected_changes(args: argparse.Namespace, spec_root: Path) -> list[LocatedChange]:
    if getattr(args, "all", False) or not getattr(args, "change", None):
        changes = discover_changes(spec_root)
        if not changes and getattr(args, "change", None):
            raise InputError(f"No changes found under {spec_root}")
        return changes
    return [locate_change(spec_root, args.change)]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    def common(command: argparse.ArgumentParser) -> None:
        command.add_argument("--root", default=".", help="Repository root")
        command.add_argument("--spec-root", help="Specification root relative to repository root")
        command.add_argument("--json", action="store_true", help="Emit JSON")

    status = sub.add_parser("status", help="Derive change state")
    status.add_argument("change", nargs="?")
    common(status)

    validate = sub.add_parser("validate", help="Validate change structure")
    validate.add_argument("change", nargs="?")
    validate.add_argument("--all", action="store_true")
    validate.add_argument("--strict", action="store_true")
    common(validate)

    analyze = sub.add_parser("analyze", help="Run deterministic pre-implementation checks")
    analyze.add_argument("change")
    analyze.add_argument("--strict", action="store_true")
    common(analyze)

    converge = sub.add_parser("converge", help="Check evidence and finding preconditions")
    converge.add_argument("change")
    converge.add_argument("--strict", action="store_true")
    common(converge)

    archive = sub.add_parser("archive", help="Check archive preconditions without mutation")
    archive.add_argument("change")
    archive.add_argument("--dry-run", action="store_true", required=True)
    archive.add_argument("--strict", action="store_true")
    common(archive)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    root = Path(args.root).resolve()
    spec_root = find_spec_root(root, args.spec_root)
    try:
        if args.command in {"status", "validate"}:
            located = selected_changes(args, spec_root)
        else:
            located = [locate_change(spec_root, args.change)]
        strict = bool(getattr(args, "strict", False))
        comparison_by_id = {item.change_id: item for item in discover_changes(spec_root)}
        for item in located:
            comparison_by_id[item.change_id] = item
        comparison = [validate_change(item, root, strict) for item in comparison_by_id.values()]
        conflicts = conflict_findings(comparison)
        for result in comparison:
            result["findings"].extend(conflicts.get(result["changeId"], []))
        comparison_results = {result["changeId"]: result for result in comparison}
        checked = [comparison_results[item.change_id] for item in located]

        if args.command == "status":
            results = [summarize_status(result) for result in checked]
            failed = any(result["state"] != "candidate" for result in results)
        elif args.command == "validate":
            results = [
                {
                    "changeId": result["changeId"],
                    "valid": not any(item.get("blocking") for item in result["findings"]),
                    "findings": result["findings"],
                }
                for result in checked
            ]
            failed = any(not result["valid"] for result in results)
        elif args.command == "analyze":
            results = []
            for result in checked:
                deterministic = result["findings"]
                outcome = "blocked" if any(item.get("blocking") for item in deterministic) else ("findings" if deterministic else "candidate")
                results.append(
                    {
                        "changeId": result["changeId"],
                        "result": outcome,
                        "findings": deterministic,
                        "semanticReviewRequired": True,
                        "semanticScope": ["intent-and-boundaries", "cross-artifact-consistency", "testability-and-quality-goals", "risk-and-change-conflicts"],
                    }
                )
            failed = any(result["result"] != "candidate" for result in results)
        else:
            results = []
            for result in checked:
                deterministic = result["findings"]
                blockers = open_blocking_findings(result)
                gaps = evidence_gaps(result)
                combined = deterministic + blockers + gaps
                if any(item.get("blocking") for item in deterministic):
                    outcome = "blocked"
                elif blockers or gaps:
                    outcome = "gaps"
                else:
                    outcome = "candidate"
                item: dict[str, Any] = {
                    "changeId": result["changeId"],
                    "result": outcome,
                    "findings": combined,
                    "semanticReviewRequired": outcome == "candidate",
                }
                if args.command == "archive":
                    metadata = result.get("metadata") or {}
                    item.update(
                        {
                            "status": "awaiting-semantic-convergence" if outcome == "candidate" else "not-ready",
                            "archiveReady": False,
                            "deterministicPreconditions": "passed" if outcome == "candidate" else "failed",
                            "delta": (metadata.get("artifacts") or {}).get("delta"),
                            "currentRefs": metadata.get("currentRefs", []),
                            "mutationPerformed": False,
                        }
                    )
                results.append(item)
            failed = any((result.get("result") != "candidate") for result in results)

        payload = {
            "schemaVersion": SCHEMA_VERSION,
            "command": args.command,
            "root": str(root),
            "specRoot": str(spec_root),
            "results": results,
        }
        payload["summary"] = {
            "total": len(results),
            "passing": sum(
                1
                for result in results
                if result.get("valid") is True
                or result.get("state") == "candidate"
                or result.get("result") in {"clean", "candidate"}
            ),
        }
        render(payload, args.json)
        return 1 if failed else 0
    except InputError as exc:
        payload = {"schemaVersion": SCHEMA_VERSION, "command": args.command, "error": str(exc)}
        render(payload, args.json)
        return 2
    except OSError as exc:
        payload = {"schemaVersion": SCHEMA_VERSION, "command": args.command, "error": f"File system error: {exc}"}
        render(payload, args.json)
        return 2


if __name__ == "__main__":
    sys.exit(main())
