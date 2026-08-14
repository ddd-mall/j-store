#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from agentic_cicd.runtime_controller import (
    GateReceiptStore,
    PhaseContextStore,
    ReviewProposalStore,
    SymphonyWorkspaceBootstrap,
    TaskStateInitializer,
    TurnStateController,
)


DEFAULT_CONTRACT = Path("/opt/jstore-agentic-controller/state-contract.json")


def workspace_write_enabled(contract_path: Path) -> bool:
    payload = json.loads(contract_path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("state contract must be a JSON object")
    capabilities = payload.get("capabilities")
    if not isinstance(capabilities, dict):
        raise ValueError("state contract capabilities must be an object")
    return capabilities.get("local_workspace_write") is True


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Trusted j-store Symphony host controller")
    subparsers = parser.add_subparsers(dest="command", required=True)
    bootstrap = subparsers.add_parser(
        "bootstrap-workspace",
        help="Clone and lock one new task workspace to fetched origin/develop",
    )
    bootstrap.add_argument("--repository-url", required=True)
    bootstrap.add_argument("--workspace", default=".")
    bootstrap.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    proposal = subparsers.add_parser(
        "submit-review-proposal",
        help="Validate and persist a reviewer proposal in host-owned state",
    )
    proposal.add_argument("--issue", required=True)
    proposal.add_argument("--payload", required=True)
    proposal.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    phase = subparsers.add_parser(
        "phase-context",
        help="Return trusted phase and sandbox context for one Symphony invocation",
    )
    phase.add_argument("--issue", required=True)
    phase.add_argument("--workspace", default=".")
    phase.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    phase.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    complete = subparsers.add_parser(
        "complete-turn",
        help="Bind the current trusted Symphony turn receipt to host-owned state",
    )
    complete.add_argument("--issue", required=True)
    complete.add_argument("--workspace", default=".")
    complete.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    complete.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    complete.add_argument(
        "--session-id", default=os.environ.get("JSTORE_TURN_SESSION_ID")
    )
    complete.add_argument(
        "--thread-id", default=os.environ.get("JSTORE_TURN_THREAD_ID")
    )
    complete.add_argument("--turn-id", default=os.environ.get("JSTORE_TURN_ID"))
    gate = subparsers.add_parser(
        "record-gate",
        help="Consume one host-owned exact-head deterministic gate receipt",
    )
    gate.add_argument("--issue", required=True)
    gate.add_argument("--payload", required=True)
    gate.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if arguments.command == "bootstrap-workspace":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        workspace = Path(arguments.workspace)
        result = SymphonyWorkspaceBootstrap().bootstrap(
            repository_url=arguments.repository_url,
            workspace=workspace,
        )
        TaskStateInitializer(Path(arguments.state_root)).initialize(result, workspace)
        print(
            "WORKSPACE_READY "
            f"issue={result.issue_identifier} "
            f"base_sha={result.base_sha} branch={result.branch}"
        )
        return 0
    if arguments.command == "submit-review-proposal":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        payload = json.loads(arguments.payload)
        if not isinstance(payload, dict):
            raise ValueError("review proposal payload must be a JSON object")
        path = ReviewProposalStore(Path(arguments.state_root)).submit(
            arguments.issue, payload
        )
        print(f"REVIEW_PROPOSAL_ACCEPTED issue={arguments.issue} path={path.name}")
        return 0
    if arguments.command == "phase-context":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        context = PhaseContextStore(
            Path(arguments.state_root),
            workspace_write_enabled=workspace_write_enabled(arguments.contract),
        ).load(arguments.issue, Path(arguments.workspace))
        print(json.dumps(context.to_json(), separators=(",", ":"), sort_keys=True))
        return 0
    if arguments.command == "complete-turn":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        for name in ("session_id", "thread_id", "turn_id"):
            if not getattr(arguments, name):
                raise ValueError(f"{name} is required")
        TurnStateController(
            Path(arguments.state_root),
            workspace_write_enabled=workspace_write_enabled(arguments.contract),
        ).complete_turn(
            arguments.issue,
            Path(arguments.workspace),
            session_id=arguments.session_id,
            thread_id=arguments.thread_id,
            turn_id=arguments.turn_id,
        )
        print(f"TURN_RECEIPT_ACCEPTED issue={arguments.issue}")
        return 0
    if arguments.command == "record-gate":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        payload = json.loads(arguments.payload)
        if not isinstance(payload, dict):
            raise ValueError("gate receipt payload must be a JSON object")
        GateReceiptStore(Path(arguments.state_root)).record(arguments.issue, payload)
        print(f"GATE_RECEIPT_ACCEPTED issue={arguments.issue}")
        return 0
    raise RuntimeError(f"unsupported command: {arguments.command}")


if __name__ == "__main__":
    raise SystemExit(main())
