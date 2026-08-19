from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.coordinator import SnapshotStore, TaskSnapshot
from scripts.agentic_cicd.candidate import CandidateRevision
from scripts.agentic_cicd.phase_bridge import (
    PHASE_COMPLETE,
    PHASE_IMPLEMENT,
    PHASE_REVIEW,
    PHASE_VALIDATE,
    IterationInputs,
    PhaseBridgeError,
    SymphonyPhaseBridge,
)
from scripts.agentic_cicd.protocol import (
    GateReceipt,
    ReviewDecision,
    ReviewFinding,
    ReviewProposal,
    TurnReceipt,
)
from scripts.agentic_cicd.workspace import BaseSyncResult


SHA_A = "a" * 40
SHA_B = "b" * 40
SHA_C = "c" * 40
IMAGE = "registry.example/gate@sha256:" + "d" * 64
POLICY = "e" * 64


def candidate(tree: str = SHA_B) -> CandidateRevision:
    artifact = "1" * 64
    policy = "2" * 64
    return CandidateRevision(
        base_sha=SHA_A,
        tree_sha=tree,
        artifact_sha256=artifact,
        snapshot_policy_sha256=policy,
        candidate_revision=CandidateRevision.calculate_revision(SHA_A, tree, artifact, policy),
    )


def gate(gate_id: str, verdict: str, findings: tuple[ReviewFinding, ...] = (), revision: CandidateRevision | None = None) -> GateReceipt:
    return GateReceipt(
        gate_id=gate_id, issue_identifier="GH-123", candidate_revision=revision or candidate(),
        runner_image=IMAGE, command_policy_sha256=POLICY, verdict=verdict,
        started_at="2026-08-15T00:00:00Z", finished_at="2026-08-15T00:01:00Z",
        exit_code=0 if verdict == "PASS" else 1, log_sha256="f" * 64,
        job_uid=f"job-{gate_id}", pod_uid=f"pod-{gate_id}", findings=findings,
    )


def inputs() -> IterationInputs:
    return IterationInputs(
        objective="Implement the accepted behavior.",
        acceptance=("AC-01",),
        ci_failures=(),
        attempts_by_root_cause={},
        budget_remaining={"turns": 4},
        validation_commands=("./scripts/quality-gate.sh",),
    )


def receipt(role: str, session: str, head: str = SHA_B) -> TurnReceipt:
    return TurnReceipt(
        session_id=session,
        thread_id=f"thread-{session}",
        turn_id=f"turn-{session}",
        role=role,
        head_sha=head,
        candidate_revision=(candidate().candidate_revision if role == "reviewer" else None),
    )


class SymphonyPhaseBridgeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.bridge = SymphonyPhaseBridge()
        self.snapshot = TaskSnapshot(
            issue_identifier="GH-123",
            state="queued",
            base_sha=SHA_A,
            head_sha=SHA_B,
            candidate_revision=candidate().to_json(),
        )

    def test_first_packet_has_no_model_invented_implementer_identity(self) -> None:
        packet = self.bridge.prepare_packet(self.snapshot, inputs())

        self.assertEqual(PHASE_IMPLEMENT, self.snapshot.iteration_phase)
        self.assertIsNone(packet.implementer_session_id)

    def test_implementation_receipt_freezes_candidate_before_separate_gate(self) -> None:
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "session-implementer"),
        )

        self.assertEqual(PHASE_VALIDATE, self.snapshot.iteration_phase)
        with self.assertRaisesRegex(PhaseBridgeError, "validate"):
            self.bridge.prepare_packet(self.snapshot, inputs())

        self.bridge.complete_validation(
            self.snapshot,
            gate("gate-1", "PASS"),
        )
        packet = self.bridge.prepare_packet(self.snapshot, inputs())
        self.assertEqual(PHASE_REVIEW, self.snapshot.iteration_phase)
        self.assertEqual("session-implementer", packet.implementer_session_id)
        self.assertEqual(
            "thread-session-implementer",
            self.snapshot.last_turn_receipt["thread_id"],
        )

    def test_gate_failure_returns_deterministic_finding_to_implementation(self) -> None:
        finding = ReviewFinding(
            root_cause_id="gate:test-failure",
            severity="high",
            evidence="The deterministic test command exited with status 1.",
            impact="The candidate cannot enter review.",
            expected_behavior="All required validation commands pass.",
            verification="Run the isolated gate again.",
        )
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "failed-session"),
        )
        self.bridge.complete_validation(
            self.snapshot,
            gate("gate-2", "FAIL", (finding,)),
        )

        packet = self.bridge.prepare_packet(self.snapshot, inputs())
        self.assertEqual(PHASE_IMPLEMENT, self.snapshot.iteration_phase)
        self.assertIsNone(self.snapshot.implementer_session_id)
        self.assertIsNone(packet.implementer_session_id)
        self.assertEqual((finding,), packet.review_findings)

    def test_stale_or_duplicate_gate_receipt_cannot_advance(self) -> None:
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "session-implementer"),
        )

        with self.assertRaisesRegex(PhaseBridgeError, "candidate revision"):
            self.bridge.complete_validation(
                self.snapshot,
                gate("gate-stale", "PASS", revision=candidate(SHA_C)),
            )

        current_gate = gate("gate-current", "PASS")
        self.bridge.complete_validation(self.snapshot, current_gate)
        self.snapshot.iteration_phase = PHASE_VALIDATE
        with self.assertRaisesRegex(PhaseBridgeError, "already consumed"):
            self.bridge.complete_validation(self.snapshot, current_gate)

    def test_gate_receipt_from_another_issue_cannot_advance(self) -> None:
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "session-implementer"),
        )
        wrong_issue = gate("gate-wrong-issue", "PASS")
        object.__setattr__(wrong_issue, "issue_identifier", "GH-999")

        with self.assertRaisesRegex(PhaseBridgeError, "task issue"):
            self.bridge.complete_validation(self.snapshot, wrong_issue)

    def test_review_pass_is_bound_to_distinct_receipt_and_exact_head(self) -> None:
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "session-implementer"),
        )
        self.bridge.complete_validation(
            self.snapshot, gate("gate-review-pass", "PASS")
        )
        decision = self.bridge.complete_review(
            self.snapshot,
            receipt("reviewer", "session-reviewer"),
            ReviewProposal("PASS", SHA_B, "spec-evaluator", (), candidate().candidate_revision),
        )

        self.assertEqual(PHASE_COMPLETE, self.snapshot.iteration_phase)
        self.assertEqual("session-reviewer", decision.reviewer_session_id)
        self.assertEqual("session-implementer", decision.implementer_session_id)
        self.assertTrue(self.snapshot.has_review_pass_for(candidate().candidate_revision))

    def test_review_fail_returns_structured_findings_to_next_implementation(self) -> None:
        finding = ReviewFinding(
            root_cause_id="acceptance:missing-test",
            severity="high",
            evidence="The acceptance rule has no executable test.",
            impact="A regression could pass the gate.",
            expected_behavior="Add a focused regression test.",
            verification="Run the focused test.",
        )
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "session-implementer"),
        )
        self.bridge.complete_validation(
            self.snapshot, gate("gate-review-fail", "PASS")
        )
        self.bridge.complete_review(
            self.snapshot,
            receipt("reviewer", "session-reviewer"),
            ReviewProposal("FAIL", SHA_B, "product-steward", (finding,), candidate().candidate_revision),
        )

        packet = self.bridge.prepare_packet(self.snapshot, inputs())
        self.assertEqual(PHASE_IMPLEMENT, self.snapshot.iteration_phase)
        self.assertIsNone(packet.implementer_session_id)
        self.assertEqual((finding,), packet.review_findings)

    def test_same_session_or_stale_head_cannot_approve(self) -> None:
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "same-session"),
        )
        self.bridge.complete_validation(
            self.snapshot, gate("gate-same-session", "PASS")
        )

        with self.assertRaisesRegex(PhaseBridgeError, "must differ"):
            self.bridge.complete_review(
                self.snapshot,
                receipt("reviewer", "same-session"),
                ReviewProposal("PASS", SHA_B, "spec-evaluator", (), candidate().candidate_revision),
            )
        with self.assertRaisesRegex(PhaseBridgeError, "candidate revision"):
            self.bridge.complete_review(
                self.snapshot,
                receipt("reviewer", "new-session"),
                ReviewProposal("PASS", SHA_B, "spec-evaluator", (), "9" * 64),
            )
        with self.assertRaisesRegex(PhaseBridgeError, "candidate head"):
            self.bridge.complete_review(
                self.snapshot,
                receipt("reviewer", "new-session"),
                ReviewProposal("PASS", SHA_C, "spec-evaluator", (), candidate().candidate_revision),
            )

    def test_new_head_invalidates_old_pass_and_phase_survives_restart(self) -> None:
        self.bridge.complete_implementation(
            self.snapshot,
            receipt("implementer", "session-implementer"),
        )
        self.bridge.complete_validation(
            self.snapshot, gate("gate-restart", "PASS")
        )
        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "snapshot.json")
            store.save(self.snapshot)
            restored = store.load()

        self.assertEqual(PHASE_REVIEW, restored.iteration_phase)
        previous_revision = candidate().candidate_revision
        restored.record_review_decision(
            ReviewDecision(
                verdict="PASS",
                head_sha=SHA_B,
                candidate_revision=previous_revision,
                reviewer_role="spec-evaluator",
                reviewer_session_id="session-reviewer",
                implementer_session_id="session-implementer",
                findings=(),
            )
        )
        self.bridge.invalidate_for_new_head(restored, SHA_C)
        self.assertEqual(PHASE_IMPLEMENT, restored.iteration_phase)
        self.assertIsNone(restored.implementer_session_id)
        self.assertTrue(restored.has_review_pass_for(previous_revision))
        self.assertFalse(restored.has_review_pass_for("9" * 64))

    def test_new_head_returns_human_review_task_to_the_gate_loop(self) -> None:
        self.snapshot.state = "human_review"
        self.snapshot.iteration_phase = PHASE_COMPLETE
        self.snapshot.handoff_head_sha = SHA_B

        self.bridge.invalidate_for_new_head(self.snapshot, SHA_C)

        self.assertEqual("queued", self.snapshot.state)
        self.assertEqual(PHASE_IMPLEMENT, self.snapshot.iteration_phase)
        self.assertEqual(SHA_C, self.snapshot.head_sha)
        self.assertIsNone(self.snapshot.handoff_head_sha)
        self.assertIsNone(self.snapshot.candidate_commit_sha)

    def test_successful_base_sync_invalidates_all_current_head_gates(self) -> None:
        self.snapshot.state = "human_review"
        self.snapshot.iteration_phase = PHASE_COMPLETE
        self.snapshot.implementer_session_id = "implementer-session"
        self.snapshot.gate_request = {"gate_id": "old-gate"}
        self.snapshot.gate_receipt = {"verdict": "PASS"}
        self.snapshot.review_workspace = "/tmp/old-review"
        self.snapshot.handoff_head_sha = SHA_B

        self.bridge.apply_base_sync(
            self.snapshot,
            BaseSyncResult(
                status="UPDATED",
                previous_base_sha=SHA_A,
                base_sha=SHA_C,
                previous_head_sha=SHA_B,
                head_sha="d" * 40,
            ),
        )

        self.assertEqual(SHA_C, self.snapshot.base_sha)
        self.assertEqual("d" * 40, self.snapshot.head_sha)
        self.assertEqual("queued", self.snapshot.state)
        self.assertEqual(PHASE_IMPLEMENT, self.snapshot.iteration_phase)
        self.assertIsNone(self.snapshot.candidate_revision)
        self.assertIsNone(self.snapshot.gate_request)
        self.assertIsNone(self.snapshot.gate_receipt)
        self.assertIsNone(self.snapshot.implementer_session_id)
        self.assertIsNone(self.snapshot.review_workspace)
        self.assertIsNone(self.snapshot.handoff_head_sha)
        self.assertEqual("UPDATED", self.snapshot.base_sync["status"])

    def test_base_conflict_is_persisted_without_accepting_the_new_base(self) -> None:
        self.snapshot.iteration_phase = PHASE_COMPLETE

        self.bridge.apply_base_sync(
            self.snapshot,
            BaseSyncResult(
                status="CONFLICT",
                previous_base_sha=SHA_A,
                base_sha=SHA_C,
                previous_head_sha=SHA_B,
                head_sha=SHA_B,
            ),
        )

        self.assertEqual(SHA_A, self.snapshot.base_sha)
        self.assertEqual(SHA_B, self.snapshot.head_sha)
        self.assertEqual("queued", self.snapshot.state)
        self.assertEqual(PHASE_IMPLEMENT, self.snapshot.iteration_phase)
        self.assertIsNone(self.snapshot.candidate_revision)
        self.assertEqual("CONFLICT", self.snapshot.base_sync["status"])
        self.assertEqual(SHA_C, self.snapshot.base_sync["base_sha"])

        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "snapshot.json")
            store.save(self.snapshot)
            restored = store.load()
        self.assertEqual(self.snapshot.base_sync, restored.base_sync)

    def test_base_sync_identity_mismatch_fails_before_snapshot_mutation(self) -> None:
        before = self.snapshot.to_json()

        with self.assertRaisesRegex(PhaseBridgeError, "differs from task identity"):
            self.bridge.apply_base_sync(
                self.snapshot,
                BaseSyncResult(
                    status="UPDATED",
                    previous_base_sha=SHA_A,
                    base_sha=SHA_C,
                    previous_head_sha="9" * 40,
                    head_sha="d" * 40,
                ),
            )

        self.assertEqual(before, self.snapshot.to_json())


if __name__ == "__main__":
    unittest.main()
