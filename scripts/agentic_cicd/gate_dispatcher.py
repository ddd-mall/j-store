from __future__ import annotations

import hashlib
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Protocol

from .protocol import GateReceipt, GateRequest, ReviewFinding


@dataclass(frozen=True)
class GateJobIdentity:
    gate_id: str
    issue_identifier: str
    candidate_revision: str
    runner_image: str
    command_policy_sha256: str
    validation_commands: tuple[str, ...]
    timeout_seconds: int
    requested_at: str
    job_uid: str


@dataclass(frozen=True)
class GateJobResult:
    status: str
    started_at: str
    finished_at: str
    exit_code: int | None
    logs: bytes
    job_uid: str
    pod_uid: str | None
    runtime_image: str


class GateJobClient(Protocol):
    def get(self, gate_id: str) -> GateJobIdentity | None: ...

    def create(self, request: GateRequest) -> GateJobIdentity: ...

    def await_result(self, gate_id: str, timeout_seconds: int) -> GateJobResult: ...

    def delete(self, gate_id: str) -> None: ...


class GateInfrastructureError(RuntimeError):
    pass


class GateDispatcher:
    """No-model dispatcher that resumes or creates exactly one isolated Job."""

    def __init__(self, client: GateJobClient):
        self.client = client

    def dispatch(self, request: GateRequest) -> GateReceipt:
        try:
            identity = self.client.get(request.gate_id)
            if identity is None:
                identity = self.client.create(request)
        except GateInfrastructureError as error:
            return self._infrastructure_receipt(request, str(error))
        expected_identity = (
            request.gate_id,
            request.issue_identifier,
            request.candidate_revision.candidate_revision,
            request.runner_image,
            request.command_policy_sha256,
            request.validation_commands,
            request.timeout_seconds,
            request.requested_at,
        )
        actual_identity = (
            identity.gate_id,
            identity.issue_identifier,
            identity.candidate_revision,
            identity.runner_image,
            identity.command_policy_sha256,
            identity.validation_commands,
            identity.timeout_seconds,
            identity.requested_at,
        )
        if actual_identity != expected_identity:
            raise RuntimeError("existing gate Job identity does not match the request")

        try:
            result = self.client.await_result(request.gate_id, request.timeout_seconds)
        except GateInfrastructureError as error:
            return self._infrastructure_receipt(request, str(error))
        if result.job_uid != identity.job_uid:
            raise RuntimeError("gate result Job UID does not match the dispatched Job")
        if result.runtime_image != request.runner_image:
            raise RuntimeError("gate runtime image does not match the requested digest")
        if result.status not in {"COMPLETED", "INFRASTRUCTURE_FAILURE"}:
            raise RuntimeError("gate Job returned an unknown terminal status")

        findings: tuple[ReviewFinding, ...] = ()
        if result.status == "INFRASTRUCTURE_FAILURE":
            verdict = "INFRASTRUCTURE_FAILURE"
        elif result.exit_code == 0:
            verdict = "PASS"
        else:
            verdict = "FAIL"
            findings = (
                ReviewFinding(
                    root_cause_id="gate:validation-command-failed",
                    severity="high",
                    evidence=f"The isolated gate exited with status {result.exit_code}.",
                    impact="The frozen candidate cannot enter independent review.",
                    expected_behavior="All commands in the trusted validation policy pass.",
                    verification="Dispatch a new gate for a newly frozen candidate.",
                ),
            )
        return GateReceipt(
            gate_id=request.gate_id,
            issue_identifier=request.issue_identifier,
            candidate_revision=request.candidate_revision,
            runner_image=request.runner_image,
            command_policy_sha256=request.command_policy_sha256,
            verdict=verdict,
            started_at=result.started_at,
            finished_at=result.finished_at,
            exit_code=result.exit_code,
            log_sha256=hashlib.sha256(result.logs).hexdigest(),
            job_uid=result.job_uid,
            pod_uid=result.pod_uid,
            findings=findings,
        )

    def cleanup(self, gate_id: str) -> None:
        self.client.delete(gate_id)

    @staticmethod
    def _infrastructure_receipt(
        request: GateRequest, evidence: str
    ) -> GateReceipt:
        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        return GateReceipt(
            gate_id=request.gate_id,
            issue_identifier=request.issue_identifier,
            candidate_revision=request.candidate_revision,
            runner_image=request.runner_image,
            command_policy_sha256=request.command_policy_sha256,
            verdict="INFRASTRUCTURE_FAILURE",
            started_at=timestamp,
            finished_at=timestamp,
            exit_code=None,
            log_sha256=hashlib.sha256(evidence.encode()).hexdigest(),
            job_uid=f"uncreated:{request.gate_id}",
            pod_uid=None,
            findings=(),
        )
