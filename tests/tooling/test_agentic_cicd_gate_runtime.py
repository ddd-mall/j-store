from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.candidate import CandidateRevision
from scripts.agentic_cicd.coordinator import SnapshotStore, TaskSnapshot
from scripts.agentic_cicd.gate_dispatcher import (
    GateDispatcher,
    GateInfrastructureError,
    GateJobIdentity,
    GateJobResult,
)
from scripts.agentic_cicd.gate_runtime import (
    GateDispatcherService,
    GateMailbox,
    GatePolicy,
    ValidatePhaseDriver,
)
from scripts.agentic_cicd.protocol import GateReceipt, ReviewFinding


REPO_ROOT = Path(__file__).resolve().parents[2]
CONTRACT = REPO_ROOT / "config" / "agentic-cicd" / "state-contract.json"
FETCH_IMAGE = "registry.internal/fetch@sha256:" + "3" * 64
RUNNER_IMAGE = "registry.internal/gate@sha256:" + "4" * 64


def failed_gate_receipt(request) -> GateReceipt:
    return GateReceipt(
        gate_id=request.gate_id,
        issue_identifier=request.issue_identifier,
        candidate_revision=request.candidate_revision,
        runner_image=request.runner_image,
        command_policy_sha256=request.command_policy_sha256,
        verdict="FAIL",
        started_at="2026-08-15T00:00:01Z",
        finished_at="2026-08-15T00:00:02Z",
        exit_code=1,
        log_sha256="5" * 64,
        job_uid="job-uid",
        pod_uid="pod-uid",
        findings=(
            ReviewFinding(
                "gate:validation-command-failed",
                "high",
                "The isolated gate exited with status 1.",
                "The frozen candidate cannot enter independent review.",
                "All commands in the trusted validation policy pass.",
                "Dispatch a new gate for a newly frozen candidate.",
            ),
        ),
    )


class FakeJobClient:
    def __init__(self) -> None:
        self.identity: GateJobIdentity | None = None
        self.creations = 0
        self.deletions = 0

    def get(self, gate_id: str) -> GateJobIdentity | None:
        return self.identity

    def create(self, request) -> GateJobIdentity:
        self.creations += 1
        self.identity = GateJobIdentity(
            request.gate_id,
            request.issue_identifier,
            request.candidate_revision.candidate_revision,
            request.runner_image,
            request.command_policy_sha256,
            request.validation_commands,
            request.timeout_seconds,
            request.requested_at,
            "job-uid",
        )
        return self.identity

    def await_result(self, gate_id: str, timeout_seconds: int) -> GateJobResult:
        return GateJobResult(
            "COMPLETED",
            "2026-08-15T00:00:01Z",
            "2026-08-15T00:00:02Z",
            0,
            b"PASS\n",
            "job-uid",
            "pod-uid",
            RUNNER_IMAGE,
        )

    def delete(self, gate_id: str) -> None:
        self.deletions += 1


class CleanupFailingJobClient(FakeJobClient):
    def __init__(self) -> None:
        super().__init__()
        self.failures_remaining = 1

    def delete(self, gate_id: str) -> None:
        self.deletions += 1
        if self.failures_remaining:
            self.failures_remaining -= 1
            raise GateInfrastructureError("temporary delete failure")


class GateRuntimeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.workspace = self.root / "workspace"
        self.workspace.mkdir()
        subprocess.run(["git", "init", "-b", "task"], cwd=self.workspace, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Gate Runtime Test"], cwd=self.workspace, check=True)
        subprocess.run(["git", "config", "user.email", "gate@example.invalid"], cwd=self.workspace, check=True)
        (self.workspace / "candidate.txt").write_text("candidate\n", encoding="utf-8")
        subprocess.run(["git", "add", "candidate.txt"], cwd=self.workspace, check=True)
        subprocess.run(["git", "commit", "-m", "baseline"], cwd=self.workspace, check=True, capture_output=True)
        self.head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.workspace, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        metadata = self.workspace / ".agentic-cicd" / "workspace.json"
        metadata.parent.mkdir()
        metadata.write_text(
            json.dumps({
                "issue_identifier": "GH-123", "repository": "ddd-mall/j-store",
                "base_sha": self.head,
                "branch": "codex/gh-123-task",
            }), encoding="utf-8",
        )
        self.state_root = self.root / "state"
        self.snapshot_path = self.state_root / "tasks" / "GH-123.json"
        SnapshotStore(self.snapshot_path).save(TaskSnapshot(
            issue_identifier="GH-123", state="queued",
            repository="ddd-mall/j-store", base_sha=self.head,
            head_sha=self.head, branch="codex/gh-123-task",
            workspace=str(self.workspace.resolve()), iteration_phase="validate",
            implementer_session_id="implementer-session",
        ))
        self.policy = GatePolicy.from_json({
            "runner_image": RUNNER_IMAGE,
            "fetch_image": FETCH_IMAGE,
            "validation_commands": ["/opt/jstore-gate/run-quality-gate"],
            "timeout_seconds": 600,
        })
        self.mailbox = GateMailbox(self.root / "exchange")
        self.driver = ValidatePhaseDriver(
            state_root=self.state_root, artifact_root=self.root / "artifacts",
            mailbox=self.mailbox, policy=self.policy, contract_path=CONTRACT,
            enabled=True,
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_validate_recovery_freezes_once_dispatches_once_and_consumes_receipt(self) -> None:
        self.driver.advance("GH-123", self.workspace)
        first = SnapshotStore(self.snapshot_path).load()
        self.assertIsNotNone(first.candidate_revision)
        self.assertIsNotNone(first.gate_request)
        self.assertEqual(1, len(self.mailbox.pending_requests()))

        self.driver.advance("GH-123", self.workspace)
        self.assertEqual(1, len(self.mailbox.pending_requests()))

        client = FakeJobClient()
        service = GateDispatcherService(
            self.mailbox, GateDispatcher(client), self.policy
        )
        self.assertEqual(1, service.process_once())
        self.assertEqual(0, service.process_once())
        self.assertEqual(1, client.creations)
        self.assertEqual(1, client.deletions)

        self.driver.advance("GH-123", self.workspace)
        completed = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("review", completed.iteration_phase)
        self.assertIsNotNone(completed.gate_receipt)
        self.assertEqual([], self.mailbox.pending_requests())

    def test_unchanged_failed_candidate_returns_to_implement_without_repeating_gate(self) -> None:
        self.driver.advance("GH-123", self.workspace)
        request = self.mailbox.read_request(self.mailbox.pending_requests()[0])
        self.mailbox.publish_receipt(failed_gate_receipt(request))
        self.driver.advance("GH-123", self.workspace)

        snapshot = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("implement", snapshot.iteration_phase)
        snapshot.candidate_revision = None
        snapshot.gate_request = None
        snapshot.gate_receipt = None
        snapshot.implementer_session_id = "unchanged-implementer"
        snapshot.iteration_phase = "validate"
        SnapshotStore(self.snapshot_path).save(snapshot)

        self.driver.advance("GH-123", self.workspace)

        restored = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("implement", restored.iteration_phase)
        self.assertIsNone(restored.candidate_revision)
        self.assertIsNone(restored.gate_request)
        self.assertEqual([], self.mailbox.pending_requests())
        self.assertEqual({}, restored.semantic_fix_strategies)

    def test_mailbox_rejects_identity_collision_and_dispatcher_policy_drift(self) -> None:
        self.driver.advance("GH-123", self.workspace)
        path = self.mailbox.pending_requests()[0]
        request = self.mailbox.read_request(path)
        payload = request.to_json()
        payload["requested_at"] = "2026-08-15T00:00:00Z"
        from scripts.agentic_cicd.protocol import GateRequest

        with self.assertRaisesRegex(RuntimeError, "collision"):
            self.mailbox.publish_request(GateRequest.from_json(payload))

    def test_cleanup_failure_does_not_stop_receipt_processing(self) -> None:
        self.driver.advance("GH-123", self.workspace)
        client = CleanupFailingJobClient()
        service = GateDispatcherService(
            self.mailbox, GateDispatcher(client), self.policy
        )

        self.assertEqual(1, service.process_once())
        self.assertEqual(0, service.process_once())
        self.assertIsNotNone(
            self.mailbox.read_receipt(self.mailbox.pending_requests()[0].stem)
        )
        self.assertEqual(2, client.deletions)
        gate_id = self.mailbox.pending_requests()[0].stem
        self.assertTrue(self.mailbox.cleanup_complete(gate_id))

    def test_gate_policy_runtime_validation_matches_schema_bounds(self) -> None:
        baseline = {
            "runner_image": RUNNER_IMAGE,
            "fetch_image": FETCH_IMAGE,
            "validation_commands": ["/opt/jstore-gate/run-quality-gate"],
            "timeout_seconds": 600,
        }
        for field, value in (
            ("timeout_seconds", 59),
            ("timeout_seconds", 3601),
            ("fetch_image", "anything@sha256:not-a-digest"),
            ("runner_image", "gate@sha256:" + "0" * 64),
            ("validation_commands", ["./scripts/quality-gate.sh"]),
        ):
            with self.subTest(field=field, value=value):
                payload = dict(baseline)
                payload[field] = value
                with self.assertRaises(ValueError):
                    GatePolicy.from_json(payload)

    def test_semantic_fix_budget_allows_two_distinct_candidates_then_fuses(self) -> None:
        snapshot = SnapshotStore(self.snapshot_path).load()
        snapshot.pending_review_findings = [{
            "root_cause_id": "review-auth-boundary",
            "severity": "HIGH",
            "summary": "candidate bypasses the trusted boundary",
        }]
        SnapshotStore(self.snapshot_path).save(snapshot)

        def revision(marker: str) -> CandidateRevision:
            base = marker * 40
            tree = marker * 40
            artifact = marker * 64
            policy = "f" * 64
            return CandidateRevision(
                base, tree, artifact, policy,
                CandidateRevision.calculate_revision(base, tree, artifact, policy),
            )

        first = revision("1")
        current = SnapshotStore(self.snapshot_path).load()
        self.assertTrue(
            self.driver._record_semantic_fix_budget(
                self.snapshot_path, current, first
            )
        )
        current = SnapshotStore(self.snapshot_path).load()
        self.assertFalse(
            self.driver._record_semantic_fix_budget(
                self.snapshot_path, current, first
            )
        )
        duplicate = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("implement", duplicate.iteration_phase)
        self.assertIsNone(duplicate.candidate_revision)
        self.assertIsNone(duplicate.implementer_session_id)
        duplicate.iteration_phase = "validate"
        SnapshotStore(self.snapshot_path).save(duplicate)
        current = SnapshotStore(self.snapshot_path).load()
        self.assertTrue(
            self.driver._record_semantic_fix_budget(
                self.snapshot_path, current, revision("2")
            )
        )
        current = SnapshotStore(self.snapshot_path).load()
        self.assertFalse(
            self.driver._record_semantic_fix_budget(
                self.snapshot_path, current, revision("3")
            )
        )
        fused = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("fused", fused.state)
        self.assertEqual(
            [first.candidate_revision, revision("2").candidate_revision],
            fused.semantic_fix_strategies["review-auth-boundary"],
        )


if __name__ == "__main__":
    unittest.main()
