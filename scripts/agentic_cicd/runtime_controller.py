from __future__ import annotations

import json
import os
import re
import secrets
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from .candidate import CandidateRevision, CandidateSnapshotter
from .coordinator import BudgetExceeded, Coordinator, SnapshotStore, TaskSnapshot
from .phase_bridge import (
    PHASE_COMPLETE,
    PHASE_IMPLEMENT,
    PHASE_REVIEW,
    PHASE_VALIDATE,
    SymphonyPhaseBridge,
)
from .process_environment import trusted_process_environment
from .pr_packet import TaskBrief
from .protocol import GateReceipt, GateRequest, ReviewProposal, TurnReceipt


ISSUE_WORKSPACE = re.compile(r"GH-([1-9][0-9]*)\Z")
GITHUB_REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
FULL_SHA = re.compile(r"[0-9a-f]{40}\Z")
METADATA_DIRECTORY = ".agentic-cicd"
METADATA_FILE = "workspace.json"
DEFAULT_TRUSTED_REPOSITORY = "ddd-mall/j-store"
GIT_COMMAND_TIMEOUT_SECONDS = 120


@dataclass(frozen=True)
class BootstrapResult:
    issue_identifier: str
    repository: str
    base_sha: str
    branch: str


class SymphonyWorkspaceBootstrap:
    """Creates one Symphony workspace from the fetched develop ref."""

    def __init__(
        self,
        *,
        trusted_repository: str = DEFAULT_TRUSTED_REPOSITORY,
        allow_local_repository: bool = False,
    ):
        if GITHUB_REPOSITORY.fullmatch(trusted_repository) is None:
            raise ValueError("trusted_repository must use canonical owner/name form")
        self.trusted_repository = trusted_repository
        self.trusted_repository_url = (
            f"https://github.com/{trusted_repository}.git"
        )
        self.allow_local_repository = allow_local_repository

    def bootstrap(self, *, repository_url: str, workspace: Path) -> BootstrapResult:
        resolved_workspace = workspace.resolve()
        match = ISSUE_WORKSPACE.fullmatch(resolved_workspace.name)
        if match is None:
            raise ValueError("workspace basename must match GH-<positive-number>")
        normalized_url = repository_url.strip()
        if not normalized_url:
            raise ValueError("repository_url must not be blank")
        if normalized_url != self.trusted_repository_url:
            local_repository = Path(normalized_url)
            if not (
                self.allow_local_repository
                and local_repository.is_absolute()
                and local_repository.is_dir()
            ):
                raise ValueError(
                    "repository_url must be the trusted HTTPS repository"
                )

        resolved_workspace.mkdir(parents=True, exist_ok=True)
        if any(resolved_workspace.iterdir()):
            raise RuntimeError("new Symphony workspace must be empty")

        self._git(
            resolved_workspace,
            "clone",
            "--filter=blob:none",
            "--no-checkout",
            normalized_url,
            ".",
        )
        self._git(resolved_workspace, "fetch", "--force", "origin", "develop")
        base_sha = self._git(
            resolved_workspace,
            "rev-parse",
            "refs/remotes/origin/develop",
        ).stdout.strip()
        if not FULL_SHA.fullmatch(base_sha):
            raise RuntimeError("origin/develop did not resolve to a full commit SHA")

        issue_identifier = resolved_workspace.name
        branch = f"codex/gh-{match.group(1)}-task"
        self._git(resolved_workspace, "checkout", "-b", branch, base_sha)
        self._exclude_runtime_metadata(resolved_workspace)
        metadata_directory = resolved_workspace / METADATA_DIRECTORY
        metadata_directory.mkdir()
        (metadata_directory / METADATA_FILE).write_text(
            json.dumps(
                {
                    "issue_identifier": issue_identifier,
                    "repository": self.trusted_repository,
                    "base_sha": base_sha,
                    "branch": branch,
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        return BootstrapResult(
            issue_identifier, self.trusted_repository, base_sha, branch
        )

    @staticmethod
    def _exclude_runtime_metadata(workspace: Path) -> None:
        exclude_path = workspace / ".git" / "info" / "exclude"
        existing = exclude_path.read_text(encoding="utf-8")
        entry = f"/{METADATA_DIRECTORY}/\n"
        if entry not in existing:
            exclude_path.write_text(existing + entry, encoding="utf-8")

    def _git(self, cwd: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        environment = trusted_process_environment()

        git_config = [
            ("protocol.allow", "never"),
            ("protocol.https.allow", "always"),
            ("http.version", "HTTP/1.1"),
            ("http.followRedirects", "false"),
            ("http.proxy", ""),
            ("http.saveCookies", "false"),
            ("http.lowSpeedLimit", "1"),
            ("http.lowSpeedTime", "30"),
            ("credential.helper", ""),
        ]
        if self.allow_local_repository:
            git_config.append(("protocol.file.allow", "always"))
        environment.update(
            {
                "GIT_CONFIG_GLOBAL": "/dev/null",
                "GIT_CONFIG_SYSTEM": "/dev/null",
                "GIT_CONFIG_NOSYSTEM": "1",
                "GIT_TERMINAL_PROMPT": "0",
                "GIT_CONFIG_COUNT": str(len(git_config)),
            }
        )
        for index, (key, value) in enumerate(git_config):
            environment[f"GIT_CONFIG_KEY_{index}"] = key
            environment[f"GIT_CONFIG_VALUE_{index}"] = value
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            env=environment,
            check=True,
            capture_output=True,
            text=True,
            timeout=GIT_COMMAND_TIMEOUT_SECONDS,
        )


class ReviewProposalStore:
    """Accepts the reviewer's untrusted proposal into host-owned state."""

    def __init__(self, state_root: Path):
        self.state_root = state_root.resolve()

    def submit(self, issue_identifier: str, payload: dict) -> Path:
        if ISSUE_WORKSPACE.fullmatch(issue_identifier) is None:
            raise ValueError("issue identifier must match GH-<positive-number>")
        snapshot = SnapshotStore(
            self.state_root / "tasks" / f"{issue_identifier}.json"
        ).load()
        if snapshot.iteration_phase != "review":
            raise RuntimeError("review proposal is accepted only in review phase")
        proposal = ReviewProposal.from_json(payload)
        if proposal.head_sha != snapshot.head_sha:
            raise ValueError("review proposal does not match candidate head")
        if (
            snapshot.candidate_revision is None
            or proposal.candidate_revision
            != snapshot.candidate_revision["candidate_revision"]
        ):
            raise ValueError("review proposal does not match candidate revision")

        destination = self.state_root / "proposals" / f"{issue_identifier}.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{destination.name}.", suffix=".tmp", dir=destination.parent
        )
        temporary_path = Path(temporary_name)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
                json.dump(proposal.to_json(), stream, indent=2, sort_keys=True)
                stream.write("\n")
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_path, destination)
        finally:
            temporary_path.unlink(missing_ok=True)
        return destination


class TaskStateInitializer:
    """Creates the host-owned snapshot paired with a new trusted workspace."""

    def __init__(self, state_root: Path):
        self.state_root = state_root.resolve()

    def initialize(
        self, result: BootstrapResult, workspace: Path, task_brief: TaskBrief
    ) -> Path:
        destination = self.state_root / "tasks" / f"{result.issue_identifier}.json"
        if task_brief.issue_identifier != result.issue_identifier:
            raise RuntimeError("task brief does not match trusted workspace")
        if destination.exists():
            snapshot = SnapshotStore(destination).load()
            expected = (
                result.issue_identifier,
                result.repository,
                result.base_sha,
                result.branch,
                str(workspace.resolve()),
            )
            actual = (
                snapshot.issue_identifier,
                snapshot.repository,
                snapshot.base_sha,
                snapshot.branch,
                snapshot.workspace,
                snapshot.task_brief,
            )
            expected = expected + (task_brief.to_json(),)
            if actual != expected:
                raise RuntimeError("existing task snapshot does not match trusted workspace")
            return destination.resolve()

        snapshot = TaskSnapshot(
            issue_identifier=result.issue_identifier,
            state="queued",
            repository=result.repository,
            base_sha=result.base_sha,
            head_sha=result.base_sha,
            branch=result.branch,
            workspace=str(workspace.resolve()),
            task_brief=task_brief.to_json(),
            iteration_phase="implement",
        )
        SnapshotStore(destination).save(snapshot)
        return destination.resolve()


@dataclass(frozen=True)
class PhaseContext:
    phase: str
    role: str
    run_model: bool
    complete_turn: bool
    thread_sandbox: str
    turn_sandbox_policy: dict
    head_sha: str
    candidate_revision: str | None
    model_workspace: str
    base_sync: dict[str, str] | None = None
    review_packet: dict | None = None

    def to_json(self) -> dict:
        return {
            "phase": self.phase,
            "role": self.role,
            "run_model": self.run_model,
            "complete_turn": self.complete_turn,
            "thread_sandbox": self.thread_sandbox,
            "turn_sandbox_policy": dict(self.turn_sandbox_policy),
            "head_sha": self.head_sha,
            "candidate_revision": self.candidate_revision,
            "model_workspace": self.model_workspace,
            "base_sync": dict(self.base_sync) if self.base_sync is not None else None,
            "review_packet": (
                dict(self.review_packet) if self.review_packet is not None else None
            ),
        }


class _TaskStateAccess:
    def __init__(self, state_root: Path):
        self.state_root = state_root.resolve()

    def _load_bound_snapshot(
        self, issue_identifier: str, workspace: Path
    ) -> tuple[SnapshotStore, TaskSnapshot]:
        if ISSUE_WORKSPACE.fullmatch(issue_identifier) is None:
            raise ValueError("issue identifier must match GH-<positive-number>")
        resolved_workspace = workspace.resolve()
        store = SnapshotStore(
            self.state_root / "tasks" / f"{issue_identifier}.json"
        )
        snapshot = store.load()
        if snapshot.workspace != str(resolved_workspace):
            raise RuntimeError("task snapshot does not match trusted workspace")
        actual_head = SymphonyWorkspaceBootstrap()._git(
            resolved_workspace, "rev-parse", "HEAD"
        ).stdout.strip()
        if actual_head != snapshot.head_sha:
            raise RuntimeError("workspace HEAD does not match host-owned snapshot")
        return store, snapshot


class PhaseContextStore(_TaskStateAccess):
    """Selects model execution policy from trusted task state and capability level."""

    def __init__(
        self,
        state_root: Path,
        *,
        workspace_write_enabled: bool,
        artifact_root: Path | None = None,
    ):
        super().__init__(state_root)
        self.workspace_write_enabled = workspace_write_enabled
        self.artifact_root = artifact_root.resolve() if artifact_root else None

    def load(self, issue_identifier: str, workspace: Path) -> PhaseContext:
        store, snapshot = self._load_bound_snapshot(issue_identifier, workspace)
        resolved_workspace = workspace.resolve()
        phase = snapshot.iteration_phase
        if phase == PHASE_IMPLEMENT and not self.workspace_write_enabled:
            return PhaseContext(
                phase=phase,
                role="observer",
                run_model=True,
                complete_turn=True,
                thread_sandbox="read-only",
                turn_sandbox_policy={"type": "readOnly", "networkAccess": False},
                head_sha=snapshot.head_sha or "",
                candidate_revision=None,
                model_workspace=str(resolved_workspace),
                base_sync=(dict(snapshot.base_sync) if snapshot.base_sync else None),
                review_packet=(
                    dict(snapshot.github_review_packet)
                    if snapshot.github_review_packet else None
                ),
            )
        if phase == PHASE_IMPLEMENT:
            return PhaseContext(
                phase=phase,
                role="implementer",
                run_model=True,
                complete_turn=True,
                thread_sandbox="workspace-write",
                turn_sandbox_policy={
                    "type": "workspaceWrite",
                    "writableRoots": [],
                    "networkAccess": False,
                },
                head_sha=snapshot.head_sha or "",
                candidate_revision=None,
                model_workspace=str(resolved_workspace),
                base_sync=(dict(snapshot.base_sync) if snapshot.base_sync else None),
                review_packet=(
                    dict(snapshot.github_review_packet)
                    if snapshot.github_review_packet else None
                ),
            )
        if phase == PHASE_REVIEW:
            if not snapshot.implementer_session_id:
                raise RuntimeError("review phase has no trusted implementer receipt")
            revision = self._validated_review_evidence(snapshot)
            if self.artifact_root is None:
                raise RuntimeError("review phase requires candidate artifact storage")
            if snapshot.review_workspace is None:
                review_root = self.artifact_root / "reviews"
                review_root.mkdir(parents=True, exist_ok=True)
                destination = review_root / (
                    f"{revision.candidate_revision}-{secrets.token_hex(8)}"
                )
                CandidateSnapshotter(resolved_workspace, self.artifact_root).materialize(
                    revision, destination
                )
                self._make_read_only(destination)
                snapshot.review_workspace = str(destination)
                store.save(snapshot)
            review_workspace = Path(snapshot.review_workspace)
            if (
                review_workspace.is_symlink()
                or not review_workspace.is_dir()
                or review_workspace.parent != self.artifact_root / "reviews"
            ):
                raise RuntimeError("review workspace is missing or unsafe")
            CandidateSnapshotter(
                resolved_workspace, self.artifact_root
            ).verify_materialized(revision, review_workspace)
            return PhaseContext(
                phase=phase,
                role="reviewer",
                run_model=True,
                complete_turn=True,
                thread_sandbox="read-only",
                turn_sandbox_policy={"type": "readOnly", "networkAccess": False},
                head_sha=snapshot.head_sha or "",
                candidate_revision=revision.candidate_revision,
                model_workspace=str(review_workspace),
                base_sync=(dict(snapshot.base_sync) if snapshot.base_sync else None),
                review_packet=None,
            )
        if phase in {PHASE_VALIDATE, PHASE_COMPLETE}:
            return PhaseContext(
                phase=phase,
                role="none",
                run_model=False,
                complete_turn=False,
                thread_sandbox="read-only",
                turn_sandbox_policy={"type": "readOnly", "networkAccess": False},
                head_sha=snapshot.head_sha or "",
                candidate_revision=(
                    snapshot.candidate_revision["candidate_revision"]
                    if snapshot.candidate_revision is not None
                    else None
                ),
                model_workspace=str(resolved_workspace),
                base_sync=(dict(snapshot.base_sync) if snapshot.base_sync else None),
                review_packet=None,
            )
        raise RuntimeError(f"unsupported iteration phase: {phase}")

    @staticmethod
    def _validated_review_evidence(snapshot: TaskSnapshot) -> CandidateRevision:
        if (
            snapshot.candidate_revision is None
            or snapshot.gate_request is None
            or snapshot.gate_receipt is None
        ):
            raise RuntimeError("review phase has no exact candidate gate evidence")
        revision = CandidateRevision.from_json(snapshot.candidate_revision)
        request = GateRequest.from_json(snapshot.gate_request)
        receipt = GateReceipt.from_json(snapshot.gate_receipt)
        if receipt.verdict != "PASS":
            raise RuntimeError("review phase requires a passing gate receipt")
        if (
            request.issue_identifier != snapshot.issue_identifier
            or receipt.issue_identifier != snapshot.issue_identifier
            or request.candidate_revision != revision
            or receipt.candidate_revision != revision
            or receipt.gate_id != request.gate_id
            or receipt.runner_image != request.runner_image
            or receipt.command_policy_sha256 != request.command_policy_sha256
        ):
            raise RuntimeError("review phase gate evidence does not match task identity")
        return revision

    @staticmethod
    def _make_read_only(root: Path) -> None:
        for path in sorted(root.rglob("*"), reverse=True):
            if path.is_symlink():
                continue
            mode = path.stat().st_mode
            os.chmod(path, 0o555 if path.is_dir() or mode & 0o111 else 0o444)
        os.chmod(root, 0o555)


class TurnStateController(_TaskStateAccess):
    """Binds a trusted Symphony receipt to the current phase without running code."""

    def __init__(
        self,
        state_root: Path,
        *,
        workspace_write_enabled: bool,
        artifact_root: Path | None = None,
        contract_path: Path | None = None,
    ):
        super().__init__(state_root)
        self.workspace_write_enabled = workspace_write_enabled
        self.artifact_root = artifact_root.resolve() if artifact_root else None
        if contract_path is not None:
            resolved_contract = contract_path.resolve()
        else:
            deployed_contract = Path(
                "/opt/jstore-agentic-controller/state-contract.json"
            )
            resolved_contract = (
                deployed_contract
                if deployed_contract.is_file()
                else Path(__file__).resolve().parents[2]
                / "config"
                / "agentic-cicd"
                / "state-contract.json"
            )
        contract = json.loads(resolved_contract.read_text(encoding="utf-8"))
        if not isinstance(contract, dict):
            raise ValueError("state contract must be a JSON object")
        self.coordinator = Coordinator.from_contract(contract)

    def complete_turn(
        self,
        issue_identifier: str,
        workspace: Path,
        *,
        session_id: str,
        thread_id: str,
        turn_id: str,
        expected_phase: str,
        expected_role: str,
        expected_head_sha: str,
        expected_candidate_revision: str | None,
        wall_clock_seconds: int = 0,
        input_tokens: int = 0,
        output_tokens: int = 0,
        outcome: str = "succeeded",
        token_usage_observed: bool = True,
    ) -> None:
        store, snapshot = self._load_bound_snapshot(issue_identifier, workspace)
        self._require_invocation_binding(
            snapshot,
            expected_phase=expected_phase,
            expected_role=expected_role,
            expected_head_sha=expected_head_sha,
            expected_candidate_revision=expected_candidate_revision,
        )
        if type(wall_clock_seconds) is not int or wall_clock_seconds < 0:
            raise ValueError("wall-clock usage must be a non-negative integer")
        if outcome not in {"succeeded", "failed"}:
            raise ValueError("turn outcome must be succeeded or failed")
        if type(token_usage_observed) is not bool:
            raise ValueError("token_usage_observed must be a boolean")
        receipt_key = "turn:" + json.dumps(
            [session_id, thread_id, turn_id], separators=(",", ":")
        )
        if not snapshot.consume_idempotency_key(receipt_key):
            raise RuntimeError("turn receipt was already consumed")
        try:
            self.coordinator.consume_budget(
                snapshot,
                turns=1,
                wall_clock_seconds=wall_clock_seconds,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
            )
        except BudgetExceeded:
            store.save(snapshot)
            raise
        role = expected_role
        receipt = TurnReceipt(
            session_id=session_id,
            thread_id=thread_id,
            turn_id=turn_id,
            role=role,
            head_sha=snapshot.head_sha or "",
            candidate_revision=(
                snapshot.candidate_revision["candidate_revision"]
                if role == "reviewer" and snapshot.candidate_revision is not None
                else None
            ),
        )
        bridge = SymphonyPhaseBridge()
        if not token_usage_observed:
            bridge.record_terminal_turn(
                snapshot,
                receipt,
                outcome=outcome,
                token_usage_observed=False,
            )
            self.coordinator.transition(snapshot, "blocked")
            snapshot.blocked_reason = "receipt:missing-token-usage"
            snapshot.claim_id = None
            store.save(snapshot)
            raise RuntimeError(snapshot.blocked_reason)
        if outcome == "failed":
            bridge.record_terminal_turn(
                snapshot,
                receipt,
                outcome=outcome,
                token_usage_observed=True,
            )
            store.save(snapshot)
            return
        consumed_proposal: Path | None = None
        if role == "implementer":
            snapshot.candidate_revision = None
            snapshot.candidate_commit_sha = None
            snapshot.gate_request = None
            snapshot.gate_receipt = None
            snapshot.review_workspace = None
            snapshot.github_review_packet = None
            bridge.complete_implementation(snapshot, receipt)
        elif role == "observer":
            bridge.complete_observation(snapshot, receipt)
        else:
            if self.artifact_root is None:
                raise RuntimeError("review completion requires candidate artifact storage")
            revision = PhaseContextStore._validated_review_evidence(snapshot)
            if snapshot.review_workspace is None:
                raise RuntimeError("review completion has no materialized candidate")
            review_workspace = Path(snapshot.review_workspace)
            if (
                review_workspace.is_symlink()
                or not review_workspace.is_dir()
                or review_workspace.parent != self.artifact_root / "reviews"
            ):
                raise RuntimeError("review workspace is missing or unsafe")
            CandidateSnapshotter(
                workspace.resolve(), self.artifact_root
            ).verify_materialized(revision, review_workspace)
            proposal_path = (
                self.state_root / "proposals" / f"{issue_identifier}.json"
            )
            payload = json.loads(proposal_path.read_text(encoding="utf-8"))
            if not isinstance(payload, dict):
                raise ValueError("review proposal must be a JSON object")
            bridge.complete_review(snapshot, receipt, ReviewProposal.from_json(payload))
            consumed_proposal = proposal_path
        store.save(snapshot)
        if consumed_proposal is not None:
            consumed_proposal.unlink()

    def _require_invocation_binding(
        self,
        snapshot: TaskSnapshot,
        *,
        expected_phase: str,
        expected_role: str,
        expected_head_sha: str,
        expected_candidate_revision: str | None,
    ) -> None:
        if snapshot.iteration_phase != expected_phase:
            raise RuntimeError(
                "trusted invocation phase no longer matches current task phase"
            )
        if expected_phase == PHASE_IMPLEMENT:
            actual_role = "implementer" if self.workspace_write_enabled else "observer"
            actual_candidate_revision = None
        elif expected_phase == PHASE_REVIEW:
            actual_role = "reviewer"
            actual_candidate_revision = (
                snapshot.candidate_revision["candidate_revision"]
                if snapshot.candidate_revision is not None
                else None
            )
        else:
            raise RuntimeError("trusted invocation phase does not accept a model turn")
        if expected_role != actual_role:
            raise RuntimeError("trusted invocation role does not match current capability")
        if expected_head_sha != snapshot.head_sha:
            raise RuntimeError("trusted invocation head no longer matches task head")
        if expected_candidate_revision != actual_candidate_revision:
            raise RuntimeError(
                "trusted invocation candidate no longer matches task candidate"
            )


class GateRequestStore(_TaskStateAccess):
    """Atomically binds one host-owned gate request to a frozen candidate."""

    def __init__(
        self,
        state_root: Path,
        *,
        allowed_runner_images: set[str],
        allowed_validation_commands: set[tuple[str, ...]],
        maximum_timeout_seconds: int,
        gate_enabled: bool = False,
    ):
        super().__init__(state_root)
        self.allowed_runner_images = frozenset(allowed_runner_images)
        self.allowed_validation_commands = frozenset(allowed_validation_commands)
        self.maximum_timeout_seconds = maximum_timeout_seconds
        self.gate_enabled = gate_enabled

    def record(self, issue_identifier: str, payload: dict) -> GateRequest:
        request = GateRequest.from_json(payload)
        if not self.gate_enabled:
            raise RuntimeError("isolated gate capability is disabled")
        if request.issue_identifier != issue_identifier:
            raise RuntimeError("gate request issue does not match task")
        if request.runner_image not in self.allowed_runner_images:
            raise RuntimeError("gate runner image is not allowlisted")
        if request.validation_commands not in self.allowed_validation_commands:
            raise RuntimeError("gate validation commands are not in the trusted policy")
        if request.timeout_seconds > self.maximum_timeout_seconds:
            raise RuntimeError("gate timeout exceeds the trusted policy")
        store = SnapshotStore(self.state_root / "tasks" / f"{issue_identifier}.json")
        snapshot = store.load()
        if snapshot.state not in {"queued", "waiting_ci"}:
            raise RuntimeError("task state does not accept a gate request")
        if snapshot.iteration_phase != PHASE_VALIDATE:
            raise RuntimeError("gate request is accepted only in validate phase")
        if snapshot.candidate_revision != request.candidate_revision.to_json():
            raise RuntimeError("gate request does not match frozen candidate")
        if f"gate:{request.gate_id}" in snapshot.consumed_idempotency_keys:
            raise RuntimeError("gate_id was already consumed")
        if snapshot.gate_request is not None:
            existing = GateRequest.from_json(snapshot.gate_request)
            if existing == request:
                return existing
            raise RuntimeError("validate phase already binds a different gate request")
        snapshot.gate_request = request.to_json()
        snapshot.gate_receipt = None
        store.save(snapshot)
        return request


class GateReceiptStore(_TaskStateAccess):
    """Consumes a trusted deterministic receipt for the active GateRequest."""

    def __init__(
        self,
        state_root: Path,
        *,
        contract_path: Path,
        gate_enabled: bool = False,
    ):
        super().__init__(state_root)
        contract = json.loads(contract_path.read_text(encoding="utf-8"))
        limit = contract.get("limits", {}).get("infrastructure_retries")
        if isinstance(limit, bool) or not isinstance(limit, int) or limit < 0:
            raise ValueError("state contract infrastructure_retries is invalid")
        self.infrastructure_retry_limit = limit
        self.gate_enabled = gate_enabled

    def record(self, issue_identifier: str, payload: dict) -> None:
        if ISSUE_WORKSPACE.fullmatch(issue_identifier) is None:
            raise ValueError("issue identifier must match GH-<positive-number>")
        if not self.gate_enabled:
            raise RuntimeError("isolated gate capability is disabled")
        store = SnapshotStore(
            self.state_root / "tasks" / f"{issue_identifier}.json"
        )
        snapshot = store.load()
        receipt = GateReceipt.from_json(payload)
        if snapshot.state not in {"queued", "waiting_ci"}:
            raise RuntimeError("task state does not accept a gate receipt")
        if snapshot.gate_request is None:
            raise RuntimeError("gate receipt has no active request")
        request = GateRequest.from_json(snapshot.gate_request)
        if (
            receipt.gate_id != request.gate_id
            or receipt.issue_identifier != request.issue_identifier
            or receipt.candidate_revision != request.candidate_revision
            or receipt.runner_image != request.runner_image
            or receipt.command_policy_sha256 != request.command_policy_sha256
        ):
            raise RuntimeError("gate receipt does not match the active request")
        snapshot.gate_receipt = receipt.to_json()
        if receipt.verdict == "INFRASTRUCTURE_FAILURE":
            if not snapshot.consume_idempotency_key(f"gate:{receipt.gate_id}"):
                raise RuntimeError("gate receipt was already consumed")
            if snapshot.infrastructure_retries >= self.infrastructure_retry_limit:
                snapshot.state = "blocked"
                snapshot.blocked_reason = "infrastructure-retry-limit"
                snapshot.claim_id = None
            else:
                snapshot.infrastructure_retries += 1
            snapshot.gate_request = None
            store.save(snapshot)
            return
        SymphonyPhaseBridge().complete_validation(snapshot, receipt)
        store.save(snapshot)


class CandidateRevisionStore(_TaskStateAccess):
    """Freezes one implementer worktree into host-owned immutable artifacts."""

    def __init__(
        self,
        state_root: Path,
        *,
        artifact_root: Path | None = None,
        freeze_enabled: bool = False,
    ):
        super().__init__(state_root)
        self.artifact_root = (
            (self.state_root / "candidates")
            if artifact_root is None
            else artifact_root.resolve()
        )
        self.freeze_enabled = freeze_enabled

    def freeze(self, issue_identifier: str, workspace: Path) -> CandidateRevision:
        if not self.freeze_enabled:
            raise RuntimeError("candidate freeze capability is disabled")
        store, snapshot = self._load_bound_snapshot(issue_identifier, workspace)
        if snapshot.iteration_phase != PHASE_VALIDATE:
            raise RuntimeError("candidate freeze is accepted only in validate phase")
        if snapshot.head_sha is None:
            raise RuntimeError("task snapshot has no trusted head SHA")
        self._validate_runtime_metadata(snapshot, workspace.resolve())
        revision = CandidateSnapshotter(
            workspace, self.artifact_root
        ).freeze(snapshot.head_sha)
        if snapshot.candidate_revision is not None:
            existing = CandidateRevision.from_json(snapshot.candidate_revision)
            if existing != revision:
                raise RuntimeError("validate phase already binds a different candidate")
            return existing
        snapshot.candidate_revision = revision.to_json()
        snapshot.candidate_commit_sha = None
        snapshot.gate_request = None
        snapshot.gate_receipt = None
        store.save(snapshot)
        return revision

    @staticmethod
    def _validate_runtime_metadata(snapshot: TaskSnapshot, workspace: Path) -> None:
        metadata_path = workspace / METADATA_DIRECTORY / METADATA_FILE
        if not metadata_path.is_file() or metadata_path.is_symlink():
            raise RuntimeError("trusted workspace metadata is missing or unsafe")
        payload = json.loads(metadata_path.read_text(encoding="utf-8"))
        expected = {
            "issue_identifier": snapshot.issue_identifier,
            "base_sha": snapshot.base_sha,
            "branch": snapshot.branch,
        }
        if payload != expected:
            raise RuntimeError("trusted workspace metadata does not match task state")
