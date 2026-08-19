#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from agentic_cicd.runtime_controller import (
    CandidateRevisionStore,
    GateReceiptStore,
    PhaseContextStore,
    ReviewProposalStore,
    SymphonyWorkspaceBootstrap,
    TaskStateInitializer,
    TurnStateController,
)
from agentic_cicd.gate_runtime import GateMailbox, GatePolicy, ValidatePhaseDriver
from agentic_cicd.coordinator import Coordinator, SnapshotStore
from agentic_cicd.capabilities import validate_disposable_github_e2e
from agentic_cicd.failure_router import FailureRouter
from agentic_cicd.github_adapter import (
    EnvironmentInstallationTokenProvider,
    GitHubRestGraphqlAdapter,
    HostGitPusher,
    validate_github_runtime_prerequisites,
)
from agentic_cicd.github_lifecycle import (
    GitHubLifecycleController,
    WorkspaceBaseSynchronizer,
)
from agentic_cicd.pr_packet import TaskBrief
from agentic_cicd.runtime_binding import validate_runtime_authority


DEFAULT_CONTRACT = Path("/opt/jstore-agentic-controller/state-contract.json")
DEFAULT_RUNTIME_BINDING = Path(
    "/opt/jstore-agentic-controller/runtime-binding.json"
)
DEFAULT_GATE_POLICY = Path("/etc/agentic-cicd/gate-policy.json")
DEFAULT_REPOSITORY = "ddd-mall/j-store"
REPOSITORY_CONTRACT = (
    Path(__file__).resolve().parents[1]
    / "config"
    / "agentic-cicd"
    / "state-contract.json"
)
AUTHORITATIVE_CONTRACT = (
    DEFAULT_CONTRACT if DEFAULT_CONTRACT.is_file() else REPOSITORY_CONTRACT
)


def trusted_runtime_repository() -> str:
    configured = os.environ.get("JSTORE_SYMPHONY_REPOSITORY", DEFAULT_REPOSITORY)
    configured_url = os.environ.get(
        "JSTORE_SYMPHONY_REPOSITORY_URL",
        f"https://github.com/{configured}.git",
    )
    return validate_runtime_authority(
        contract_path=AUTHORITATIVE_CONTRACT,
        binding_path=DEFAULT_RUNTIME_BINDING,
        configured_repository=configured,
        configured_repository_url=configured_url,
        unbound_repository=DEFAULT_REPOSITORY,
    )


def require_task_repository(
    *, issue_identifier: str, state_root: Path, repository: str
) -> None:
    snapshot = SnapshotStore(
        state_root.resolve() / "tasks" / f"{issue_identifier}.json"
    ).load()
    if snapshot.repository != repository:
        raise RuntimeError("task repository does not match the immutable runtime binding")


def capability_enabled(contract_path: Path, capability: str) -> bool:
    payload = load_contract(contract_path)
    capabilities = payload.get("capabilities")
    if not isinstance(capabilities, dict):
        raise ValueError("state contract capabilities must be an object")
    return capabilities.get(capability) is True


