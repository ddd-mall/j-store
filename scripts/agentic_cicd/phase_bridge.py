from __future__ import annotations

from dataclasses import dataclass

from .coordinator import TaskSnapshot
from .protocol import (
    FULL_SHA,
    GateReceipt,
    IterationPacket,
    ReviewDecision,
    ReviewFinding,
    ReviewProposal,
    TurnReceipt,
)
from .workspace import BaseSyncResult


PHASE_IMPLEMENT = "implement"
PHASE_VALIDATE = "validate"
PHASE_REVIEW = "review"
PHASE_COMPLETE = "complete"
PHASES = {PHASE_IMPLEMENT, PHASE_VALIDATE, PHASE_REVIEW, PHASE_COMPLETE}


class PhaseBridgeError(RuntimeError):
    """Raised when a Symphony turn cannot advance trusted task state."""


@dataclass(frozen=True)
class IterationInputs:
    objective: str
    acceptance: tuple[str, ...]
    ci_failures: tuple[dict, ...]
    attempts_by_root_cause: dict[str, int]
    budget_remaining: dict[str, int]
    validation_commands: tuple[str, ...]


class SymphonyPhaseBridge:
    """Host-owned implement/review state around one-turn Symphony invocations.

    The bridge never starts Codex. Symphony remains the only owner of App Server
    sessions and supplies a trusted TurnReceipt after each invocation.
    """

    def prepare_packet(
        self, snapshot: TaskSnapshot, inputs: IterationInputs
    ) -> IterationPacket:
        self._validate_phase(snapshot)
        if snapshot.iteration_phase == PHASE_COMPLETE:
            raise PhaseBridgeError("completed iteration cannot prepare another turn")
        if snapshot.iteration_phase == PHASE_VALIDATE:
            raise PhaseBridgeError("validate phase does not start a model turn")
        if snapshot.base_sha is None or snapshot.head_sha is None:
            raise PhaseBridgeError("snapshot must bind base_sha and head_sha")
        if snapshot.iteration_phase == PHASE_REVIEW and not snapshot.implementer_session_id:
            raise PhaseBridgeError("review phase requires a trusted implementer receipt")

        return IterationPacket(
            issue_identifier=snapshot.issue_identifier,
            objective=inputs.objective,
            base_sha=snapshot.base_sha,
            head_sha=snapshot.head_sha,
            acceptance=inputs.acceptance,
            review_findings=tuple(
                ReviewFinding.from_json(dict(finding))
                for finding in snapshot.pending_review_findings
            ),
            ci_failures=inputs.ci_failures,
            attempts_by_root_cause=dict(inputs.attempts_by_root_cause),
            budget_remaining=dict(inputs.budget_remaining),
            validation_commands=inputs.validation_commands,
            implementer_session_id=(
                snapshot.implementer_session_id
                if snapshot.iteration_phase == PHASE_REVIEW
                else None
            ),
        )

    def complete_implementation(
        self,
        snapshot: TaskSnapshot,
        receipt: TurnReceipt,
    ) -> None:
        self._require_phase(snapshot, PHASE_IMPLEMENT)
        self._require_receipt(snapshot, receipt, "implementer")
        snapshot.implementer_session_id = receipt.session_id
        snapshot.last_turn_receipt = self._receipt_json(receipt)
        snapshot.iteration_phase = PHASE_VALIDATE

    def complete_observation(
        self,
        snapshot: TaskSnapshot,
        receipt: TurnReceipt,
    ) -> None:
        self._require_phase(snapshot, PHASE_IMPLEMENT)
        self._require_receipt(snapshot, receipt, "observer")
        snapshot.last_turn_receipt = self._receipt_json(receipt)
        snapshot.implementer_session_id = None
        snapshot.iteration_phase = PHASE_COMPLETE

    def record_terminal_turn(
        self,
        snapshot: TaskSnapshot,
        receipt: TurnReceipt,
        *,
        outcome: str,
        token_usage_observed: bool,
    ) -> None:
        self._validate_phase(snapshot)
        self._require_receipt(snapshot, receipt, receipt.role)
        snapshot.last_turn_receipt = {
            **self._receipt_json(receipt),
            "outcome": outcome,
            "token_usage_observed": str(token_usage_observed).lower(),
        }

    def complete_validation(
        self,
        snapshot: TaskSnapshot,
        receipt: GateReceipt,
    ) -> None:
        self._require_phase(snapshot, PHASE_VALIDATE)
        if receipt.issue_identifier != snapshot.issue_identifier:
            raise PhaseBridgeError("gate receipt does not match task issue")
        if snapshot.candidate_revision is None:
            raise PhaseBridgeError("gate receipt has no frozen candidate")
        if receipt.candidate_revision.to_json() != snapshot.candidate_revision:
            raise PhaseBridgeError("gate receipt does not match candidate revision")
        idempotency_key = f"gate:{receipt.gate_id}"
        if not snapshot.consume_idempotency_key(idempotency_key):
            raise PhaseBridgeError("gate receipt was already consumed")
        if receipt.verdict == "INFRASTRUCTURE_FAILURE":
            raise PhaseBridgeError("infrastructure failure must be handled by the host")
        if receipt.verdict == "PASS":
            snapshot.iteration_phase = PHASE_REVIEW
        else:
            snapshot.pending_review_findings = [
                finding.to_json() for finding in receipt.findings
            ]
            snapshot.implementer_session_id = None
            snapshot.iteration_phase = PHASE_IMPLEMENT

    def complete_review(
        self,
        snapshot: TaskSnapshot,
        receipt: TurnReceipt,
        proposal: ReviewProposal,
    ) -> ReviewDecision:
        self._require_phase(snapshot, PHASE_REVIEW)
        self._require_receipt(snapshot, receipt, "reviewer")
        if not snapshot.implementer_session_id:
            raise PhaseBridgeError("review has no trusted implementer session")
        if receipt.session_id == snapshot.implementer_session_id:
            raise PhaseBridgeError("reviewer session must differ from implementer session")
        if proposal.head_sha != snapshot.head_sha:
            raise PhaseBridgeError("review proposal does not match candidate head")
        if snapshot.candidate_revision is None:
            raise PhaseBridgeError("review has no frozen candidate")
        candidate_identity = snapshot.candidate_revision["candidate_revision"]
        if receipt.candidate_revision != candidate_identity:
            raise PhaseBridgeError("review receipt does not match candidate revision")
        if proposal.candidate_revision != candidate_identity:
            raise PhaseBridgeError("review proposal does not match candidate revision")

        decision = ReviewDecision(
            verdict=proposal.verdict,
            head_sha=proposal.head_sha,
            reviewer_role=proposal.reviewer_role,
            reviewer_session_id=receipt.session_id,
            implementer_session_id=snapshot.implementer_session_id,
            findings=proposal.findings,
            candidate_revision=candidate_identity,
        )
        snapshot.record_review_decision(decision)
        snapshot.last_turn_receipt = self._receipt_json(receipt)
        if decision.verdict == "PASS":
            snapshot.pending_review_findings = []
            snapshot.iteration_phase = PHASE_COMPLETE
        else:
            snapshot.pending_review_findings = [
                finding.to_json() for finding in decision.findings
            ]
            snapshot.implementer_session_id = None
            snapshot.review_workspace = None
            snapshot.iteration_phase = PHASE_IMPLEMENT
        return decision

    @staticmethod
    def invalidate_for_new_head(snapshot: TaskSnapshot, head_sha: str) -> None:
        if not FULL_SHA.fullmatch(head_sha):
            raise ValueError("head_sha must be a lowercase full Git SHA")
        if snapshot.head_sha == head_sha:
            return
        SymphonyPhaseBridge._require_refreshable_state(snapshot)
        snapshot.head_sha = head_sha
        SymphonyPhaseBridge._invalidate_current_candidate(snapshot)
        SymphonyPhaseBridge._return_to_gate_loop(snapshot)

    @staticmethod
    def apply_base_sync(snapshot: TaskSnapshot, result: BaseSyncResult) -> None:
        SymphonyPhaseBridge._require_refreshable_state(snapshot)
        if (
            snapshot.base_sha != result.previous_base_sha
            or snapshot.head_sha != result.previous_head_sha
        ):
            raise PhaseBridgeError("base synchronization differs from task identity")
        snapshot.base_sync = result.to_json()
        if result.status == "UNCHANGED":
            return
        if result.status == "UPDATED":
            snapshot.base_sha = result.base_sha
            snapshot.head_sha = result.head_sha
        SymphonyPhaseBridge._invalidate_current_candidate(snapshot)
        SymphonyPhaseBridge._return_to_gate_loop(snapshot)

    @staticmethod
    def _return_to_gate_loop(snapshot: TaskSnapshot) -> None:
        snapshot.state = "queued"
        snapshot.claim_id = None
        snapshot.blocked_reason = None

    @staticmethod
    def _require_refreshable_state(snapshot: TaskSnapshot) -> None:
        if snapshot.state not in {"queued", "waiting_ci", "human_review"}:
            raise PhaseBridgeError(
                f"task state {snapshot.state} cannot refresh candidate identity"
            )

    @staticmethod
    def _invalidate_current_candidate(snapshot: TaskSnapshot) -> None:
        snapshot.implementer_session_id = None
        snapshot.candidate_revision = None
        snapshot.candidate_commit_sha = None
        snapshot.gate_request = None
        snapshot.gate_receipt = None
        snapshot.review_workspace = None
        snapshot.pending_review_findings = []
        snapshot.last_turn_receipt = None
        snapshot.handoff_head_sha = None
        snapshot.github_review_packet = None
        snapshot.iteration_phase = PHASE_IMPLEMENT

    @staticmethod
    def _validate_phase(snapshot: TaskSnapshot) -> None:
        if snapshot.iteration_phase not in PHASES:
            raise PhaseBridgeError(
                f"unknown iteration phase: {snapshot.iteration_phase}"
            )

    def _require_phase(self, snapshot: TaskSnapshot, expected: str) -> None:
        self._validate_phase(snapshot)
        if snapshot.iteration_phase != expected:
            raise PhaseBridgeError(
                f"expected {expected} phase, got {snapshot.iteration_phase}"
            )

    @staticmethod
    def _require_receipt(
        snapshot: TaskSnapshot, receipt: TurnReceipt, expected_role: str
    ) -> None:
        if receipt.role != expected_role:
            raise PhaseBridgeError(
                f"expected {expected_role} receipt, got {receipt.role}"
            )
        if receipt.head_sha != snapshot.head_sha:
            raise PhaseBridgeError("turn receipt does not match candidate head")
        if expected_role == "reviewer":
            if snapshot.candidate_revision is None:
                raise PhaseBridgeError("review receipt has no frozen candidate")
            if receipt.candidate_revision != snapshot.candidate_revision["candidate_revision"]:
                raise PhaseBridgeError("review receipt does not match candidate revision")

    @staticmethod
    def _receipt_json(receipt: TurnReceipt) -> dict[str, str]:
        payload = {
            "session_id": receipt.session_id,
            "thread_id": receipt.thread_id,
            "turn_id": receipt.turn_id,
            "role": receipt.role,
            "head_sha": receipt.head_sha,
        }
        if receipt.candidate_revision is not None:
            payload["candidate_revision"] = receipt.candidate_revision
        return payload
