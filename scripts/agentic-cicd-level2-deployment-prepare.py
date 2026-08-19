#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from agentic_cicd.level2_deployment import prepare_level2_deployment_candidate


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Prepare, but do not apply, a digest-bound Level 2 deployment candidate."
    )
    parser.add_argument("--source-record", required=True, type=Path)
    parser.add_argument("--source-record-sha256", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--github-app-login", required=True)
    parser.add_argument("--reviewer", required=True)
    parser.add_argument("--output-dir", required=True, type=Path)
    arguments = parser.parse_args()
    prepare_level2_deployment_candidate(
        source_record_path=arguments.source_record.resolve(),
        expected_source_record_sha256=arguments.source_record_sha256,
        repository=arguments.repository,
        github_app_login=arguments.github_app_login,
        reviewer=arguments.reviewer,
        output_directory=arguments.output_dir.resolve(),
        repository_root=REPOSITORY_ROOT,
    )
    print(f"LEVEL2_DEPLOYMENT_MANIFEST={arguments.output_dir.resolve() / 'manifest.yaml'}")
    print(
        "LEVEL2_DEPLOYMENT_PROFILE="
        f"{arguments.output_dir.resolve() / 'deployment-profile.json'}"
    )
    print("PASS: rendered Level 2 deployment candidate; no cluster write performed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
