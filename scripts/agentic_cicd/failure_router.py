from __future__ import annotations

from dataclasses import dataclass

from .coordinator import Coordinator, TaskSnapshot
from .protocol import FULL_SHA, ROOT_CAUSE_ID, SHA256


SOURCE_KINDS = {"ci", "review"}
CONCLUSIONS = {"SUCCESS", "FAILURE", "CONFLICT", "PENDING"}
BASELINE_CONCLUSIONS = {
    "SUCCESS",
    "FAILURE",
    "CONFLICT",
    "UNKNOWN",
    "NOT_APPLICABLE",
}
INFRASTRUCTURE_CATEGORIES = {"runner", "network", "service", "third_party"}
HUMAN_DECISION_CATEGORIES = {"requirement", "permission"}


@dataclass(frozen=True)
class FailureEvidence:
    event_id: str
    root_cause_id: str
    source_kind: str
    base_sha: str
    head_sha: str
    current_conclusion: str
    baseline_conclusion: str
    same_head_conclusions: tuple[str, ...]
    infrastructure_category: str | None = None
    human_decision_category: str | None = None

    def __post_init__(self) -> None:
        if (
            not isinstance(self.event_id, str)
            or not self.event_id.strip()
            or len(self.event_id) > 256
            or "\n" in self.event_id
            or "\r" in self.event_id
        ):
            raise ValueError("event_id must be a stable one-line identifier")
        if not ROOT_CAUSE_ID.fullmatch(self.root_cause_id):
            raise ValueError("root_cause_id must be a stable machine identifier")
        if self.source_kind not in SOURCE_KINDS:
            raise ValueError("source_kind must be ci or review")
        if not FULL_SHA.fullmatch(self.base_sha) or not FULL_SHA.fullmatch(
            self.head_sha
        ):
            raise ValueError("failure evidence must bind full lowercase Git SHAs")
        if self.current_conclusion not in CONCLUSIONS:
            raise ValueError("current conclusion is invalid")
        if self.baseline_conclusion not in BASELINE_CONCLUSIONS:
            raise ValueError("baseline conclusion is invalid")
        if not self.same_head_conclusions or any(
            conclusion not in CONCLUSIONS
            for conclusion in self.same_head_conclusions
        ):
            raise ValueError("same-head conclusions are invalid")
        if self.current_conclusion not in self.same_head_conclusions:
            raise ValueError("same-head conclusions must include the current result")
        if (
            self.infrastructure_category is not None
            and self.infrastructure_category not in INFRASTRUCTURE_CATEGORIES
        ):
            raise ValueError("infrastructure category is invalid")
        if (
            self.human_decision_category is not None
            and self.human_decision_category not in HUMAN_DECISION_CATEGORIES
        ):
            raise ValueError("human decision category is invalid")
        if self.source_kind == "review":
            if self.baseline_conclusion != "NOT_APPLICABLE":
                raise ValueError("review evidence has no baseline conclusion")
        elif self.baseline_conclusion == "NOT_APPLICABLE":
            raise ValueError("CI evidence requires a baseline conclusion")


@dataclass(frozen=True)
class FailureRoute:
    category: str
    action: str
    root_cause_id: str
    reason: str

    def __post_init__(self) -> None:
        if self.category not in {
            "candidate",
            "baseline",
            "infrastructure",
            "flaky",
            "requirement_permission",
        }:
            raise ValueError("failure route category is invalid")
        if self.action not in {
            "return_to_implementation",
            "retry_infrastructure",
            "await_authorized_rerun",
            "blocked_no_progress",
            "blocked",
            "fused",
        }:
            raise ValueError("failure route action is invalid")
        if not ROOT_CAUSE_ID.fullmatch(self.root_cause_id):
            raise ValueError("root_cause_id must be a stable machine identifier")
        if not isinstance(self.reason, str) or not self.reason.strip():
            raise ValueError("failure route reason must not be blank")

    def to_json(self) -> dict[str, str]:
        return {
            "category": self.category,
            "action": self.action,
            "root_cause_id": self.root_cause_id,
            "reason": self.reason,
        }

    @classmethod
    def from_json(cls, payload: dict[str, str]) -> "FailureRoute":
        required = {"category", "action", "root_cause_id", "reason"}
        if set(payload) != required or not all(
            isinstance(payload[name], str) for name in required
        ):
            raise ValueError("failure route fields do not match the contract")
        return cls(**payload)


