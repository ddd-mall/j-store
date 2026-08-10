#!/usr/bin/env python3

from __future__ import annotations

import fnmatch
import json
import subprocess
import sys
import tomllib
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = REPO_ROOT / "config/licenses/file-ownership.toml"
REPORT_PATH = REPO_ROOT / "build/reports/licenses/file-ownership.json"
SPDX_COPYRIGHT = "SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)"
SPDX_LICENSE = "SPDX-License-Identifier: Apache-2.0"


def repository_files() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
    )
    return sorted(
        relative_path
        for path in result.stdout.split(b"\0")
        if path
        for relative_path in (path.decode("utf-8"),)
        if (REPO_ROOT / relative_path).is_file()
    )


def main() -> int:
    manifest = tomllib.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    default = manifest["default"]
    overrides = manifest.get("overrides", {})
    files = repository_files()
    errors: list[str] = []
    report_entries: list[dict[str, str]] = []
    matched_overrides = {name: 0 for name in overrides}

    for relative_path in files:
        matching = [
            (name, rule)
            for name, rule in overrides.items()
            if any(fnmatch.fnmatchcase(relative_path, pattern) for pattern in rule["paths"])
        ]
        if len(matching) > 1:
            errors.append(
                f"{relative_path}: matches multiple ownership overrides "
                + ", ".join(name for name, _ in matching)
            )
            continue

        if matching:
            name, ownership = matching[0]
            matched_overrides[name] += 1
        else:
            ownership = default

        entry = {
            "path": relative_path,
            "classification": ownership["classification"],
            "copyright": ownership["copyright"],
            "license": ownership["license"],
        }
        report_entries.append(entry)

        path = REPO_ROOT / relative_path
        if ownership["classification"] == "third-party" and path.is_file():
            try:
                content = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            if SPDX_COPYRIGHT in content:
                errors.append(f"{relative_path}: third-party file carries the project owner's SPDX copyright")

    for name, count in matched_overrides.items():
        if count == 0:
            errors.append(f"ownership override {name!r} does not match any repository file")

    for relative_path in files:
        if not relative_path.endswith((".java", ".kt")):
            continue
        path = REPO_ROOT / relative_path
        content = path.read_text(encoding="utf-8")
        if SPDX_COPYRIGHT not in content or SPDX_LICENSE not in content:
            errors.append(f"{relative_path}: missing project SPDX ownership header")

    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(
        json.dumps(
            {
                "schemaVersion": manifest["version"],
                "files": report_entries,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    if errors:
        print("FAIL: file ownership audit found violations:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    third_party_count = sum(
        entry["classification"] == "third-party" for entry in report_entries
    )
    print(
        f"PASS: classified {len(report_entries)} repository files "
        f"({third_party_count} third-party)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
