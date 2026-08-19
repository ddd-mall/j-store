from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.app_server import (  # noqa: E402
    AppServerClient,
    JsonLineTransport,
    build_implementer_thread_params,
    build_review_thread_params,
)
from agentic_cicd.protocol import (  # noqa: E402
    GATE_RECEIPT_SCHEMA,
    GATE_REQUEST_SCHEMA,
    ITERATION_PACKET_SCHEMA,
    REVIEW_DECISION_SCHEMA,
    REVIEW_PROPOSAL_SCHEMA,
    IterationPacket,
    GateReceipt,
    GateRequest,
    ReviewDecision,
    ReviewCommentFeedback,
    ReviewFinding,
    ReviewLedger,
    ReviewPacket,
    ReviewProposal,
    ReviewThreadFeedback,
    parse_review_decision,
)
from agentic_cicd.coordinator import SnapshotStore, TaskSnapshot  # noqa: E402
from agentic_cicd.candidate import CandidateRevision  # noqa: E402


SHA_A = "a" * 40
SHA_B = "b" * 40
IMAGE = "registry.example/gate@sha256:" + "d" * 64


def sample_candidate() -> CandidateRevision:
    artifact, policy = "1" * 64, "2" * 64
    return CandidateRevision(
        SHA_A, SHA_B, artifact, policy,
        CandidateRevision.calculate_revision(SHA_A, SHA_B, artifact, policy),
    )


def sample_gate_request() -> GateRequest:
    commands = ("./scripts/quality-gate.sh",)
    return GateRequest(
        gate_id="gate-123", issue_identifier="GH-123", candidate_revision=sample_candidate(),
        runner_image=IMAGE, command_policy_sha256=GateRequest.calculate_command_policy_sha256(commands),
        validation_commands=commands, timeout_seconds=600,
        requested_at="2026-08-15T00:00:00Z",
    )


def sample_gate_receipt(verdict: str = "PASS", findings: tuple[ReviewFinding, ...] = ()) -> GateReceipt:
    request = sample_gate_request()
    return GateReceipt(
        gate_id=request.gate_id, issue_identifier=request.issue_identifier,
        candidate_revision=request.candidate_revision, runner_image=request.runner_image,
        command_policy_sha256=request.command_policy_sha256, verdict=verdict,
        started_at="2026-08-15T00:00:01Z", finished_at="2026-08-15T00:00:02Z",
        exit_code=0 if verdict == "PASS" else (1 if verdict == "FAIL" else None),
        log_sha256="f" * 64, job_uid="job-uid", pod_uid="pod-uid", findings=findings,
    )


def sample_packet() -> IterationPacket:
    return IterationPacket(
        issue_identifier="GH-123",
        objective="Implement the accepted order-total behavior.",
        base_sha=SHA_A,
        head_sha=SHA_B,
        acceptance=("AC-01", "AC-02"),
        review_findings=(),
        ci_failures=(),
        attempts_by_root_cause={},
        budget_remaining={"turns": 12, "cost_microusd": 1_000_000},
        validation_commands=("./scripts/quality-gate.sh",),
        implementer_session_id="implementer-1",
    )


