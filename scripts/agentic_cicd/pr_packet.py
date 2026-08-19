from __future__ import annotations

import base64
import json
import re
from dataclasses import dataclass
from typing import Any, TYPE_CHECKING

from .protocol import GateReceipt, GateRequest

if TYPE_CHECKING:
    from .coordinator import TaskSnapshot


ISSUE_IDENTIFIER = re.compile(r"GH-[1-9][0-9]*\Z")
ACCEPTANCE_IDENTIFIER = re.compile(r"AC-[A-Z0-9][A-Z0-9-]{1,63}\Z")
ACCEPTANCE_LINE = re.compile(
    r"^- \[ \] (AC-[A-Z0-9][A-Z0-9-]{1,63}) \| (.+) \| Evidence: `([^`]+)`$"
)
SECTION = re.compile(r"(?m)^### ([^\r\n]+)\s*$")
PACKET_MARKER = re.compile(
    r"\A<!-- j-store-agentic-cicd-pr-packet:([A-Za-z0-9_-]+) -->\n"
)
PLACEHOLDER = re.compile(r"(?i)(?:\bTBD\b|\bTODO\b|<[^>]*placeholder[^>]*>)")

ISSUE_SECTIONS = {
    "intent": "\u5177\u4f53\u76ee\u6807",
    "value": "\u4ef7\u503c\u4e0e\u52a8\u673a",
    "in_scope": "\u8303\u56f4",
    "out_of_scope": "\u975e\u76ee\u6807",
    "acceptance": "\u9a8c\u6536\u6807\u51c6",
    "validation": "\u5fc5\u9700\u9a8c\u8bc1",
    "compatibility": "\u517c\u5bb9\u6027\u4e0e\u8fc1\u79fb",
    "recovery": "\u6062\u590d\u4e0e\u56de\u6eda",
    "required_human_approvals": "\u6240\u9700\u4eba\u5de5\u5ba1\u6279",
    "residual_risks": "\u6b8b\u4f59\u98ce\u9669",
    "risk": "\u98ce\u9669\u7c7b\u578b",
    "authorization": "\u786e\u8ba4",
}


def _nonblank(value: str, field_name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field_name} must not be blank")
    normalized = value.strip()
    if PLACEHOLDER.search(normalized):
        raise ValueError(f"{field_name} contains placeholder text")
    return normalized


def _string_tuple(value: Any, field_name: str) -> tuple[str, ...]:
    if not isinstance(value, list) or not value:
        raise ValueError(f"{field_name} must be a non-empty array")
    return tuple(_nonblank(item, field_name) for item in value)


def _sections(body: str) -> dict[str, str]:
    matches = list(SECTION.finditer(body))
    sections: dict[str, str] = {}
    for index, match in enumerate(matches):
        name = match.group(1).strip()
        if name in sections:
            raise ValueError(f"duplicate Issue section: {name}")
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        sections[name] = body[match.end() : end].strip()
    return sections


def _list_values(value: str, field_name: str) -> tuple[str, ...]:
    results: list[str] = []
    for line in value.splitlines():
        normalized = line.strip()
        if not normalized:
            continue
        if normalized.startswith("- "):
            normalized = normalized[2:].strip()
        if normalized.startswith("`") and normalized.endswith("`"):
            normalized = normalized[1:-1].strip()
        results.append(_nonblank(normalized, field_name))
    if not results:
        raise ValueError(f"{field_name} must not be empty")
    return tuple(results)


