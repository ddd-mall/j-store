from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from .coordinator import SnapshotStore, TaskSnapshot
from .phase_bridge import (
    PHASE_COMPLETE,
    PHASE_IMPLEMENT,
    PHASE_REVIEW,
    PHASE_VALIDATE,
    SymphonyPhaseBridge,
)
from .protocol import GateReceipt, ReviewProposal, TurnReceipt


ISSUE_WORKSPACE = re.compile(r"GH-([1-9][0-9]*)\Z")
FULL_SHA = re.compile(r"[0-9a-f]{40}\Z")
METADATA_DIRECTORY = ".agentic-cicd"
METADATA_FILE = "workspace.json"


@dataclass(frozen=True)
class BootstrapResult:
    issue_identifier: str
    base_sha: str
    branch: str


class SymphonyWorkspaceBootstrap:
    """Creates one Symphony workspace from the fetched develop ref."""

    def bootstrap(self, *, repository_url: str, workspace: Path) -> BootstrapResult:
        resolved_workspace = workspace.resolve()
        match = ISSUE_WORKSPACE.fullmatch(resolved_workspace.name)
        if match is None:
            raise ValueError("workspace basename must match GH-<positive-number>")
        normalized_url = repository_url.strip()
        if not normalized_url:
            raise ValueError("repository_url must not be blank")

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
                    "base_sha": base_sha,
                    "branch": branch,
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        return BootstrapResult(issue_identifier, base_sha, branch)

    @staticmethod
    def _exclude_runtime_metadata(workspace: Path) -> None:
        exclude_path = workspace / ".git" / "info" / "exclude"
        existing = exclude_path.read_text(encoding="utf-8")
        entry = f"/{METADATA_DIRECTORY}/\n"
        if entry not in existing:
            exclude_path.write_text(existing + entry, encoding="utf-8")

    @staticmethod
    def _git(cwd: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
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

    def initialize(self, result: BootstrapResult, workspace: Path) -> Path:
        destination = self.state_root / "tasks" / f"{result.issue_identifier}.json"
        if destination.exists():
            snapshot = SnapshotStore(destination).load()
            expected = (
                result.issue_identifier,
                result.base_sha,
                result.branch,
                str(workspace.resolve()),
            )
            actual = (
                snapshot.issue_identifier,
                snapshot.base_sha,
                snapshot.branch,
                snapshot.workspace,
            )
            if actual != expected:
                raise RuntimeError("existing task snapshot does not match trusted workspace")
            return destination.resolve()

        snapshot = TaskSnapshot(
            issue_identifier=result.issue_identifier,
            state="queued",
            base_sha=result.base_sha,
            head_sha=result.base_sha,
            branch=result.branch,
            workspace=str(workspace.resolve()),
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

    def to_json(self) -> dict:
        return {
            "phase": self.phase,
            "role": self.role,
            "run_model": self.run_model,
            "complete_turn": self.complete_turn,
            "thread_sandbox": self.thread_sandbox,
            "turn_sandbox_policy": dict(self.turn_sandbox_policy),
            "head_sha": self.head_sha,
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
        actual_head = SymphonyWorkspaceBootstrap._git(
            resolved_workspace, "rev-parse", "HEAD"
        ).stdout.strip()
        if actual_head != snapshot.head_sha:
            raise RuntimeError("workspace HEAD does not match host-owned snapshot")
        return store, snapshot


class PhaseContextStore(_TaskStateAccess):
    """Selects model execution policy from trusted task state and capability level."""

    def __init__(self, state_root: Path, *, workspace_write_enabled: bool):
        super().__init__(state_root)
        self.workspace_write_enabled = workspace_write_enabled

    def load(self, issue_identifier: str, workspace: Path) -> PhaseContext:
        _store, snapshot = self._load_bound_snapshot(issue_identifier, workspace)
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
            )
        if phase == PHASE_REVIEW:
            if not snapshot.implementer_session_id:
                raise RuntimeError("review phase has no trusted implementer receipt")
            return PhaseContext(
                phase=phase,
                role="reviewer",
                run_model=True,
                complete_turn=True,
                thread_sandbox="read-only",
                turn_sandbox_policy={"type": "readOnly", "networkAccess": False},
                head_sha=snapshot.head_sha or "",
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
            )
        raise RuntimeError(f"unsupported iteration phase: {phase}")


class TurnStateController(_TaskStateAccess):
    """Binds a trusted Symphony receipt to the current phase without running code."""

    def __init__(self, state_root: Path, *, workspace_write_enabled: bool):
        super().__init__(state_root)
        self.workspace_write_enabled = workspace_write_enabled

    def complete_turn(
        self,
        issue_identifier: str,
        workspace: Path,
        *,
        session_id: str,
        thread_id: str,
        turn_id: str,
    ) -> None:
        store, snapshot = self._load_bound_snapshot(issue_identifier, workspace)
        if snapshot.iteration_phase == PHASE_IMPLEMENT:
            role = "implementer" if self.workspace_write_enabled else "observer"
        elif snapshot.iteration_phase == PHASE_REVIEW:
            role = "reviewer"
        else:
            raise RuntimeError("current phase does not accept a model turn receipt")
        receipt = TurnReceipt(
            session_id=session_id,
            thread_id=thread_id,
            turn_id=turn_id,
            role=role,
            head_sha=snapshot.head_sha or "",
        )
        bridge = SymphonyPhaseBridge()
        consumed_proposal: Path | None = None
        if role == "implementer":
            bridge.complete_implementation(snapshot, receipt)
        elif role == "observer":
            bridge.complete_observation(snapshot, receipt)
        else:
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


class GateReceiptStore(_TaskStateAccess):
    """Consumes a host-supplied deterministic gate receipt for the frozen head."""

    def record(self, issue_identifier: str, payload: dict) -> None:
        if ISSUE_WORKSPACE.fullmatch(issue_identifier) is None:
            raise ValueError("issue identifier must match GH-<positive-number>")
        store = SnapshotStore(
            self.state_root / "tasks" / f"{issue_identifier}.json"
        )
        snapshot = store.load()
        receipt = GateReceipt.from_json(payload)
        SymphonyPhaseBridge().complete_validation(snapshot, receipt)
        store.save(snapshot)
