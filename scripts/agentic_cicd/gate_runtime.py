from __future__ import annotations

import json
import os
import re
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .candidate import CandidateRevision
from .coordinator import Coordinator, SnapshotStore
from .gate_dispatcher import GateDispatcher, GateInfrastructureError
from .phase_bridge import PHASE_IMPLEMENT, PHASE_VALIDATE
from .protocol import GateReceipt, GateRequest
from .runtime_controller import CandidateRevisionStore, GateReceiptStore, GateRequestStore


PINNED_NON_SENTINEL_IMAGE = re.compile(
    r"[^\s@]+@sha256:(?!0{64}\Z)[0-9a-f]{64}\Z"
)
TRUSTED_VALIDATION_COMMANDS = ("/opt/jstore-gate/run-quality-gate",)


@dataclass(frozen=True)
class GatePolicy:
    runner_image: str
    fetch_image: str
    validation_commands: tuple[str, ...]
    timeout_seconds: int

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "GatePolicy":
        required = {
            "runner_image",
            "fetch_image",
            "validation_commands",
            "timeout_seconds",
        }
        if set(payload) != required:
            raise ValueError("gate policy fields do not match the contract")
        if not isinstance(payload["validation_commands"], list) or not all(
            isinstance(value, str) for value in payload["validation_commands"]
        ):
            raise ValueError("gate policy commands must be an array of strings")
        policy = cls(
            runner_image=payload["runner_image"],
            fetch_image=payload["fetch_image"],
            validation_commands=tuple(payload["validation_commands"]),
            timeout_seconds=payload["timeout_seconds"],
        )
        GateRequest(
            gate_id="gate-policy-validation",
            issue_identifier="GH-1",
            candidate_revision=_policy_candidate(),
            runner_image=policy.runner_image,
            command_policy_sha256=policy.command_policy_sha256,
            validation_commands=policy.validation_commands,
            timeout_seconds=policy.timeout_seconds,
            requested_at="1970-01-01T00:00:00Z",
        )
        if not PINNED_NON_SENTINEL_IMAGE.fullmatch(policy.fetch_image):
            raise ValueError("fetch_image must use a non-sentinel immutable digest")
        if not PINNED_NON_SENTINEL_IMAGE.fullmatch(policy.runner_image):
            raise ValueError("runner_image must use a non-sentinel immutable digest")
        if policy.timeout_seconds < 60 or policy.timeout_seconds > 3600:
            raise ValueError("gate policy timeout must be between 60 and 3600 seconds")
        if policy.validation_commands != TRUSTED_VALIDATION_COMMANDS:
            raise ValueError("gate policy must invoke the trusted runner entrypoint")
        return policy

    @classmethod
    def load(cls, path: Path) -> "GatePolicy":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise ValueError("gate policy must be a JSON object")
        return cls.from_json(payload)

    @property
    def command_policy_sha256(self) -> str:
        return GateRequest.calculate_command_policy_sha256(self.validation_commands)


def _policy_candidate() -> CandidateRevision:
    base, tree, artifact, policy = "a" * 40, "b" * 40, "c" * 64, "d" * 64
    return CandidateRevision(
        base,
        tree,
        artifact,
        policy,
        CandidateRevision.calculate_revision(base, tree, artifact, policy),
    )