class FailureRouter:
    """Routes trusted CI/review facts without inspecting untrusted prose."""

    def __init__(self, coordinator: Coordinator):
        self.coordinator = coordinator

    def route(
        self, snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> FailureRoute:
        self._require_revision_identity(snapshot, evidence)
        event_key = (
            f"failure:{evidence.base_sha}:{evidence.head_sha}:"
            f"{evidence.source_kind}:"
            f"{evidence.event_id}"
        )
        existing = snapshot.failure_routes.get(event_key)
        if existing is not None:
            return FailureRoute.from_json(existing)

        explicit_category = (
            evidence.infrastructure_category is not None
            or evidence.human_decision_category is not None
        )
        if snapshot.state != "waiting_ci" and not (
            snapshot.state == "queued" and explicit_category
        ):
            raise RuntimeError("failure routing requires waiting_ci state")
        if evidence.current_conclusion not in {"FAILURE", "CONFLICT"}:
            raise ValueError("current evidence is not a failure terminal")
        if (
            evidence.infrastructure_category is not None
            and evidence.human_decision_category is not None
        ):
            raise ValueError("failure evidence categories are contradictory")
        if evidence.human_decision_category is not None:
            route = self._human_decision(snapshot, evidence)
        elif evidence.infrastructure_category is not None:
            route = self._infrastructure(snapshot, evidence)
        elif evidence.source_kind == "ci" and evidence.baseline_conclusion == "UNKNOWN":
            raise ValueError("CI baseline evidence is incomplete")
        elif evidence.baseline_conclusion in {"FAILURE", "CONFLICT"}:
            route = self._baseline(snapshot, evidence)
        elif self._is_flaky(evidence):
            route = self._flaky(snapshot, evidence)
        else:
            route = self._candidate(snapshot, evidence)

        snapshot.failure_routes[event_key] = route.to_json()
        return route

    @staticmethod
    def _require_revision_identity(
        snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> None:
        if snapshot.base_sha != evidence.base_sha or snapshot.head_sha != evidence.head_sha:
            raise RuntimeError("failure evidence differs from task identity")

    @staticmethod
    def _is_flaky(evidence: FailureEvidence) -> bool:
        conclusions = set(evidence.same_head_conclusions)
        return (
            evidence.source_kind == "ci"
            and evidence.current_conclusion == "FAILURE"
            and {"SUCCESS", "FAILURE"}.issubset(conclusions)
        )

    def _candidate(
        self, snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> FailureRoute:
        candidate = snapshot.candidate_revision
        revision = candidate.get("candidate_revision") if candidate is not None else None
        if not isinstance(revision, str) or not SHA256.fullmatch(revision):
            raise RuntimeError("candidate failure has no trusted candidate revision")
        outcome = self.coordinator.record_failed_semantic_fix(
            snapshot, evidence.root_cause_id, revision
        )
        if outcome == "retry":
            self.coordinator.transition(snapshot, "queued")
            snapshot.iteration_phase = "implement"
            snapshot.implementer_session_id = None
            snapshot.claim_id = None
            snapshot.blocked_reason = None
            snapshot.candidate_revision = None
            snapshot.candidate_commit_sha = None
            snapshot.gate_request = None
            snapshot.gate_receipt = None
            snapshot.review_workspace = None
            snapshot.pending_review_findings = []
            snapshot.last_turn_receipt = None
            snapshot.pull_request_packet = None
            snapshot.handoff_head_sha = None
            action = "return_to_implementation"
        elif outcome == "duplicate":
            self.coordinator.transition(snapshot, "blocked")
            snapshot.blocked_reason = f"no-new-strategy:{evidence.root_cause_id}"
            snapshot.claim_id = None
            action = "blocked_no_progress"
        else:
            action = "fused"
        return FailureRoute(
            category="candidate",
            action=action,
            root_cause_id=evidence.root_cause_id,
            reason=f"candidate-failure:{evidence.root_cause_id}",
        )

    def _infrastructure(
        self, snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> FailureRoute:
        outcome = self.coordinator.record_infrastructure_failure(snapshot)
        if outcome == "retry":
            snapshot.blocked_reason = None
            action = "retry_infrastructure"
        else:
            action = "blocked"
        return FailureRoute(
            category="infrastructure",
            action=action,
            root_cause_id=evidence.root_cause_id,
            reason=(
                f"infrastructure:{evidence.infrastructure_category}:"
                f"{evidence.root_cause_id}"
            ),
        )

    def _flaky(
        self, snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> FailureRoute:
        outcome = self.coordinator.record_flaky_rerun(
            snapshot, evidence.root_cause_id
        )
        if outcome == "retry":
            snapshot.blocked_reason = None
            action = "await_authorized_rerun"
        else:
            action = "blocked"
        return FailureRoute(
            category="flaky",
            action=action,
            root_cause_id=evidence.root_cause_id,
            reason=f"flaky:{evidence.root_cause_id}",
        )

    def _baseline(
        self, snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> FailureRoute:
        self._block(snapshot, f"baseline-failure:{evidence.root_cause_id}")
        return FailureRoute(
            category="baseline",
            action="blocked",
            root_cause_id=evidence.root_cause_id,
            reason=f"baseline-failure:{evidence.root_cause_id}",
        )

    def _human_decision(
        self, snapshot: TaskSnapshot, evidence: FailureEvidence
    ) -> FailureRoute:
        reason = (
            f"human-decision:{evidence.human_decision_category}:"
            f"{evidence.root_cause_id}"
        )
        self._block(snapshot, reason)
        return FailureRoute(
            category="requirement_permission",
            action="blocked",
            root_cause_id=evidence.root_cause_id,
            reason=reason,
        )

    def _block(self, snapshot: TaskSnapshot, reason: str) -> None:
        self.coordinator.transition(snapshot, "blocked")
        snapshot.blocked_reason = reason
        snapshot.claim_id = None
