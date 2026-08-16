from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .candidate import CandidateRevision


ISSUE_IDENTIFIER = re.compile(r"GH-[1-9][0-9]*\Z")
FULL_SHA = re.compile(r"[0-9a-f]{40}\Z")
ROOT_CAUSE_ID = re.compile(r"[a-z0-9][a-z0-9:._/-]{2,127}\Z")
SEVERITIES = {"low", "medium", "high", "critical"}
REVIEWER_ROLES = {"product-steward", "spec-evaluator", "security-supply-chain"}
TURN_ROLES = {"observer", "implementer", "reviewer"}
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
GATE_ID = re.compile(r"gate-[a-z0-9][a-z0-9-]{0,63}\Z")
PINNED_IMAGE = re.compile(r"[^\s@]+@sha256:[0-9a-f]{64}\Z")


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


def _sha256_digest(value: str, field_name: str) -> str:
    if not SHA256.fullmatch(value):
        raise ValueError(f"{field_name} must be a lowercase SHA-256")
    return value


def _utc_timestamp(value: str, field_name: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{field_name} must be an RFC3339 timestamp") from error
    if parsed.tzinfo is None or parsed.utcoffset() != timezone.utc.utcoffset(parsed):
        raise ValueError(f"{field_name} must be a UTC RFC3339 timestamp")
    return parsed


@dataclass(frozen=True)
class GateRequest:
    """Host-owned request for one immutable candidate gate execution."""

    gate_id: str
    issue_identifier: str
    candidate_revision: CandidateRevision
    runner_image: str
    command_policy_sha256: str
    validation_commands: tuple[str, ...]
    timeout_seconds: int
    requested_at: str

    @staticmethod
    def calculate_command_policy_sha256(commands: tuple[str, ...]) -> str:
        encoded = json.dumps(
            {"validation_commands": list(commands)},
            separators=(",", ":"),
            sort_keys=True,
        ).encode()
        return hashlib.sha256(encoded).hexdigest()

    def __post_init__(self) -> None:
        if not GATE_ID.fullmatch(self.gate_id):
            raise ValueError("gate_id must be a stable gate-* identifier")
        if not ISSUE_IDENTIFIER.fullmatch(self.issue_identifier):
            raise ValueError("issue identifier must match GH-<positive-number>")
        if not PINNED_IMAGE.fullmatch(self.runner_image):
            raise ValueError("runner_image must use an immutable sha256 digest")
        _sha256_digest(self.command_policy_sha256, "command_policy_sha256")
        if not self.validation_commands:
            raise ValueError("validation_commands must not be empty")
        for command in self.validation_commands:
            if not isinstance(command, str) or "\n" in command or "\r" in command:
                raise ValueError("validation command must be one line")
            _nonblank(command, "validation command")
        if self.command_policy_sha256 != self.calculate_command_policy_sha256(
            self.validation_commands
        ):
            raise ValueError("command_policy_sha256 does not bind validation_commands")
        if isinstance(self.timeout_seconds, bool) or not isinstance(self.timeout_seconds, int) or self.timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be a positive integer")
        _utc_timestamp(self.requested_at, "requested_at")

    def to_json(self) -> dict[str, Any]:
        return {
            "gate_id": self.gate_id,
            "issue_identifier": self.issue_identifier,
            "candidate_revision": self.candidate_revision.to_json(),
            "runner_image": self.runner_image,
            "command_policy_sha256": self.command_policy_sha256,
            "validation_commands": list(self.validation_commands),
            "timeout_seconds": self.timeout_seconds,
            "requested_at": self.requested_at,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "GateRequest":
        required = {"gate_id", "issue_identifier", "candidate_revision", "runner_image", "command_policy_sha256", "validation_commands", "timeout_seconds", "requested_at"}
        if set(payload) != required:
            raise ValueError("gate request fields do not match the contract")
        string_fields = required - {
            "candidate_revision",
            "validation_commands",
            "timeout_seconds",
        }
        if not all(isinstance(payload[name], str) for name in string_fields):
            raise ValueError("gate request scalar identity fields must be strings")
        if not isinstance(payload["candidate_revision"], dict) or not isinstance(payload["validation_commands"], list):
            raise ValueError("gate request candidate and commands must be structured")
        return cls(
            gate_id=payload["gate_id"], issue_identifier=payload["issue_identifier"],
            candidate_revision=CandidateRevision.from_json(dict(payload["candidate_revision"])),
            runner_image=payload["runner_image"], command_policy_sha256=payload["command_policy_sha256"],
            validation_commands=tuple(payload["validation_commands"]), timeout_seconds=payload["timeout_seconds"],
            requested_at=payload["requested_at"],
        )


@dataclass(frozen=True)
class GateReceipt:
    """Trusted result produced by an isolated deterministic gate runner."""

    gate_id: str
    issue_identifier: str
    candidate_revision: CandidateRevision
    runner_image: str
    command_policy_sha256: str
    verdict: str
    started_at: str
    finished_at: str
    exit_code: int | None
    log_sha256: str
    job_uid: str
    pod_uid: str | None
    findings: tuple[ReviewFinding, ...]

    def __post_init__(self) -> None:
        if not GATE_ID.fullmatch(self.gate_id):
            raise ValueError("gate_id must be a stable gate-* identifier")
        if not ISSUE_IDENTIFIER.fullmatch(self.issue_identifier):
            raise ValueError("issue identifier must match GH-<positive-number>")
        if not PINNED_IMAGE.fullmatch(self.runner_image):
            raise ValueError("runner_image must use an immutable sha256 digest")
        _sha256_digest(self.command_policy_sha256, "command_policy_sha256")
        if self.verdict not in {"PASS", "FAIL", "INFRASTRUCTURE_FAILURE"}:
            raise ValueError("gate verdict must be PASS, FAIL, or INFRASTRUCTURE_FAILURE")
        started = _utc_timestamp(self.started_at, "started_at")
        finished = _utc_timestamp(self.finished_at, "finished_at")
        if finished < started:
            raise ValueError("finished_at must not precede started_at")
        if self.exit_code is not None and (isinstance(self.exit_code, bool) or not isinstance(self.exit_code, int)):
            raise ValueError("exit_code must be an integer or null")
        _sha256_digest(self.log_sha256, "log_sha256")
        _nonblank(self.job_uid, "job_uid")
        if self.pod_uid is not None:
            _nonblank(self.pod_uid, "pod_uid")
        if self.verdict == "PASS" and self.findings:
            raise ValueError("gate PASS cannot contain findings")
        if self.verdict == "PASS" and self.exit_code != 0:
            raise ValueError("gate PASS requires exit_code 0")
        if self.verdict == "FAIL" and not self.findings:
            raise ValueError("gate FAIL must contain at least one finding")
        if self.verdict == "FAIL" and (self.exit_code is None or self.exit_code == 0):
            raise ValueError("gate FAIL requires a non-zero exit_code")
        if self.verdict == "INFRASTRUCTURE_FAILURE" and self.findings:
            raise ValueError("infrastructure failure cannot contain candidate findings")

    def to_json(self) -> dict[str, Any]:
        return {
            "gate_id": self.gate_id, "issue_identifier": self.issue_identifier,
            "candidate_revision": self.candidate_revision.to_json(), "runner_image": self.runner_image,
            "command_policy_sha256": self.command_policy_sha256, "verdict": self.verdict,
            "started_at": self.started_at, "finished_at": self.finished_at, "exit_code": self.exit_code,
            "log_sha256": self.log_sha256, "job_uid": self.job_uid, "pod_uid": self.pod_uid,
            "findings": [finding.to_json() for finding in self.findings],
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "GateReceipt":
        required = {"gate_id", "issue_identifier", "candidate_revision", "runner_image", "command_policy_sha256", "verdict", "started_at", "finished_at", "exit_code", "log_sha256", "job_uid", "pod_uid", "findings"}
        if set(payload) != required:
            raise ValueError("gate receipt fields do not match the contract")
        string_fields = required - {
            "candidate_revision",
            "exit_code",
            "pod_uid",
            "findings",
        }
        if not all(isinstance(payload[name], str) for name in string_fields):
            raise ValueError("gate receipt scalar identity fields must be strings")
        if payload["pod_uid"] is not None and not isinstance(payload["pod_uid"], str):
            raise ValueError("gate receipt pod_uid must be a string or null")
        if not isinstance(payload["candidate_revision"], dict):
            raise ValueError("gate receipt candidate_revision must be an object")
        if not isinstance(payload["findings"], list) or not all(
            isinstance(value, dict) for value in payload["findings"]
        ):
            raise ValueError("gate findings must be an array of objects")
        return cls(
            gate_id=payload["gate_id"], issue_identifier=payload["issue_identifier"],
            candidate_revision=CandidateRevision.from_json(dict(payload["candidate_revision"])),
            runner_image=payload["runner_image"], command_policy_sha256=payload["command_policy_sha256"], verdict=payload["verdict"],
            started_at=payload["started_at"], finished_at=payload["finished_at"], exit_code=payload["exit_code"],
            log_sha256=payload["log_sha256"], job_uid=payload["job_uid"], pod_uid=payload["pod_uid"],
            findings=tuple(
                ReviewFinding.from_json(dict(value)) for value in payload["findings"]
            ),
        )


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
    implementer_session_id: str | None

    def __post_init__(self) -> None:
        if not ISSUE_IDENTIFIER.fullmatch(self.issue_identifier):
            raise ValueError("issue identifier must match GH-<positive-number>")
        _nonblank(self.objective, "objective")
        _full_sha(self.base_sha, "base_sha")
        _full_sha(self.head_sha, "head_sha")
        if self.implementer_session_id is not None:
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
        ):
            if not isinstance(payload[field_name], str):
                raise ValueError(f"{field_name} must be a string")
        if payload["implementer_session_id"] is not None and not isinstance(
            payload["implementer_session_id"], str
        ):
            raise ValueError("implementer_session_id must be a string or null")
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
            implementer_session_id=payload["implementer_session_id"],
        )


@dataclass(frozen=True)
class TurnReceipt:
    """Trusted Symphony receipt captured outside the model output."""

    session_id: str
    thread_id: str
    turn_id: str
    role: str
    head_sha: str
    candidate_revision: str | None = None

    def __post_init__(self) -> None:
        for field_name in ("session_id", "thread_id", "turn_id"):
            _nonblank(getattr(self, field_name), field_name)
        if self.role not in TURN_ROLES:
            raise ValueError(f"role must be one of {sorted(TURN_ROLES)}")
        _full_sha(self.head_sha, "head_sha")
        if self.candidate_revision is not None:
            _sha256_digest(self.candidate_revision, "candidate_revision")


@dataclass(frozen=True)
class ReviewProposal:
    """Untrusted reviewer output before host-owned identities are attached."""

    verdict: str
    head_sha: str
    reviewer_role: str
    findings: tuple[ReviewFinding, ...]
    candidate_revision: str

    def __post_init__(self) -> None:
        if self.verdict not in {"PASS", "FAIL"}:
            raise ValueError("verdict must be PASS or FAIL")
        _full_sha(self.head_sha, "head_sha")
        _sha256_digest(self.candidate_revision, "candidate_revision")
        if self.reviewer_role not in REVIEWER_ROLES:
            raise ValueError(f"reviewer_role must be one of {sorted(REVIEWER_ROLES)}")
        if self.verdict == "PASS" and self.findings:
            raise ValueError("PASS cannot contain findings")
        if self.verdict == "FAIL" and not self.findings:
            raise ValueError("FAIL must contain at least one finding")

    def to_json(self) -> dict[str, Any]:
        return {
            "verdict": self.verdict,
            "head_sha": self.head_sha,
            "candidate_revision": self.candidate_revision,
            "reviewer_role": self.reviewer_role,
            "findings": [finding.to_json() for finding in self.findings],
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "ReviewProposal":
        required = {"verdict", "head_sha", "candidate_revision", "reviewer_role", "findings"}
        if set(payload) != required:
            raise ValueError("review proposal fields do not match the contract")
        if not all(
            isinstance(payload[field], str)
            for field in ("verdict", "head_sha", "candidate_revision", "reviewer_role")
        ):
            raise ValueError("review proposal scalar fields must be strings")
        if not isinstance(payload["findings"], list) or not all(
            isinstance(value, dict) for value in payload["findings"]
        ):
            raise ValueError("findings must be an array of objects")
        return cls(
            verdict=payload["verdict"],
            head_sha=payload["head_sha"],
            reviewer_role=payload["reviewer_role"],
            findings=tuple(
                ReviewFinding.from_json(dict(value)) for value in payload["findings"]
            ),
            candidate_revision=payload["candidate_revision"],
        )


@dataclass(frozen=True)
class ReviewDecision:
    verdict: str
    head_sha: str
    reviewer_role: str
    reviewer_session_id: str
    implementer_session_id: str
    findings: tuple[ReviewFinding, ...]
    candidate_revision: str

    def __post_init__(self) -> None:
        if self.verdict not in {"PASS", "FAIL"}:
            raise ValueError("verdict must be PASS or FAIL")
        _full_sha(self.head_sha, "head_sha")
        _sha256_digest(self.candidate_revision, "candidate_revision")
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
            "candidate_revision": self.candidate_revision,
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
            "candidate_revision",
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
            "candidate_revision",
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
            candidate_revision=str(payload["candidate_revision"]),
        )


class ReviewLedger:
    """Stores immutable review decisions keyed by the exact candidate SHA."""

    def __init__(self) -> None:
        self._decisions: dict[str, ReviewDecision] = {}

    def record(self, decision: ReviewDecision) -> None:
        self._decisions[decision.candidate_revision] = decision

    def has_pass_for(self, candidate_revision: str) -> bool:
        _sha256_digest(candidate_revision, "candidate_revision")
        decision = self._decisions.get(candidate_revision)
        return decision is not None and decision.verdict == "PASS"


def parse_review_decision(
    payload: dict[str, Any],
    *,
    expected_head_sha: str,
    expected_candidate_revision: str,
    reviewer_session_id: str,
    implementer_session_id: str,
) -> ReviewDecision:
    """Validate model output against host-owned review identity and candidate state."""

    decision = ReviewDecision.from_json(payload)
    expected_head = _full_sha(expected_head_sha, "expected_head_sha")
    expected_candidate = _sha256_digest(
        expected_candidate_revision, "expected_candidate_revision"
    )
    expected_reviewer = _nonblank(reviewer_session_id, "reviewer_session_id")
    expected_implementer = _nonblank(
        implementer_session_id, "implementer_session_id"
    )
    if decision.head_sha != expected_head:
        raise ValueError("review decision head_sha differs from the host candidate")
    if decision.candidate_revision != expected_candidate:
        raise ValueError("review decision candidate_revision differs from the host candidate")
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
REVIEW_PROPOSAL_SCHEMA = _load_schema("review-proposal.schema.json")
GATE_REQUEST_SCHEMA = _load_schema("gate-request.schema.json")
GATE_RECEIPT_SCHEMA = _load_schema("gate-receipt.schema.json")