class GateMailbox:
    """Exchanges exact request/receipt files on a credential-free trusted volume."""

    def __init__(self, root: Path):
        self.root = root.resolve()
        self.requests = self.root / "requests"
        self.receipts = self.root / "receipts"
        self.requests.mkdir(parents=True, exist_ok=True, mode=0o770)
        self.receipts.mkdir(parents=True, exist_ok=True, mode=0o770)

    def publish_request(self, request: GateRequest) -> Path:
        return self._publish(self.requests, request.gate_id, request.to_json())

    def publish_receipt(self, receipt: GateReceipt) -> Path:
        return self._publish(self.receipts, receipt.gate_id, receipt.to_json())

    def read_request(self, path: Path) -> GateRequest:
        return GateRequest.from_json(self._read(path, self.requests))

    def read_receipt(self, gate_id: str) -> GateReceipt | None:
        path = self.receipts / f"{gate_id}.json"
        if not path.exists():
            return None
        return GateReceipt.from_json(self._read(path, self.receipts))

    def pending_requests(self) -> list[Path]:
        return sorted(self.requests.glob("gate-*.json"))

    def complete(self, gate_id: str) -> None:
        path = self.requests / f"{gate_id}.json"
        if path.is_symlink():
            raise RuntimeError("gate mailbox entry must not be a symlink")
        path.unlink(missing_ok=True)

    def cleanup_complete(self, gate_id: str) -> bool:
        marker = self.receipts / f".cleanup-{gate_id}.json"
        return marker.is_file() and not marker.is_symlink()

    def mark_cleanup_complete(self, gate_id: str) -> None:
        self._publish(
            self.receipts,
            f".cleanup-{gate_id}",
            {"gate_id": gate_id, "job_deleted": True},
        )

    def receipt_gate_ids(self) -> list[str]:
        return sorted(path.stem for path in self.receipts.glob("gate-*.json"))

    @staticmethod
    def _publish(directory: Path, gate_id: str, payload: dict[str, Any]) -> Path:
        destination = directory / f"{gate_id}.json"
        encoded = (json.dumps(payload, separators=(",", ":"), sort_keys=True) + "\n").encode()
        if destination.exists():
            if destination.is_symlink() or destination.read_bytes() != encoded:
                raise RuntimeError("gate mailbox identity collision")
            return destination
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{gate_id}.", suffix=".tmp", dir=directory
        )
        temporary = Path(temporary_name)
        try:
            with os.fdopen(descriptor, "wb") as stream:
                os.fchmod(stream.fileno(), 0o640)
                stream.write(encoded)
                stream.flush()
                os.fsync(stream.fileno())
            try:
                os.link(temporary, destination, follow_symlinks=False)
            except FileExistsError:
                if destination.is_symlink() or destination.read_bytes() != encoded:
                    raise RuntimeError("gate mailbox identity collision")
            return destination
        finally:
            temporary.unlink(missing_ok=True)

    @staticmethod
    def _read(path: Path, expected_parent: Path) -> dict[str, Any]:
        if path.parent.resolve() != expected_parent or path.is_symlink():
            raise RuntimeError("gate mailbox path is unsafe")
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
            payload = json.load(stream)
        if not isinstance(payload, dict):
            raise ValueError("gate mailbox payload must be an object")
        return payload