@dataclass(frozen=True)
class AcceptanceCriterion:
    identifier: str
    statement: str
    evidence_command: str

    def __post_init__(self) -> None:
        if not ACCEPTANCE_IDENTIFIER.fullmatch(self.identifier):
            raise ValueError("acceptance identifier is invalid")
        _nonblank(self.statement, "acceptance statement")
        _nonblank(self.evidence_command, "acceptance evidence command")

    def to_json(self) -> dict[str, str]:
        return {
            "identifier": self.identifier,
            "statement": self.statement,
            "evidence_command": self.evidence_command,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "AcceptanceCriterion":
        if set(payload) != {"identifier", "statement", "evidence_command"}:
            raise ValueError("acceptance criterion fields do not match the contract")
        if not all(isinstance(value, str) for value in payload.values()):
            raise ValueError("acceptance criterion values must be strings")
        return cls(**payload)


@dataclass(frozen=True)
class TaskBrief:
    issue_identifier: str
    title: str
    intent: str
    value: str
    in_scope: str
    out_of_scope: str
    acceptance: tuple[AcceptanceCriterion, ...]
    validation_commands: tuple[str, ...]
    compatibility: str
    recovery: str
    required_human_approvals: tuple[str, ...]
    residual_risks: tuple[str, ...]
    risk: str

    def __post_init__(self) -> None:
        if not ISSUE_IDENTIFIER.fullmatch(self.issue_identifier):
            raise ValueError("issue identifier must match GH-<positive-number>")
        for field_name in (
            "title",
            "intent",
            "value",
            "in_scope",
            "out_of_scope",
            "compatibility",
            "recovery",
            "risk",
        ):
            _nonblank(getattr(self, field_name), field_name)
        if not self.acceptance:
            raise ValueError("acceptance must not be empty")
        identifiers = [item.identifier for item in self.acceptance]
        if len(identifiers) != len(set(identifiers)):
            raise ValueError("acceptance identifiers must be unique")
        if not self.validation_commands:
            raise ValueError("validation commands must not be empty")
        if len(self.validation_commands) != len(set(self.validation_commands)):
            raise ValueError("validation commands must be unique")
        for criterion in self.acceptance:
            if criterion.evidence_command not in self.validation_commands:
                raise ValueError("acceptance evidence must name a required validation command")
        if not self.required_human_approvals or not self.residual_risks:
            raise ValueError("approval and residual-risk conclusions are required")
        for value in self.required_human_approvals + self.residual_risks:
            _nonblank(value, "task brief conclusion")
        if "\u9ad8\u98ce\u9669" in self.risk and self.approvals_resolved:
            raise ValueError("high-risk tasks must name a required human approval")

    @property
    def approvals_resolved(self) -> bool:
        return len(self.required_human_approvals) == 1 and self.required_human_approvals[0].casefold() == "none"

    @classmethod
    def parse(cls, issue_identifier: str, title: str, body: str) -> "TaskBrief":
        parsed = _sections(_nonblank(body, "Issue body"))
        missing = [name for name in ISSUE_SECTIONS.values() if name not in parsed]
        if missing:
            raise ValueError(f"Issue body is missing required sections: {missing}")
        acceptance: list[AcceptanceCriterion] = []
        for line in parsed[ISSUE_SECTIONS["acceptance"]].splitlines():
            normalized = line.strip()
            if not normalized:
                continue
            match = ACCEPTANCE_LINE.fullmatch(normalized)
            if match is None:
                raise ValueError("each acceptance line must bind an ID, statement, and evidence command")
            acceptance.append(AcceptanceCriterion(*match.groups()))
        authorization = parsed[ISSUE_SECTIONS["authorization"]]
        authorization_lines = [line.strip() for line in authorization.splitlines() if line.strip()]
        if not authorization_lines or any(not line.startswith("- [x] ") for line in authorization_lines):
            raise ValueError("all Issue authorization acknowledgements must be checked")
        return cls(
            issue_identifier=issue_identifier,
            title=_nonblank(title, "Issue title"),
            intent=_nonblank(parsed[ISSUE_SECTIONS["intent"]], "intent"),
            value=_nonblank(parsed[ISSUE_SECTIONS["value"]], "value"),
            in_scope=_nonblank(parsed[ISSUE_SECTIONS["in_scope"]], "scope"),
            out_of_scope=_nonblank(parsed[ISSUE_SECTIONS["out_of_scope"]], "out of scope"),
            acceptance=tuple(acceptance),
            validation_commands=_list_values(parsed[ISSUE_SECTIONS["validation"]], "validation command"),
            compatibility=_nonblank(parsed[ISSUE_SECTIONS["compatibility"]], "compatibility"),
            recovery=_nonblank(parsed[ISSUE_SECTIONS["recovery"]], "recovery"),
            required_human_approvals=_list_values(
                parsed[ISSUE_SECTIONS["required_human_approvals"]], "required human approval"
            ),
            residual_risks=_list_values(parsed[ISSUE_SECTIONS["residual_risks"]], "residual risk"),
            risk=_nonblank(parsed[ISSUE_SECTIONS["risk"]], "risk"),
        )

    def to_json(self) -> dict[str, Any]:
        return {
            "issue_identifier": self.issue_identifier,
            "title": self.title,
            "intent": self.intent,
            "value": self.value,
            "in_scope": self.in_scope,
            "out_of_scope": self.out_of_scope,
            "acceptance": [item.to_json() for item in self.acceptance],
            "validation_commands": list(self.validation_commands),
            "compatibility": self.compatibility,
            "recovery": self.recovery,
            "required_human_approvals": list(self.required_human_approvals),
            "residual_risks": list(self.residual_risks),
            "risk": self.risk,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "TaskBrief":
        required = {
            "issue_identifier", "title", "intent", "value", "in_scope",
            "out_of_scope", "acceptance", "validation_commands", "compatibility",
            "recovery", "required_human_approvals", "residual_risks", "risk",
        }
        if set(payload) != required:
            raise ValueError("task brief fields do not match the contract")
        scalar_fields = required - {
            "acceptance", "validation_commands", "required_human_approvals", "residual_risks"
        }
        if not all(isinstance(payload[name], str) for name in scalar_fields):
            raise ValueError("task brief scalar values must be strings")
        acceptance = payload["acceptance"]
        if not isinstance(acceptance, list) or not all(isinstance(item, dict) for item in acceptance):
            raise ValueError("task brief acceptance must be an array of objects")
        return cls(
            **{name: payload[name] for name in scalar_fields},
            acceptance=tuple(AcceptanceCriterion.from_json(item) for item in acceptance),
            validation_commands=_string_tuple(payload["validation_commands"], "validation commands"),
            required_human_approvals=_string_tuple(payload["required_human_approvals"], "required human approvals"),
            residual_risks=_string_tuple(payload["residual_risks"], "residual risks"),
        )


@dataclass(frozen=True)
class PullRequestPacket:
    issue_identifier: str
    issue_title: str
    candidate_revision: str
    promoted_head_sha: str
    target_branch: str
    source_branch: str
    intent: str
    in_scope: str
    out_of_scope: str
    acceptance: tuple[AcceptanceCriterion, ...]
    validation_commands: tuple[str, ...]
    gate_id: str
    gate_log_sha256: str
    compatibility: str
    recovery: str
    reviewer_role: str
    reviewer_session_id: str
    required_human_approvals: tuple[str, ...]
    residual_risks: tuple[str, ...]
    skipped_checks: tuple[str, ...]

    def __post_init__(self) -> None:
        TaskBrief(
            issue_identifier=self.issue_identifier,
            title=self.issue_title,
            intent=self.intent,
            value="bound in task brief",
            in_scope=self.in_scope,
            out_of_scope=self.out_of_scope,
            acceptance=self.acceptance,
            validation_commands=self.validation_commands,
            compatibility=self.compatibility,
            recovery=self.recovery,
            required_human_approvals=self.required_human_approvals,
            residual_risks=self.residual_risks,
            risk="low",
        )
        if not re.fullmatch(r"[0-9a-f]{64}", self.candidate_revision):
            raise ValueError("packet candidate revision must be a SHA-256")
        if not re.fullmatch(r"[0-9a-f]{40}", self.promoted_head_sha):
            raise ValueError("packet promoted head must be a full Git SHA")
        for field_name in ("target_branch", "source_branch", "gate_id", "gate_log_sha256", "reviewer_role", "reviewer_session_id"):
            _nonblank(getattr(self, field_name), field_name)
        if not re.fullmatch(r"[0-9a-f]{64}", self.gate_log_sha256):
            raise ValueError("packet gate log digest must be a SHA-256")
        if not self.skipped_checks:
            raise ValueError("packet must state its skipped-check conclusion")

    @property
    def approvals_resolved(self) -> bool:
        return len(self.required_human_approvals) == 1 and self.required_human_approvals[0].casefold() == "none"

    def to_json(self) -> dict[str, Any]:
        return {
            "issue_identifier": self.issue_identifier,
            "issue_title": self.issue_title,
            "candidate_revision": self.candidate_revision,
            "promoted_head_sha": self.promoted_head_sha,
            "target_branch": self.target_branch,
            "source_branch": self.source_branch,
            "intent": self.intent,
            "in_scope": self.in_scope,
            "out_of_scope": self.out_of_scope,
            "acceptance": [item.to_json() for item in self.acceptance],
            "validation_commands": list(self.validation_commands),
            "gate_id": self.gate_id,
            "gate_log_sha256": self.gate_log_sha256,
            "compatibility": self.compatibility,
            "recovery": self.recovery,
            "reviewer_role": self.reviewer_role,
            "reviewer_session_id": self.reviewer_session_id,
            "required_human_approvals": list(self.required_human_approvals),
            "residual_risks": list(self.residual_risks),
            "skipped_checks": list(self.skipped_checks),
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "PullRequestPacket":
        required = {
            "issue_identifier", "issue_title", "candidate_revision", "promoted_head_sha",
            "target_branch", "source_branch", "intent", "in_scope", "out_of_scope",
            "acceptance", "validation_commands", "gate_id", "gate_log_sha256",
            "compatibility", "recovery", "reviewer_role", "reviewer_session_id",
            "required_human_approvals", "residual_risks", "skipped_checks",
        }
        if set(payload) != required:
            raise ValueError("pull request packet fields do not match the contract")
        tuple_fields = {
            "acceptance", "validation_commands", "required_human_approvals",
            "residual_risks", "skipped_checks",
        }
        scalar_fields = required - tuple_fields
        if not all(isinstance(payload[name], str) for name in scalar_fields):
            raise ValueError("pull request packet scalar values must be strings")
        acceptance = payload["acceptance"]
        if not isinstance(acceptance, list) or not all(isinstance(item, dict) for item in acceptance):
            raise ValueError("packet acceptance must be an array of objects")
        return cls(
            **{name: payload[name] for name in scalar_fields},
            acceptance=tuple(AcceptanceCriterion.from_json(item) for item in acceptance),
            validation_commands=_string_tuple(payload["validation_commands"], "validation commands"),
            required_human_approvals=_string_tuple(payload["required_human_approvals"], "required human approvals"),
            residual_risks=_string_tuple(payload["residual_risks"], "residual risks"),
            skipped_checks=_string_tuple(payload["skipped_checks"], "skipped checks"),
        )

    def render(self) -> str:
        encoded = base64.urlsafe_b64encode(
            json.dumps(self.to_json(), separators=(",", ":"), sort_keys=True).encode()
        ).decode().rstrip("=")
        acceptance = "\n".join(
            f"- [x] `{item.identifier}` {item.statement}\n  Evidence: `{item.evidence_command}` via GateReceipt `{self.gate_id}` PASS."
            for item in self.acceptance
        )
        commands = "\n".join(
            f"- [x] `{command}`: PASS (GateReceipt `{self.gate_id}`, log `{self.gate_log_sha256}`)."
            for command in self.validation_commands
        )
        approvals = "\n".join(f"- [x] {value}" for value in self.required_human_approvals)
        risks = "\n".join(f"- {value}" for value in self.residual_risks)
        skipped = "\n".join(f"- {value}" for value in self.skipped_checks)
        return (
            f"<!-- j-store-agentic-cicd-pr-packet:{encoded} -->\n"
            "## Intent\n\n"
            f"- Requirement / issue: `{self.issue_identifier}` {self.issue_title}\n"
            f"- CandidateRevision: `{self.candidate_revision}`\n"
            f"- Promoted head: `{self.promoted_head_sha}`\n"
            f"- Intent: {self.intent}\n"
            f"- In scope: {self.in_scope}\n"
            f"- Out of scope: {self.out_of_scope}\n\n"
            "## Branch policy\n\n"
            f"- [x] Target / source branch: `{self.target_branch}` / `{self.source_branch}`.\n"
            "- [x] Exact-SHA non-force push; merge, release and deploy remain prohibited.\n\n"
            "## Evidence\n\n"
            f"{acceptance}\n\nCommands and results:\n\n{commands}\n\n"
            f"Compatibility and migration: {self.compatibility}\n\n"
            f"Recovery and rollback: {self.recovery}\n\n"
            f"Skipped checks:\n\n{skipped}\n\n"
            "## Independent review\n\n"
            f"- [x] `{self.reviewer_role}` session `{self.reviewer_session_id}` passed the exact candidate.\n"
            f"- [x] Required human approval:\n{approvals}\n\n"
            "## Residual risk\n\n"
            f"{risks}"
        )

    @classmethod
    def parse(cls, body: str) -> "PullRequestPacket":
        match = PACKET_MARKER.match(body)
        if match is None or "- [ ]" in body or PLACEHOLDER.search(body):
            raise ValueError("PR body is not a complete structured packet")
        encoded = match.group(1)
        try:
            payload = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))
        except (ValueError, json.JSONDecodeError) as error:
            raise ValueError("PR packet marker is invalid") from error
        if not isinstance(payload, dict):
            raise ValueError("PR packet payload must be an object")
        packet = cls.from_json(payload)
        if packet.render() != body:
            raise ValueError("PR body differs from its structured packet")
        return packet


def build_pull_request_packet(snapshot: "TaskSnapshot", *, target_branch: str) -> PullRequestPacket:
    if snapshot.task_brief is None:
        raise RuntimeError("task has no trusted Issue brief")
    brief = TaskBrief.from_json(snapshot.task_brief)
    if brief.issue_identifier != snapshot.issue_identifier:
        raise RuntimeError("task brief differs from task identity")
    if not brief.approvals_resolved:
        raise RuntimeError("required human approval remains unresolved")
    if not snapshot.head_sha or snapshot.candidate_commit_sha != snapshot.head_sha:
        raise RuntimeError("promoted head does not match task state")
    if snapshot.candidate_revision is None or snapshot.gate_request is None or snapshot.gate_receipt is None:
        raise RuntimeError("task lacks candidate or deterministic gate evidence")
    request = GateRequest.from_json(snapshot.gate_request)
    receipt = GateReceipt.from_json(snapshot.gate_receipt)
    candidate = request.candidate_revision
    if candidate.to_json() != snapshot.candidate_revision:
        raise RuntimeError("gate request differs from CandidateRevision")
    if (
        receipt.verdict != "PASS"
        or receipt.issue_identifier != snapshot.issue_identifier
        or receipt.candidate_revision != candidate
        or receipt.gate_id != request.gate_id
        or receipt.runner_image != request.runner_image
        or receipt.command_policy_sha256 != request.command_policy_sha256
        or receipt.exit_code != 0
    ):
        raise RuntimeError("GateReceipt does not prove the exact candidate")
    if request.validation_commands != brief.validation_commands:
        raise RuntimeError("GateRequest commands differ from the trusted Issue brief")
    decision = snapshot.review_decision_for(candidate.candidate_revision)
    if (
        decision is None
        or decision.verdict != "PASS"
        or decision.head_sha != snapshot.head_sha
        or decision.candidate_revision != candidate.candidate_revision
    ):
        raise RuntimeError("independent review does not bind the promoted candidate")
    return PullRequestPacket(
        issue_identifier=snapshot.issue_identifier,
        issue_title=brief.title,
        candidate_revision=candidate.candidate_revision,
        promoted_head_sha=snapshot.head_sha,
        target_branch=_nonblank(target_branch, "target branch"),
        source_branch=_nonblank(snapshot.branch or "", "source branch"),
        intent=brief.intent,
        in_scope=brief.in_scope,
        out_of_scope=brief.out_of_scope,
        acceptance=brief.acceptance,
        validation_commands=brief.validation_commands,
        gate_id=receipt.gate_id,
        gate_log_sha256=receipt.log_sha256,
        compatibility=brief.compatibility,
        recovery=brief.recovery,
        reviewer_role=decision.reviewer_role,
        reviewer_session_id=decision.reviewer_session_id,
        required_human_approvals=brief.required_human_approvals,
        residual_risks=brief.residual_risks,
        skipped_checks=(
            receipt.skipped_checks
            if receipt.skipped_checks
            else ("No skipped checks.",)
        ),
    )
