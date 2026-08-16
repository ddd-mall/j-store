from __future__ import annotations

import json
import os
import tempfile
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

from .protocol import ReviewDecision


class ClaimConflict(RuntimeError):
    """Raised when another run already owns a task claim."""


class InvalidTransition(RuntimeError):
    """Raised when a state transition is outside the repository contract."""


class BudgetExceeded(RuntimeError):
    """Raised after a task is blocked by a deterministic budget limit."""


@dataclass
class BudgetUsage:
    turns: int = 0
    wall_clock_seconds: int = 0
    cost_microusd: int = 0


@dataclass
class TaskSnapshot:
    issue_identifier: str
    state: str
    claim_id: str | None = None
    base_sha: str | None = None
    head_sha: str | None = None
    branch: str | None = None
    workspace: str | None = None
    pull_request_number: int | None = None
    semantic_fix_strategies: dict[str, list[str]] = field(default_factory=dict)
    infrastructure_retries: int = 0
    budget: BudgetUsage = field(default_factory=BudgetUsage)
    consumed_idempotency_keys: set[str] = field(default_factory=set)
    review_decisions: dict[str, dict[str, Any]] = field(default_factory=dict)
    blocked_reason: str | None = None
    iteration_phase: str = "implement"
    implementer_session_id: str | None = None
    pending_review_findings: list[dict[str, Any]] = field(default_factory=list)
    last_turn_receipt: dict[str, str] | None = None
    candidate_revision: dict[str, str] | None = None
    review_workspace: str | None = None
    gate_request: dict[str, Any] | None = None
    gate_receipt: dict[str, Any] | None = None

    def consume_idempotency_key(self, key: str) -> bool:
        normalized = key.strip()
        if not normalized:
            raise ValueError("idempotency key must not be blank")
        if normalized in self.consumed_idempotency_keys:
            return False
        self.consumed_idempotency_keys.add(normalized)
        return True

    def to_json(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["consumed_idempotency_keys"] = sorted(self.consumed_idempotency_keys)
        return payload

    def record_review_decision(self, decision: ReviewDecision) -> None:
        self.review_decisions[decision.candidate_revision] = decision.to_json()

    def review_decision_for(self, candidate_revision: str) -> ReviewDecision | None:
        payload = self.review_decisions.get(candidate_revision)
        return ReviewDecision.from_json(payload) if payload is not None else None

    def has_review_pass_for(self, candidate_revision: str) -> bool:
        decision = self.review_decision_for(candidate_revision)
        return decision is not None and decision.verdict == "PASS"

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "TaskSnapshot":
        data = dict(payload)
        data["budget"] = BudgetUsage(**data.get("budget", {}))
        data["consumed_idempotency_keys"] = set(
            data.get("consumed_idempotency_keys", [])
        )
        data["semantic_fix_strategies"] = {
            str(root_cause): [str(strategy) for strategy in strategies]
            for root_cause, strategies in data.get(
                "semantic_fix_strategies", {}
            ).items()
        }
        data["review_decisions"] = {
            str(head_sha): dict(decision)
            for head_sha, decision in data.get("review_decisions", {}).items()
        }
        data["pending_review_findings"] = [
            dict(finding) for finding in data.get("pending_review_findings", [])
        ]
        if data.get("last_turn_receipt") is not None:
            data["last_turn_receipt"] = {
                str(key): str(value)
                for key, value in data["last_turn_receipt"].items()
            }
        if data.get("candidate_revision") is not None:
            data["candidate_revision"] = {
                str(key): str(value)
                for key, value in data["candidate_revision"].items()
            }
        for field_name in ("gate_request", "gate_receipt"):
            if data.get(field_name) is not None:
                data[field_name] = dict(data[field_name])
        return cls(**data)


class SnapshotStore:
    """Persists one task snapshot with an atomic same-directory replace."""

    def __init__(self, path: Path):
        self.path = path.resolve()

    def save(self, snapshot: TaskSnapshot) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{self.path.name}.",
            suffix=".tmp",
            dir=self.path.parent,
        )
        temporary_path = Path(temporary_name)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
                json.dump(snapshot.to_json(), stream, indent=2, sort_keys=True)
                stream.write("\n")
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_path, self.path)
        finally:
            temporary_path.unlink(missing_ok=True)

    def load(self) -> TaskSnapshot:
        payload = json.loads(self.path.read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise ValueError("task snapshot must be a JSON object")
        return TaskSnapshot.from_json(payload)


class KillSwitch:
    """A host-owned sentinel that prevents new task claims."""

    def __init__(self, path: Path):
        self.path = path.resolve()

    def activate(self, reason: str) -> None:
        normalized = reason.strip()
        if not normalized:
            raise ValueError("kill-switch reason must not be blank")
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(normalized + "\n", encoding="utf-8")

    def deactivate(self) -> None:
        self.path.unlink(missing_ok=True)

    def reason(self) -> str | None:
        if not self.path.is_file():
            return None
        reason = self.path.read_text(encoding="utf-8").strip()
        return reason or "kill switch is active"


@dataclass(frozen=True)
class CoordinatorLimits:
    semantic_fixes_per_root_cause: int
    infrastructure_retries: int
    max_turns_per_task: int
    max_wall_clock_seconds: int
    max_cost_microusd: int


class Coordinator:
    def __init__(
        self,
        transitions: dict[str, set[str]],
        claimable_states: set[str],
        limits: CoordinatorLimits,
    ):
        self.transitions = transitions
        self.claimable_states = claimable_states
        self.limits = limits

    @classmethod
    def from_contract(cls, contract: dict[str, Any]) -> "Coordinator":
        raw_limits = contract["limits"]
        limits = CoordinatorLimits(
            semantic_fixes_per_root_cause=int(
                raw_limits["semantic_fixes_per_root_cause"]
            ),
            infrastructure_retries=int(raw_limits["infrastructure_retries"]),
            max_turns_per_task=int(raw_limits["max_turns_per_task"]),
            max_wall_clock_seconds=int(raw_limits["max_wall_clock_seconds"]),
            max_cost_microusd=int(raw_limits["max_cost_microusd"]),
        )
        transitions = {
            str(source): {str(target) for target in targets}
            for source, targets in contract["transitions"].items()
        }
        claimable_states = {str(state) for state in contract["claimable_states"]}
        return cls(transitions, claimable_states, limits)

    def claim(
        self,
        snapshot: TaskSnapshot,
        claim_id: str,
        *,
        kill_switch: KillSwitch | None = None,
    ) -> None:
        if kill_switch is not None and (reason := kill_switch.reason()) is not None:
            raise RuntimeError(f"kill switch is active: {reason}")
        if snapshot.state not in self.claimable_states:
            raise InvalidTransition(f"state {snapshot.state} is not claimable")
        normalized = claim_id.strip()
        if not normalized:
            raise ValueError("claim id must not be blank")
        if snapshot.claim_id is None:
            snapshot.claim_id = normalized
        elif snapshot.claim_id != normalized:
            raise ClaimConflict(
                f"task {snapshot.issue_identifier} is claimed by {snapshot.claim_id}"
            )

    def transition(self, snapshot: TaskSnapshot, target: str) -> None:
        if target == snapshot.state:
            return
        allowed = self.transitions.get(snapshot.state, set())
        if target not in allowed:
            raise InvalidTransition(f"{snapshot.state} -> {target} is not allowed")
        snapshot.state = target

    def record_failed_semantic_fix(
        self,
        snapshot: TaskSnapshot,
        root_cause_id: str,
        strategy_fingerprint: str,
    ) -> str:
        root_cause = root_cause_id.strip()
        strategy = strategy_fingerprint.strip()
        if not root_cause or not strategy:
            raise ValueError("root cause and strategy fingerprint must not be blank")
        strategies = snapshot.semantic_fix_strategies.setdefault(root_cause, [])
        if strategy in strategies:
            return "duplicate"
        if len(strategies) >= self.limits.semantic_fixes_per_root_cause:
            self.transition(snapshot, "fused")
            snapshot.blocked_reason = f"semantic-fix-limit:{root_cause}"
            snapshot.claim_id = None
            return "fused"
        strategies.append(strategy)
        return "retry"

    def record_infrastructure_failure(self, snapshot: TaskSnapshot) -> str:
        if snapshot.infrastructure_retries >= self.limits.infrastructure_retries:
            self.transition(snapshot, "blocked")
            snapshot.blocked_reason = "infrastructure-retry-limit"
            snapshot.claim_id = None
            return "blocked"
        snapshot.infrastructure_retries += 1
        return "retry"

    def consume_budget(
        self,
        snapshot: TaskSnapshot,
        *,
        turns: int = 0,
        wall_clock_seconds: int = 0,
        cost_microusd: int = 0,
    ) -> None:
        increments = (turns, wall_clock_seconds, cost_microusd)
        if any(value < 0 for value in increments):
            raise ValueError("budget increments must be non-negative")
        snapshot.budget.turns += turns
        snapshot.budget.wall_clock_seconds += wall_clock_seconds
        snapshot.budget.cost_microusd += cost_microusd

        exceeded: str | None = None
        if snapshot.budget.turns > self.limits.max_turns_per_task:
            exceeded = "turns"
        elif snapshot.budget.wall_clock_seconds > self.limits.max_wall_clock_seconds:
            exceeded = "wall-clock"
        elif snapshot.budget.cost_microusd > self.limits.max_cost_microusd:
            exceeded = "cost"

        if exceeded is not None:
            self.transition(snapshot, "blocked")
            snapshot.blocked_reason = f"budget:{exceeded}"
            snapshot.claim_id = None
            raise BudgetExceeded(snapshot.blocked_reason)

    def cancel(self, snapshot: TaskSnapshot, reason: str) -> None:
        normalized = reason.strip()
        if not normalized:
            raise ValueError("cancellation reason must not be blank")
        self.transition(snapshot, "cancelled")
        snapshot.claim_id = None
        snapshot.blocked_reason = normalized
