#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import subprocess
from pathlib import Path

from agentic_cicd.host_credentials import (
    prepare_host_credentials,
    validate_host_credentials,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate or install fixed host-native Symphony credentials"
    )
    parser.add_argument("--github-token-file", type=Path, required=True)
    parser.add_argument("--expires-at-epoch-seconds", type=int, required=True)
    parser.add_argument("--auth-file", type=Path, required=True)
    parser.add_argument("--config-file", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check-only", action="store_true")
    mode.add_argument("--install", action="store_true")
    arguments = parser.parse_args()
    values = {
        "token_file": arguments.github_token_file,
        "expires_at_epoch_seconds": arguments.expires_at_epoch_seconds,
        "auth_file": arguments.auth_file,
        "config_file": arguments.config_file,
    }
    if arguments.check_only:
        validate_host_credentials(**values)
        print("HOST_CREDENTIALS_VALID destination=unchanged")
        return 0
    if os.geteuid() != 0:
        raise SystemExit("ERROR: --install requires root")
    active = subprocess.run(
        ["systemctl", "is-active", "--quiet", "jstore-agentic-cicd.service"],
        check=False,
    )
    if active.returncode == 0:
        raise SystemExit("ERROR: stop jstore-agentic-cicd.service before credential update")
    prepare_host_credentials(
        **values,
        destination=Path("/etc/jstore-agentic-cicd/credentials"),
    )
    print("HOST_CREDENTIALS_INSTALLED service=inactive values=redacted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
