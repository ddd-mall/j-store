from __future__ import annotations

import unittest

from scripts.agentic_cicd.candidate import CandidateRevision
from scripts.agentic_cicd.gate_dispatcher import GateDispatcher, GateJobIdentity, GateJobResult
from scripts.agentic_cicd.protocol import GateRequest


IMAGE = "registry.example/gate@sha256:" + "d" * 64


def request() -> GateRequest:
    base, tree, artifact, policy = "a" * 40, "b" * 40, "1" * 64, "2" * 64
    candidate = CandidateRevision(base, tree, artifact, policy, CandidateRevision.calculate_revision(base, tree, artifact, policy))
    commands = ("./scripts/quality-gate.sh",)
    return GateRequest(
        "gate-123", "GH-123", candidate, IMAGE,
        GateRequest.calculate_command_policy_sha256(commands),
        commands, 600, "2026-08-15T00:00:00Z",
    )


class FakeGateJobClient:
    def __init__(self, *, status: str = "COMPLETED", exit_code: int | None = 0):
        self.identity: GateJobIdentity | None = None
        self.creations = 0
        self.status = status
        self.exit_code = exit_code
        self.deletions = 0

    def get(self, gate_id: str) -> GateJobIdentity | None:
        return self.identity

    def create(self, gate_request: GateRequest) -> GateJobIdentity:
        self.creations += 1
        self.identity = GateJobIdentity(
            gate_request.gate_id, gate_request.issue_identifier,
            gate_request.candidate_revision.candidate_revision,
            gate_request.runner_image, gate_request.command_policy_sha256,
            gate_request.validation_commands, gate_request.timeout_seconds,
            gate_request.requested_at, "job-uid",
        )
        return self.identity

    def await_result(self, gate_id: str, timeout_seconds: int) -> GateJobResult:
        return GateJobResult(
            self.status, "2026-08-15T00:00:01Z", "2026-08-15T00:00:02Z",
            self.exit_code, b"gate logs", "job-uid", "pod-uid", IMAGE,
        )

    def delete(self, gate_id: str) -> None:
        self.deletions += 1


class GateDispatcherTest(unittest.TestCase):
    def test_duplicate_dispatch_resumes_one_exact_job(self) -> None:
        client = FakeGateJobClient()
        dispatcher = GateDispatcher(client)

        first = dispatcher.dispatch(request())
        second = dispatcher.dispatch(request())

        self.assertEqual(1, client.creations)
        self.assertEqual(first, second)
        self.assertEqual("PASS", first.verdict)

    def test_existing_job_with_stale_candidate_is_rejected(self) -> None:
        client = FakeGateJobClient()
        client.identity = GateJobIdentity(
            "gate-123", "GH-123", "9" * 64, IMAGE,
            request().command_policy_sha256, request().validation_commands,
            request().timeout_seconds, request().requested_at, "job-uid"
        )

        with self.assertRaisesRegex(RuntimeError, "identity"):
            GateDispatcher(client).dispatch(request())

    def test_existing_job_from_another_task_is_rejected(self) -> None:
        gate_request = request()
        client = FakeGateJobClient()
        client.identity = GateJobIdentity(
            gate_request.gate_id, "GH-999",
            gate_request.candidate_revision.candidate_revision,
            gate_request.runner_image, gate_request.command_policy_sha256,
            gate_request.validation_commands, gate_request.timeout_seconds,
            gate_request.requested_at, "job-uid",
        )

        with self.assertRaisesRegex(RuntimeError, "identity"):
            GateDispatcher(client).dispatch(gate_request)

    def test_candidate_and_infrastructure_failures_are_classified_separately(self) -> None:
        candidate_failure = GateDispatcher(FakeGateJobClient(exit_code=7)).dispatch(request())
        infrastructure_failure = GateDispatcher(
            FakeGateJobClient(status="INFRASTRUCTURE_FAILURE", exit_code=None)
        ).dispatch(request())

        self.assertEqual("FAIL", candidate_failure.verdict)
        self.assertEqual(1, len(candidate_failure.findings))
        self.assertEqual("INFRASTRUCTURE_FAILURE", infrastructure_failure.verdict)
        self.assertEqual((), infrastructure_failure.findings)

    def test_runtime_image_and_job_uid_are_trusted_results(self) -> None:
        client = FakeGateJobClient()
        client.identity = GateJobIdentity(
            "gate-123", "GH-123", request().candidate_revision.candidate_revision,
            IMAGE, request().command_policy_sha256, request().validation_commands,
            request().timeout_seconds, request().requested_at, "other-job"
        )
        with self.assertRaisesRegex(RuntimeError, "Job UID"):
            GateDispatcher(client).dispatch(request())


if __name__ == "__main__":
    unittest.main()
