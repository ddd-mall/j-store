#!/usr/bin/env python3

from __future__ import annotations

import os
import subprocess
from pathlib import Path, PurePosixPath


def repository_files(repository_root: Path) -> list[str]:
    manifest_path = os.environ.get("JSTORE_REPOSITORY_FILES_FILE")
    if manifest_path is None:
        result = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
            cwd=repository_root,
            check=True,
            capture_output=True,
        )
        candidates = [
            path.decode("utf-8") for path in result.stdout.split(b"\0") if path
        ]
    else:
        manifest = Path(manifest_path)
        if not manifest.is_absolute() or not manifest.is_file():
            raise ValueError("repository file manifest must be an existing absolute path")
        candidates = manifest.read_text(encoding="utf-8").splitlines()

    normalized: list[str] = []
    for candidate in candidates:
        relative = candidate.removeprefix("./")
        path = PurePosixPath(relative)
        if (
            not relative
            or path.is_absolute()
            or ".." in path.parts
            or "\r" in relative
            or "\n" in relative
        ):
            raise ValueError(f"invalid repository file path: {candidate!r}")
        if not (repository_root / relative).is_file():
            raise ValueError(f"repository file is missing or not regular: {relative}")
        normalized.append(relative)

    if len(normalized) != len(set(normalized)):
        raise ValueError("repository file manifest contains duplicate paths")
    return sorted(normalized)
