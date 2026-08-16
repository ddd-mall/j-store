#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from agentic_cicd.artifact_broker import serve


def main() -> None:
    parser = argparse.ArgumentParser(description="Read-only CandidateRevision artifact broker")
    parser.add_argument("--artifact-root", type=Path, required=True)
    parser.add_argument("--lease-root", type=Path, required=True)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8081)
    arguments = parser.parse_args()
    serve(arguments.artifact_root, arguments.lease_root, arguments.host, arguments.port)


if __name__ == "__main__":
    main()