def load_contract(contract_path: Path) -> dict:
    payload = json.loads(contract_path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("state contract must be a JSON object")
    return payload


def workspace_write_enabled(contract_path: Path) -> bool:
    return capability_enabled(contract_path, "local_workspace_write")


def reconcile_github_if_ready(
    *,
    issue_identifier: str,
    state_root: Path,
    artifact_root: Path | None,
    contract_path: Path,
) -> None:
    contract = load_contract(contract_path)
    capabilities = contract.get("capabilities")
    required_checks = contract.get("required_checks")
    if (
        not isinstance(capabilities, dict)
        or not isinstance(required_checks, list)
        or not required_checks
        or not all(isinstance(value, str) and value.strip() for value in required_checks)
    ):
        raise ValueError("state contract GitHub fields are invalid")
    snapshot = SnapshotStore(
        state_root.resolve() / "tasks" / f"{issue_identifier}.json"
    ).load()
    if not snapshot.repository:
        raise RuntimeError("task snapshot has no trusted repository identity")
    if snapshot.iteration_phase != "complete" or capabilities.get("push_commit") is not True:
        return
    if artifact_root is None:
        raise RuntimeError("GitHub reconciliation requires candidate artifact storage")
    provider = EnvironmentInstallationTokenProvider()
    workpad_author = os.environ.get("JSTORE_GITHUB_APP_LOGIN")
    reviewer = os.environ.get("JSTORE_GITHUB_REVIEWER")
    workpad_author, reviewer = validate_github_runtime_prerequisites(
        token_provider=provider,
        capabilities=capabilities,
        github_app_login=workpad_author,
        reviewer=reviewer,
    )
    adapter = GitHubRestGraphqlAdapter(
        token_provider=provider,
        workpad_author_login=workpad_author,
    )
    pusher = HostGitPusher(token_provider=provider, capabilities=capabilities)
    candidate = snapshot.candidate_revision or {}
    revision = candidate.get("candidate_revision", "unknown")
    GitHubLifecycleController(
        state_root=state_root,
        artifact_root=artifact_root,
        client=adapter,
        pusher=pusher,
        base_synchronizer=WorkspaceBaseSynchronizer(),
        failure_router=FailureRouter(Coordinator.from_contract(contract)),
        capabilities=capabilities,
        required_checks={str(value) for value in required_checks},
    ).reconcile(
        issue_identifier,
        repository=snapshot.repository,
        title=f"ci(agent): prepare {issue_identifier} candidate",
        workpad_body=f"Candidate `{revision}` is ready for human review.",
        reviewer=reviewer,
    )


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
        "--issue-title", default=os.environ.get("JSTORE_ISSUE_TITLE")
    )
    bootstrap.add_argument(
        "--issue-body", default=os.environ.get("JSTORE_ISSUE_BODY")
    )
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
    phase.add_argument(
        "--artifact-root", default=os.environ.get("JSTORE_CANDIDATE_ARTIFACT_ROOT")
    )
    phase.add_argument(
        "--exchange-root", default=os.environ.get("JSTORE_GATE_EXCHANGE_ROOT")
    )
    phase.add_argument("--gate-policy", type=Path, default=DEFAULT_GATE_POLICY)
    complete = subparsers.add_parser(
        "complete-turn",
        help="Bind the current trusted Symphony turn receipt to host-owned state",
    )
    complete.add_argument("--issue", required=True)
    complete.add_argument("--workspace", default=".")
    complete.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    complete.add_argument(
        "--artifact-root", default=os.environ.get("JSTORE_CANDIDATE_ARTIFACT_ROOT")
    )
    complete.add_argument(
        "--session-id", default=os.environ.get("JSTORE_TURN_SESSION_ID")
    )
    complete.add_argument(
        "--thread-id", default=os.environ.get("JSTORE_TURN_THREAD_ID")
    )
    complete.add_argument("--turn-id", default=os.environ.get("JSTORE_TURN_ID"))
    complete.add_argument(
        "--outcome",
        choices=("succeeded", "failed"),
        default=os.environ.get("JSTORE_TURN_OUTCOME", "succeeded"),
    )
    complete.add_argument(
        "--token-usage-observed",
        choices=("true", "false"),
        default=os.environ.get("JSTORE_TURN_TOKEN_USAGE_OBSERVED", "true"),
    )
    complete.add_argument(
        "--wall-clock-seconds",
        type=int,
        default=os.environ.get("JSTORE_TURN_WALL_CLOCK_SECONDS"),
    )
    complete.add_argument(
        "--input-tokens",
        type=int,
        default=os.environ.get("JSTORE_TURN_INPUT_TOKENS"),
    )
    complete.add_argument(
        "--output-tokens",
        type=int,
        default=os.environ.get("JSTORE_TURN_OUTPUT_TOKENS"),
    )
    complete.add_argument(
        "--expected-phase", default=os.environ.get("JSTORE_INVOCATION_PHASE")
    )
    complete.add_argument(
        "--expected-role", default=os.environ.get("JSTORE_INVOCATION_ROLE")
    )
    complete.add_argument(
        "--expected-head-sha", default=os.environ.get("JSTORE_INVOCATION_HEAD_SHA")
    )
    complete.add_argument(
        "--expected-candidate-revision",
        default=os.environ.get("JSTORE_INVOCATION_CANDIDATE_REVISION"),
    )
    freeze = subparsers.add_parser(
        "freeze-candidate",
        help="Freeze the bound workspace into a host-owned CandidateRevision",
    )
    freeze.add_argument("--issue", required=True)
    freeze.add_argument("--workspace", default=".")
    freeze.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    freeze.add_argument(
        "--artifact-root", default=os.environ.get("JSTORE_CANDIDATE_ARTIFACT_ROOT")
    )
    gate = subparsers.add_parser(
        "record-gate",
        help="Consume one host-owned exact-head deterministic gate receipt",
    )
    gate.add_argument("--issue", required=True)
    gate.add_argument("--payload", required=True)
    gate.add_argument(
        "--state-root", default=os.environ.get("JSTORE_AGENTIC_CICD_STATE_ROOT")
    )
    preflight = subparsers.add_parser(
        "github-e2e-preflight",
        help="Validate a disposable Level 2 profile without credentials or network access",
    )
    preflight.add_argument("--repository", required=True)
    preflight.add_argument("--repository-url", required=True)
    preflight.add_argument("--contract", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if arguments.command == "github-e2e-preflight":
        candidate = load_contract(arguments.contract)
        authoritative = load_contract(AUTHORITATIVE_CONTRACT)
        failures = validate_disposable_github_e2e(
            repository=arguments.repository,
            repository_url=arguments.repository_url,
            contract=candidate,
            authoritative_contract=authoritative,
        )
        if failures:
            raise ValueError("; ".join(failures))
        print(
            "GITHUB_E2E_PREFLIGHT_READY "
            f"repository={arguments.repository} capability_level=2"
        )
        return 0
    runtime_repository = trusted_runtime_repository()
    if arguments.command == "bootstrap-workspace":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        if not arguments.issue_title or not arguments.issue_body:
            raise ValueError("trusted Issue title and body are required")
        workspace = Path(arguments.workspace)
        result = SymphonyWorkspaceBootstrap(
            trusted_repository=runtime_repository
        ).bootstrap(
            repository_url=arguments.repository_url,
            workspace=workspace,
        )
        task_brief = TaskBrief.parse(
            result.issue_identifier, arguments.issue_title, arguments.issue_body
        )
        TaskStateInitializer(Path(arguments.state_root)).initialize(
            result, workspace, task_brief
        )
        print(
            "WORKSPACE_READY "
            f"issue={result.issue_identifier} "
            f"base_sha={result.base_sha} branch={result.branch}"
        )
        return 0
    if arguments.command == "submit-review-proposal":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        require_task_repository(
            issue_identifier=arguments.issue,
            state_root=Path(arguments.state_root),
            repository=runtime_repository,
        )
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
        require_task_repository(
            issue_identifier=arguments.issue,
            state_root=Path(arguments.state_root),
            repository=runtime_repository,
        )
        gate_enabled = capability_enabled(AUTHORITATIVE_CONTRACT, "run_isolated_gate")
        if gate_enabled:
            if not arguments.artifact_root or not arguments.exchange_root:
                raise ValueError("candidate artifact and gate exchange roots are required")
            ValidatePhaseDriver(
                state_root=Path(arguments.state_root),
                artifact_root=Path(arguments.artifact_root),
                mailbox=GateMailbox(Path(arguments.exchange_root)),
                policy=GatePolicy.load(arguments.gate_policy),
                contract_path=AUTHORITATIVE_CONTRACT,
                enabled=True,
            ).advance(arguments.issue, Path(arguments.workspace))
        reconcile_github_if_ready(
            issue_identifier=arguments.issue,
            state_root=Path(arguments.state_root),
            artifact_root=(
                Path(arguments.artifact_root) if arguments.artifact_root else None
            ),
            contract_path=AUTHORITATIVE_CONTRACT,
        )
        context = PhaseContextStore(
            Path(arguments.state_root),
            workspace_write_enabled=workspace_write_enabled(AUTHORITATIVE_CONTRACT),
            artifact_root=(
                Path(arguments.artifact_root) if arguments.artifact_root else None
            ),
        ).load(arguments.issue, Path(arguments.workspace))
        print(json.dumps(context.to_json(), separators=(",", ":"), sort_keys=True))
        return 0
    if arguments.command == "complete-turn":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        require_task_repository(
            issue_identifier=arguments.issue,
            state_root=Path(arguments.state_root),
            repository=runtime_repository,
        )
        for name in (
            "session_id",
            "thread_id",
            "turn_id",
            "expected_phase",
            "expected_role",
            "expected_head_sha",
        ):
            if not getattr(arguments, name):
                raise ValueError(f"{name} is required")
        for name in ("wall_clock_seconds", "input_tokens", "output_tokens"):
            if getattr(arguments, name) is None:
                raise ValueError(f"{name} is required")
        TurnStateController(
            Path(arguments.state_root),
            workspace_write_enabled=workspace_write_enabled(AUTHORITATIVE_CONTRACT),
            artifact_root=(
                Path(arguments.artifact_root) if arguments.artifact_root else None
            ),
            contract_path=AUTHORITATIVE_CONTRACT,
        ).complete_turn(
            arguments.issue,
            Path(arguments.workspace),
            session_id=arguments.session_id,
            thread_id=arguments.thread_id,
            turn_id=arguments.turn_id,
            expected_phase=arguments.expected_phase,
            expected_role=arguments.expected_role,
            expected_head_sha=arguments.expected_head_sha,
            expected_candidate_revision=(
                arguments.expected_candidate_revision or None
            ),
            wall_clock_seconds=arguments.wall_clock_seconds,
            input_tokens=arguments.input_tokens,
            output_tokens=arguments.output_tokens,
            outcome=arguments.outcome,
            token_usage_observed=arguments.token_usage_observed == "true",
        )
        print(f"TURN_RECEIPT_ACCEPTED issue={arguments.issue}")
        return 0
    if arguments.command == "record-gate":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        require_task_repository(
            issue_identifier=arguments.issue,
            state_root=Path(arguments.state_root),
            repository=runtime_repository,
        )
        payload = json.loads(arguments.payload)
        if not isinstance(payload, dict):
            raise ValueError("gate receipt payload must be a JSON object")
        GateReceiptStore(
            Path(arguments.state_root),
            contract_path=AUTHORITATIVE_CONTRACT,
            gate_enabled=capability_enabled(
                AUTHORITATIVE_CONTRACT, "run_isolated_gate"
            ),
        ).record(arguments.issue, payload)
        print(f"GATE_RECEIPT_ACCEPTED issue={arguments.issue}")
        return 0
    if arguments.command == "freeze-candidate":
        if not arguments.state_root:
            raise ValueError("JSTORE_AGENTIC_CICD_STATE_ROOT is required")
        require_task_repository(
            issue_identifier=arguments.issue,
            state_root=Path(arguments.state_root),
            repository=runtime_repository,
        )
        revision = CandidateRevisionStore(
            Path(arguments.state_root),
            artifact_root=(
                Path(arguments.artifact_root)
                if arguments.artifact_root
                else None
            ),
            freeze_enabled=capability_enabled(
                AUTHORITATIVE_CONTRACT, "freeze_local_candidate"
            ),
        ).freeze(arguments.issue, Path(arguments.workspace))
        print(json.dumps(revision.to_json(), separators=(",", ":"), sort_keys=True))
        return 0
    raise RuntimeError(f"unsupported command: {arguments.command}")


if __name__ == "__main__":
    raise SystemExit(main())
