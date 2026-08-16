#!/usr/bin/env python3
"""Credential-free phase-context stub for patched Symphony's native test suite."""

import json
import sys


def argument(name: str) -> str:
    index = sys.argv.index(name)
    return sys.argv[index + 1]


if len(sys.argv) > 1 and sys.argv[1] == "phase-context":
    print(
        json.dumps(
            {
                "phase": "implement",
                "role": "observer",
                "run_model": True,
                "complete_turn": False,
                "thread_sandbox": "read-only",
                "turn_sandbox_policy": {
                    "type": "readOnly",
                    "networkAccess": False,
                },
                "head_sha": "0" * 40,
                "candidate_revision": None,
                "model_workspace": argument("--workspace"),
            },
            separators=(",", ":"),
            sort_keys=True,
        )
    )
    raise SystemExit(0)

raise SystemExit("unsupported test controller command")
