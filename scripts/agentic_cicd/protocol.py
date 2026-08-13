from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ISSUE_IDENTIFIER = re.compile(r"GH-[1-9][0-9]*\Z")
FULL_SHA = re.compile(r"[0-9a-f]{40}\Z")
ROOT_CAUSE_ID = re.compile(r"[a-z0-9][a-z0-9:._/-]{2,127}\Z")
SEVERITIES = {"low", "medium", "high", "critical"}
REVIEWER_ROLES = {"product-steward", "spec-evaluator", "security-supply-chain"}


def _nonblank(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must not be blank")
    return normalized


def _full_sha(value: str, field_name: str) -> str:
    if not FULL_SHA.fullmatch(value):
        raise ValueError(f"{field_name} must be a lowercase full Git SHA")
    return value


@dataclass(frozen=True)
class ReviewFinding:
    root_cause_id: str
    severity: str
    evidence: str
    impact: str
    expected_behavior: str
    verification: str

    def __post_init__(self) -> None:
        if not ROOT_CAUSE_ID.fullmatch(self.root_cause_id):
            raise ValueError("root_cause_id must be a stable machine identifier")
        if self.severity not in SEVERITIES:
            raise ValueError(f"severity must be one of {sorted(SEVERITIES)}")
        for field_name in (
            "evidence",
            "impact",
            "expected_behavior",
            "verification",
        ):
            _nonblank(getattr(self, field_name), field_name)

    def to_json(self) -> dict[str, str]:
        return {
            "root_cause_id": self.root_cause_id,
            "severity": self.severity,
            "evidence": self.evidence,
            "impact": self.impact,
            "expected_behavior": self.expected_behavior,
            "verification": self.verification,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "ReviewFinding":
        required = {
            "root_cause_id",
            "severity",
            "evidence",
            "impact",
            "expected_behavior",
            "verification",
        }
        if set(payload) != required:
            raise ValueError("review finding fields do not match the contract")
        return cls(**{key: str(payload[key]) for key in required})


@dataclass(frozen=True)
class IterationPacket:
    issue_identifier: str
    objective: str
    base_sha: str
    head_sha: str
    acceptance: tuple[str, ...]
    review_findings: tuple[ReviewFinding, ...]
    ci_failures: tuple[dict[str, Any], ...]
    attempts_by_root_cause: dict[str, int]
    budget_remaining: dict[str, int]
    validation_commands: tuple[str, ...]
    implementer_session_id: str

    def __post_init__(self) -> None:
        if not ISSUE_IDENTIFIER.fullmatch(self.issue_identifier):
            raise ValueError("issue identifier must match GH-<positive-number>")
        _nonblank(self.objective, "objective")
        _full_sha(self.base_sha, "base_sha")
        _full_sha(self.head_sha, "head_sha")
        _nonblank(self.implementer_session_id, "implementer_session_id")
        if not self.acceptance:
            raise ValueError("acceptance must contain at least one criterion")
        for criterion in self.acceptance:
            _nonblank(criterion, "acceptance criterion")
        if not self.validation_commands:
            raise ValueError("validation_commands must not be empty")
        for command in self.validation_commands:
            if "\n" in command or "\r" in command:
                raise ValueError("validation command must be one line")
            _nonblank(command, "validation command")
        for root_cause, attempts in self.attempts_by_root_cause.items():
            if not ROOT_CAUSE_ID.fullmatch(root_cause):
                raise ValueError("attempt root cause must be a stable machine identifier")
            if isinstance(attempts, bool) or not isinstance(attempts, int) or attempts < 0:
                raise ValueError("attempt count must be a non-negative integer")
        for name, remaining in self.budget_remaining.items():
            _nonblank(name, "budget name")
            if isinstance(remaining, bool) or not isinstance(remaining, int) or remaining < 0:
                raise ValueError("remaining budget must be a non-negative integer")
        if not all(isinstance(failure, dict) for failure in self.ci_failures):
            raise ValueError("ci_failures entries must be objects")

    def to_json(self) -> dict[str, Any]:
        return {
            "issue_identifier": self.issue_identifier,
            "objective": self.objective,
            "base_sha": self.base_sha,
            "head_sha": self.head_sha,
            "acceptance": list(self.acceptance),
            "review_findings": [finding.to_json() for finding in self.review_findings],
            "ci_failures": [dict(failure) for failure in self.ci_failures],
            "attempts_by_root_cause": dict(self.attempts_by_root_cause),
            "budget_remaining": dict(self.budget_remaining),
            "validation_commands": list(self.validation_commands),
            "implementer_session_id": self.implementer_session_id,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "IterationPacket":
        required = {
            "issue_identifier",
            "objective",
            "base_sha",
            "head_sha",
            "acceptance",
            "review_findings",
            "ci_failures",
            "attempts_by_root_cause",
            "budget_remaining",
            "validation_commands",
            "implementer_session_id",
        }
        if set(payload) != required:
            raise ValueError("iteration packet fields do not match the contract")
        for field_name in (
            "issue_identifier",
            "objective",
            "base_sha",
            "head_sha",
            "implementer_session_id",
        ):
            if not isinstance(payload[field_name], str):
                raise ValueError(f"{field_name} must be a string")
        for field_name in (
            "acceptance",
            "review_findings",
            "ci_failures",
            "validation_commands",
        ):
            if not isinstance(payload[field_name], list):
                raise ValueError(f"{field_name} must be an array")
        if not all(isinstance(value, str) for value in payload["acceptance"]):
            raise ValueError("acceptance entries must be strings")
        if not all(isinstance(value, dict) for value in payload["review_findings"]):
            raise ValueError("review_findings entries must be objects")
        if not all(isinstance(value, dict) for value in payload["ci_failures"]):
            raise ValueError("ci_failures entries must be objects")
        if not all(isinstance(value, str) for value in payload["validation_commands"]):
            raise ValueError("validation_commands entries must be strings")
        if not isinstance(payload["attempts_by_root_cause"], dict):
            raise ValueError("attempts_by_root_cause must be an object")
        if not isinstance(payload["budget_remaining"], dict):
            raise ValueError("budget_remaining must be an object")
        return cls(
            issue_identifier=str(payload["issue_identifier"]),
            objective=str(payload["objective"]),
            base_sha=str(payload["base_sha"]),
            head_sha=str(payload["head_sha"]),
            acceptance=tuple(str(value) for value in payload["acceptance"]),
            review_findings=tuple(
                ReviewFinding.from_json(dict(value))
                for value in payload["review_findings"]
            ),
            ci_failures=tuple(dict(value) for value in payload["ci_failures"]),
            attempts_by_root_cause={
                str(key): value
                for key, value in payload["attempts_by_root_cause"].items()
            },
            budget_remaining={
                str(key): value for key, value in payload["budget_remaining"].items()
            },
            validation_commands=tuple(
                str(value) for value in payload["validation_commands"]
            ),
            implementer_session_id=str(payload["implementer_session_id"]),
        )


@dataclass(frozen=True)
class ReviewDecision:
    verdict: str
    head_sha: str
    reviewer_role: str
    reviewer_session_id: str
    implementer_session_id: str
    findings: tuple[ReviewFinding, ...]

    def __post_init__(self) -> None:
        if self.verdict not in {"PASS", "FAIL"}:
            raise ValueError("verdict must be PASS or FAIL")
        _full_sha(self.head_sha, "head_sha")
        if self.reviewer_role not in REVIEWER_ROLES:
            raise ValueError(f"reviewer_role must be one of {sorted(REVIEWER_ROLES)}")
        reviewer = _nonblank(self.reviewer_session_id, "reviewer_session_id")
        implementer = _nonblank(self.implementer_session_id, "implementer_session_id")
        if reviewer == implementer:
            raise ValueError("reviewer session must be independent from implementer session")
        if self.verdict == "PASS" and self.findings:
            raise ValueError("PASS cannot contain findings")
        if self.verdict == "FAIL" and not self.findings:
            raise ValueError("FAIL must contain at least one finding")

    def to_json(self) -> dict[str, Any]:
        return {
            "verdict": self.verdict,
            "head_sha": self.head_sha,
            "reviewer_role": self.reviewer_role,
            "reviewer_session_id": self.reviewer_session_id,
            "implementer_session_id": self.implementer_session_id,
            "findings": [finding.to_json() for finding in self.findings],
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "ReviewDecision":
        required = {
            "verdict",
            "head_sha",
            "reviewer_role",
            "reviewer_session_id",
            "implementer_session_id",
            "findings",
        }
        if set(payload) != required:
            raise ValueError("review decision fields do not match the contract")
        for field_name in (
            "verdict",
            "head_sha",
            "reviewer_role",
            "reviewer_session_id",
            "implementer_session_id",
        ):
            if not isinstance(payload[field_name], str):
                raise ValueError(f"{field_name} must be a string")
        if not isinstance(payload["findings"], list) or not all(
            isinstance(value, dict) for value in payload["findings"]
        ):
            raise ValueError("findings must be an array of objects")
        return cls(
            verdict=str(payload["verdict"]),
            head_sha=str(payload["head_sha"]),
            reviewer_role=str(payload["reviewer_role"]),
            reviewer_session_id=str(payload["reviewer_session_id"]),
            implementer_session_id=str(payload["implementer_session_id"]),
            findings=tuple(
                ReviewFinding.from_json(dict(value)) for value in payload["findings"]
            ),
        )


class ReviewLedger:
    """Stores immutable review decisions keyed by the exact candidate SHA."""

    def __init__(self) -> None:
        self._decisions: dict[str, ReviewDecision] = {}

    def record(self, decision: ReviewDecision) -> None:
        self._decisions[decision.head_sha] = decision

    def has_pass_for(self, head_sha: str) -> bool:
        _full_sha(head_sha, "head_sha")
        decision = self._decisions.get(head_sha)
        return decision is not None and decision.verdict == "PASS"


def parse_review_decision(
    payload: dict[str, Any],
    *,
    expected_head_sha: str,
    reviewer_session_id: str,
    implementer_session_id: str,
) -> ReviewDecision:
    """Validate model output against host-owned review identity and candidate state."""

    decision = ReviewDecision.from_json(payload)
    expected_head = _full_sha(expected_head_sha, "expected_head_sha")
    expected_reviewer = _nonblank(reviewer_session_id, "reviewer_session_id")
    expected_implementer = _nonblank(
        implementer_session_id, "implementer_session_id"
    )
    if decision.head_sha != expected_head:
        raise ValueError("review decision head_sha differs from the host candidate")
    if decision.reviewer_session_id != expected_reviewer:
        raise ValueError("reviewer_session_id differs from the host review session")
    if decision.implementer_session_id != expected_implementer:
        raise ValueError("implementer_session_id differs from the host implementation session")
    return decision


SCHEMA_ROOT = Path(__file__).resolve().parents[2] / "config" / "agentic-cicd"


def _load_schema(name: str) -> dict[str, Any]:
    payload = json.loads((SCHEMA_ROOT / name).read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"{name} must contain a JSON object")
    return payload


ITERATION_PACKET_SCHEMA = _load_schema("iteration-packet.schema.json")
REVIEW_DECISION_SCHEMA = _load_schema("review-decision.schema.json")
