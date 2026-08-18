#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
from pathlib import Path

from agentic_cicd.runtime import RuntimePreflight


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate pinned Symphony and Codex runtimes without starting a model turn."
    )
    parser.add_argument(
        "--symphony-source",
        default=os.environ.get("JSTORE_SYMPHONY_SOURCE"),
        help=(
            "Path to the pinned OpenAI Symphony source checkout; may also be set "
            "through JSTORE_SYMPHONY_SOURCE."
        ),
    )
    parser.add_argument(
        "--source-only",
        action="store_true",
        help="Validate only the pinned Symphony source and GitHub secret boundaries.",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if not arguments.symphony_source:
        print(
            "FAIL: provide --symphony-source or set JSTORE_SYMPHONY_SOURCE.",
            flush=True,
        )
        return 1

    preflight = RuntimePreflight(
        symphony_source=Path(arguments.symphony_source),
        symphony_lock=REPOSITORY_ROOT
        / "config"
        / "agentic-cicd"
        / "symphony.lock.json",
        codex_lock=REPOSITORY_ROOT
        / "config"
        / "agentic-cicd"
        / "codex-app-server.lock.json",
    )
    result = (
        preflight.check_symphony_source()
        if arguments.source_only
        else preflight.check()
    )
    for check in result.checks:
        print(f"PASS: {check}")
    for failure in result.failures:
        print(f"FAIL: {failure}")
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
