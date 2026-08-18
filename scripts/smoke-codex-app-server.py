#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from jsonschema import Draft202012Validator

from agentic_cicd.app_server import (
    AppServerClient,
    JsonLineTransport,
    build_implementer_thread_params,
    build_review_thread_params,
    build_review_turn_params,
)
from agentic_cicd.protocol import IterationPacket


REPO_ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = REPO_ROOT / "config" / "agentic-cicd" / "codex-app-server.lock.json"


def main() -> int:
    lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    binary = str(lock["binary"])
    version = subprocess.run(
        [binary, "--version"],
        check=True,
        capture_output=True,
        text=True,
        timeout=10,
    ).stdout.strip()
    if lock.get("version_policy") != "installed-stable" or not re.fullmatch(
        r"codex-cli [0-9]+\.[0-9]+\.[0-9]+", version
    ):
        print(
            f"FAIL: expected an installed stable Codex CLI, found {version!r}",
            file=sys.stderr,
        )
        return 1

    with tempfile.TemporaryDirectory() as directory:
        schema_root = Path(directory)
        subprocess.run(
            [
                binary,
                "app-server",
                "generate-json-schema",
                "--out",
                str(schema_root),
            ],
            check=True,
            capture_output=True,
            text=True,
            timeout=20,
        )
        thread_schema = json.loads(
            (schema_root / "v2" / "ThreadStartParams.json").read_text(
                encoding="utf-8"
            )
        )
        turn_schema = json.loads(
            (schema_root / "v2" / "TurnStartParams.json").read_text(
                encoding="utf-8"
            )
        )
        workspace = Path("/tmp/j-store-app-server-schema-smoke")
        Draft202012Validator(thread_schema).validate(
            build_implementer_thread_params(workspace)
        )
        Draft202012Validator(thread_schema).validate(
            build_review_thread_params(workspace)
        )
        packet = IterationPacket(
            issue_identifier="GH-1",
            objective="Validate the pinned app-server protocol without a model turn.",
            base_sha="a" * 40,
            head_sha="b" * 40,
            acceptance=("AC-smoke",),
            review_findings=(),
            ci_failures=(),
            attempts_by_root_cause={},
            budget_remaining={"turns": 1, "cost_microusd": 0},
            validation_commands=("./scripts/quality-gate.sh",),
            implementer_session_id="implementer-smoke",
        )
        Draft202012Validator(turn_schema).validate(
            build_review_turn_params("reviewer-smoke", packet)
        )

    process = subprocess.Popen(
        [binary, "app-server"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if process.stdin is None or process.stdout is None:
        process.kill()
        raise RuntimeError("failed to open app-server stdio")
    try:
        client = AppServerClient(JsonLineTransport(process.stdout, process.stdin))
        response = client.initialize()
        if response.get("platformOs") != "linux":
            print("FAIL: app-server did not initialize on Linux", file=sys.stderr)
            return 1
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)

    print(
        f"PASS: {version} schema and app-server initialize handshake completed on Linux."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