class ProtocolContractTest(unittest.TestCase):
    def test_review_packet_separates_actionable_and_audit_feedback(self) -> None:
        current = ReviewCommentFeedback(
            comment_id="PRRC_current",
            author_login="reviewer",
            body="Fix the current behavior.",
            commit_sha=SHA_B,
            outdated=False,
            created_at="2026-08-19T00:00:00Z",
            updated_at="2026-08-19T00:00:00Z",
        )
        historical = ReviewCommentFeedback(
            comment_id="PRRC_old",
            author_login=None,
            body="Old feedback.",
            commit_sha=SHA_A,
            outdated=True,
            created_at="2026-08-18T00:00:00Z",
            updated_at="2026-08-18T00:00:00Z",
        )
        packet = ReviewPacket(
            repository="ddd-mall/j-store",
            pull_request_number=51,
            head_sha=SHA_B,
            threads=(
                ReviewThreadFeedback(
                    thread_id="PRRT_thread",
                    path="src/example.py",
                    line=10,
                    original_line=8,
                    resolved=False,
                    classification="actionable",
                    comments=(current,),
                ),
                ReviewThreadFeedback(
                    thread_id="PRRT_thread",
                    path="src/example.py",
                    line=10,
                    original_line=8,
                    resolved=False,
                    classification="audit",
                    comments=(historical,),
                ),
            ),
        )

        self.assertEqual(packet, ReviewPacket.from_json(packet.to_json()))
        self.assertEqual(1, packet.unresolved_actionable_threads)
        self.assertEqual((current,), packet.actionable_comments)
        self.assertEqual((historical,), packet.audit_comments)

        invalid = packet.to_json()
        invalid["threads"][0]["comments"][0]["commit_sha"] = SHA_A
        with self.assertRaisesRegex(ValueError, "current head"):
            ReviewPacket.from_json(invalid)

    def test_gate_contracts_bind_exact_candidate_and_runtime_identity(self) -> None:
        request = sample_gate_request()
        pass_receipt = sample_gate_receipt()
        self.assertEqual(sample_candidate(), pass_receipt.candidate_revision)
        self.assertEqual(request, GateRequest.from_json(request.to_json()))
        self.assertEqual(pass_receipt, GateReceipt.from_json(pass_receipt.to_json()))
        Draft202012Validator(GATE_REQUEST_SCHEMA).validate(request.to_json())
        Draft202012Validator(GATE_RECEIPT_SCHEMA).validate(pass_receipt.to_json())

        changed = request.to_json()
        changed["validation_commands"] = ["./gradlew test"]
        with self.assertRaisesRegex(ValueError, "does not bind"):
            GateRequest.from_json(changed)

        with self.assertRaisesRegex(ValueError, "FAIL"):
            sample_gate_receipt("FAIL")
        with self.assertRaisesRegex(ValueError, "PASS"):
            sample_gate_receipt("PASS", (ReviewFinding("gate:unexpected", "high", "evidence", "impact", "expected", "verification"),))

    def test_infrastructure_failure_is_not_candidate_evidence(self) -> None:
        receipt = sample_gate_receipt("INFRASTRUCTURE_FAILURE")
        self.assertIsNone(receipt.exit_code)
        with self.assertRaisesRegex(ValueError, "infrastructure failure"):
            sample_gate_receipt("INFRASTRUCTURE_FAILURE", (ReviewFinding("gate:infra", "high", "evidence", "impact", "expected", "verify"),))

    def test_iteration_packet_rejects_noncanonical_identity_and_sha(self) -> None:
        payload = sample_packet().to_json()
        payload["issue_identifier"] = "../../outside"

        with self.assertRaisesRegex(ValueError, "issue identifier"):
            IterationPacket.from_json(payload)

        payload = sample_packet().to_json()
        payload["head_sha"] = "HEAD"
        with self.assertRaisesRegex(ValueError, "head_sha"):
            IterationPacket.from_json(payload)

    def test_iteration_packet_round_trips_all_planning_inputs(self) -> None:
        packet = sample_packet()

        self.assertEqual(packet, IterationPacket.from_json(packet.to_json()))
        self.assertEqual("GH-123", packet.to_json()["issue_identifier"])
        self.assertEqual(["AC-01", "AC-02"], packet.to_json()["acceptance"])
        Draft202012Validator(ITERATION_PACKET_SCHEMA).validate(packet.to_json())

    def test_first_implementation_packet_can_precede_trusted_session_receipt(self) -> None:
        payload = sample_packet().to_json()
        payload["implementer_session_id"] = None

        packet = IterationPacket.from_json(payload)

        self.assertIsNone(packet.implementer_session_id)
        Draft202012Validator(ITERATION_PACKET_SCHEMA).validate(packet.to_json())

    def test_iteration_packet_rejects_scalar_values_for_array_fields(self) -> None:
        payload = sample_packet().to_json()
        payload["acceptance"] = "AC-01"

        with self.assertRaisesRegex(ValueError, "acceptance"):
            IterationPacket.from_json(payload)

        payload = sample_packet().to_json()
        payload["ci_failures"] = {"kind": "candidate"}
        with self.assertRaisesRegex(ValueError, "ci_failures"):
            IterationPacket.from_json(payload)

    def test_review_pass_requires_an_independent_session_and_no_findings(self) -> None:
        with self.assertRaisesRegex(ValueError, "independent"):
            ReviewDecision(
                verdict="PASS",
                head_sha=SHA_B,
                reviewer_role="spec-evaluator",
                reviewer_session_id="implementer-1",
                implementer_session_id="implementer-1",
                findings=(),
                candidate_revision=sample_candidate().candidate_revision,
            )

        finding = ReviewFinding(
            root_cause_id="acceptance:missing-refund-test",
            severity="high",
            evidence="Refund acceptance has no executable test.",
            impact="A regression can pass the local gate.",
            expected_behavior="Add a regression test covering the acceptance rule.",
            verification="Run the focused refund test.",
        )
        with self.assertRaisesRegex(ValueError, "PASS"):
            ReviewDecision(
                verdict="PASS",
                head_sha=SHA_B,
                reviewer_role="spec-evaluator",
                reviewer_session_id="reviewer-1",
                implementer_session_id="implementer-1",
                findings=(finding,),
                candidate_revision=sample_candidate().candidate_revision,
            )

    def test_fail_requires_structured_findings_with_stable_root_cause(self) -> None:
        with self.assertRaisesRegex(ValueError, "finding"):
            ReviewDecision(
                verdict="FAIL",
                head_sha=SHA_B,
                reviewer_role="product-steward",
                reviewer_session_id="reviewer-1",
                implementer_session_id="implementer-1",
                findings=(),
                candidate_revision=sample_candidate().candidate_revision,
            )

    def test_review_proposal_excludes_untrusted_runtime_identity(self) -> None:
        proposal = ReviewProposal(
            verdict="PASS",
            head_sha=SHA_B,
            reviewer_role="spec-evaluator",
            findings=(),
            candidate_revision=sample_candidate().candidate_revision,
        )

        self.assertNotIn("reviewer_session_id", proposal.to_json())
        self.assertNotIn("implementer_session_id", proposal.to_json())
        Draft202012Validator(REVIEW_PROPOSAL_SCHEMA).validate(proposal.to_json())

        with self.assertRaisesRegex(ValueError, "root_cause_id"):
            ReviewFinding(
                root_cause_id="not stable!",
                severity="medium",
                evidence="evidence",
                impact="impact",
                expected_behavior="expected",
                verification="verification",
            )

    def test_pass_is_valid_only_for_the_exact_reviewed_head(self) -> None:
        ledger = ReviewLedger()
        decision = ReviewDecision(
            verdict="PASS",
            head_sha=SHA_B,
            reviewer_role="spec-evaluator",
            reviewer_session_id="reviewer-1",
            implementer_session_id="implementer-1",
            findings=(),
            candidate_revision=sample_candidate().candidate_revision,
        )

        ledger.record(decision)

        self.assertTrue(ledger.has_pass_for(sample_candidate().candidate_revision))
        self.assertFalse(ledger.has_pass_for("c" * 64))
        Draft202012Validator(REVIEW_DECISION_SCHEMA).validate(decision.to_json())

    def test_review_decision_survives_snapshot_recovery_but_not_a_new_head(self) -> None:
        snapshot = TaskSnapshot(issue_identifier="GH-123", state="queued")
        decision = ReviewDecision(
            verdict="PASS",
            head_sha=SHA_B,
            reviewer_role="spec-evaluator",
            reviewer_session_id="reviewer-1",
            implementer_session_id="implementer-1",
            findings=(),
            candidate_revision=sample_candidate().candidate_revision,
        )
        snapshot.record_review_decision(decision)

        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "task-state.json")
            store.save(snapshot)
            restored = store.load()

        self.assertTrue(restored.has_review_pass_for(sample_candidate().candidate_revision))
        self.assertFalse(restored.has_review_pass_for("c" * 64))

    def test_host_rejects_model_claiming_another_head_or_reviewer_identity(self) -> None:
        decision = ReviewDecision(
            verdict="PASS",
            head_sha=SHA_B,
            reviewer_role="spec-evaluator",
            reviewer_session_id="reviewer-claimed",
            implementer_session_id="implementer-1",
            findings=(),
            candidate_revision=sample_candidate().candidate_revision,
        ).to_json()

        with self.assertRaisesRegex(ValueError, "review session"):
            parse_review_decision(
                decision,
                expected_head_sha=SHA_B,
                expected_candidate_revision=sample_candidate().candidate_revision,
                reviewer_session_id="reviewer-actual",
                implementer_session_id="implementer-1",
            )

        decision["reviewer_session_id"] = "reviewer-actual"
        with self.assertRaisesRegex(ValueError, "head_sha"):
            parse_review_decision(
                decision,
                expected_head_sha="c" * 40,
                expected_candidate_revision=sample_candidate().candidate_revision,
                reviewer_session_id="reviewer-actual",
                implementer_session_id="implementer-1",
            )