class ValidatePhaseDriver:
    """Freezes, dispatches and consumes gate evidence without running candidate code."""

    def __init__(
        self,
        *,
        state_root: Path,
        artifact_root: Path,
        mailbox: GateMailbox,
        policy: GatePolicy,
        contract_path: Path,
        enabled: bool,
    ):
        self.state_root = state_root
        self.artifact_root = artifact_root
        self.mailbox = mailbox
        self.policy = policy
        self.contract_path = contract_path
        self.enabled = enabled

    def advance(self, issue_identifier: str, workspace: Path) -> None:
        if not self.enabled:
            return
        snapshot_path = self.state_root / "tasks" / f"{issue_identifier}.json"
        snapshot = SnapshotStore(snapshot_path).load()
        if snapshot.iteration_phase != PHASE_VALIDATE:
            return
        if snapshot.gate_request is not None:
            active = GateRequest.from_json(snapshot.gate_request)
            receipt = self.mailbox.read_receipt(active.gate_id)
            if receipt is not None:
                GateReceiptStore(
                    self.state_root,
                    contract_path=self.contract_path,
                    gate_enabled=True,
                ).record(issue_identifier, receipt.to_json())
                self.mailbox.complete(active.gate_id)
                snapshot = SnapshotStore(snapshot_path).load()
                if snapshot.iteration_phase != PHASE_VALIDATE:
                    return

        revision = CandidateRevisionStore(
            self.state_root,
            artifact_root=self.artifact_root,
            freeze_enabled=True,
        ).freeze(issue_identifier, workspace)
        snapshot = SnapshotStore(snapshot_path).load()
        gate_id = self._gate_id(
            snapshot.issue_identifier, revision, snapshot.infrastructure_retries
        )
        if f"gate:{gate_id}" in snapshot.consumed_idempotency_keys:
            self._return_duplicate_candidate_to_implementation(snapshot_path, snapshot)
            return
        if not self._record_semantic_fix_budget(snapshot_path, snapshot, revision):
            return
        if snapshot.gate_request is None:
            request = GateRequest(
                gate_id=gate_id,
                issue_identifier=snapshot.issue_identifier,
                candidate_revision=revision,
                runner_image=self.policy.runner_image,
                command_policy_sha256=self.policy.command_policy_sha256,
                validation_commands=self.policy.validation_commands,
                timeout_seconds=self.policy.timeout_seconds,
                requested_at=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            )
            GateRequestStore(
                self.state_root,
                allowed_runner_images={self.policy.runner_image},
                allowed_validation_commands={self.policy.validation_commands},
                maximum_timeout_seconds=self.policy.timeout_seconds,
                gate_enabled=True,
            ).record(issue_identifier, request.to_json())
        else:
            request = GateRequest.from_json(snapshot.gate_request)
        self.mailbox.publish_request(request)

    def _record_semantic_fix_budget(
        self,
        snapshot_path: Path,
        snapshot,
        revision: CandidateRevision,
    ) -> bool:
        if not snapshot.pending_review_findings:
            return True
        contract = json.loads(self.contract_path.read_text(encoding="utf-8"))
        coordinator = Coordinator.from_contract(contract)
        for finding in snapshot.pending_review_findings:
            root_cause = finding.get("root_cause_id")
            if not isinstance(root_cause, str):
                raise RuntimeError("pending finding has no stable root cause")
            outcome = coordinator.record_failed_semantic_fix(
                snapshot, root_cause, revision.candidate_revision
            )
            if outcome == "fused":
                SnapshotStore(snapshot_path).save(snapshot)
                return False
            if outcome == "duplicate":
                self._return_duplicate_candidate_to_implementation(
                    snapshot_path, snapshot
                )
                return False
        SnapshotStore(snapshot_path).save(snapshot)
        return True

    @staticmethod
    def _return_duplicate_candidate_to_implementation(
        snapshot_path: Path, snapshot
    ) -> None:
        snapshot.candidate_revision = None
        snapshot.candidate_commit_sha = None
        snapshot.implementer_session_id = None
        snapshot.iteration_phase = PHASE_IMPLEMENT
        SnapshotStore(snapshot_path).save(snapshot)

    @staticmethod
    def _gate_id(issue: str, revision: CandidateRevision, retry: int) -> str:
        return f"gate-{issue.lower()}-{revision.candidate_revision[:16]}-{retry}"


class GateDispatcherService:
    def __init__(
        self,
        mailbox: GateMailbox,
        dispatcher: GateDispatcher,
        policy: GatePolicy,
    ):
        self.mailbox = mailbox
        self.dispatcher = dispatcher
        self.policy = policy

    def process_once(self) -> int:
        processed = 0
        for gate_id in self.mailbox.receipt_gate_ids():
            self._cleanup(gate_id)
        for path in self.mailbox.pending_requests():
            request = self.mailbox.read_request(path)
            if self.mailbox.read_receipt(request.gate_id) is not None:
                self._cleanup(request.gate_id)
                continue
            self._validate_policy(request)
            receipt = self.dispatcher.dispatch(request)
            self.mailbox.publish_receipt(receipt)
            self._cleanup(request.gate_id)
            processed += 1
        return processed

    def _cleanup(self, gate_id: str) -> None:
        if self.mailbox.cleanup_complete(gate_id):
            return
        try:
            self.dispatcher.cleanup(gate_id)
        except GateInfrastructureError:
            return
        self.mailbox.mark_cleanup_complete(gate_id)

    def serve(self, interval_seconds: float = 1.0) -> None:
        while True:
            self.process_once()
            time.sleep(interval_seconds)

    def _validate_policy(self, request: GateRequest) -> None:
        if (
            request.runner_image != self.policy.runner_image
            or request.validation_commands != self.policy.validation_commands
            or request.command_policy_sha256 != self.policy.command_policy_sha256
            or request.timeout_seconds != self.policy.timeout_seconds
        ):
            raise RuntimeError("gate request differs from dispatcher policy")