class AppServerProtocolTest(unittest.TestCase):
    def test_initialize_uses_required_handshake_and_correlates_response(self) -> None:
        incoming = io.StringIO(
            json.dumps({"method": "server/notice", "params": {}})
            + "\n"
            + json.dumps(
                {
                    "id": 1,
                    "result": {
                        "codexHome": "/tmp/codex-home",
                        "platformFamily": "unix",
                        "platformOs": "linux",
                        "userAgent": "codex-cli/0.146.0",
                    },
                }
            )
            + "\n"
        )
        outgoing = io.StringIO()
        client = AppServerClient(JsonLineTransport(incoming, outgoing))

        result = client.initialize()

        sent = [json.loads(line) for line in outgoing.getvalue().splitlines()]
        self.assertEqual("initialize", sent[0]["method"])
        self.assertEqual("j_store_agentic_cicd", sent[0]["params"]["clientInfo"]["name"])
        self.assertEqual({"method": "initialized", "params": {}}, sent[1])
        self.assertEqual("linux", result["platformOs"])
        self.assertEqual(1, len(client.notifications))

    def test_role_params_separate_workspace_writer_from_read_only_reviewer(self) -> None:
        workspace = Path("/tmp/workspaces/GH-123")

        implementer = build_implementer_thread_params(workspace)
        reviewer = build_review_thread_params(workspace)

        self.assertEqual("workspace-write", implementer["sandbox"])
        self.assertEqual("read-only", reviewer["sandbox"])
        self.assertTrue(implementer["ephemeral"])
        self.assertTrue(reviewer["ephemeral"])
        self.assertEqual("never", reviewer["approvalPolicy"])
        self.assertNotEqual(
            implementer["developerInstructions"], reviewer["developerInstructions"]
        )

    def test_turn_uses_structured_packet_and_review_output_schema(self) -> None:
        incoming = io.StringIO(
            json.dumps(
                {
                    "id": 1,
                    "result": {
                        "codexHome": "/tmp/codex-home",
                        "platformFamily": "unix",
                        "platformOs": "linux",
                        "userAgent": "codex-cli/0.146.0",
                    },
                }
            )
            + "\n"
            + json.dumps({"id": 2, "result": {"turn": {"id": "turn-1"}}})
            + "\n"
        )
        outgoing = io.StringIO()
        client = AppServerClient(JsonLineTransport(incoming, outgoing))

        client.initialize()
        result = client.start_review_turn("thread-review-1", sample_packet())

        request = json.loads(outgoing.getvalue().splitlines()[2])
        self.assertEqual("turn/start", request["method"])
        self.assertEqual("thread-review-1", request["params"]["threadId"])
        self.assertEqual("readOnly", request["params"]["sandboxPolicy"]["type"])
        self.assertEqual("object", request["params"]["outputSchema"]["type"])
        packet = json.loads(request["params"]["input"][0]["text"])
        self.assertEqual(SHA_B, packet["head_sha"])
        self.assertEqual("turn-1", result["turn"]["id"])

    def test_review_turn_requires_host_captured_implementer_identity(self) -> None:
        payload = sample_packet().to_json()
        payload["implementer_session_id"] = None
        packet = IterationPacket.from_json(payload)
        client = AppServerClient(JsonLineTransport(io.StringIO(), io.StringIO()))
        client.initialized = True

        with self.assertRaisesRegex(ValueError, "trusted implementer"):
            client.start_review_turn("thread-review-1", packet)

    def test_runtime_lock_accepts_stable_codex_cli_and_pins_protocol(self) -> None:
        lock = json.loads(
            (REPO_ROOT / "config" / "agentic-cicd" / "codex-app-server.lock.json").read_text(
                encoding="utf-8"
            )
        )

        self.assertEqual("installed-stable", lock["version_policy"])
        self.assertNotIn("codex_cli_version", lock)
        self.assertEqual("v2", lock["protocol_version"])
        self.assertEqual("stdio-jsonl", lock["transport"])


if __name__ == "__main__":
    unittest.main()
