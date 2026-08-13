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
    ITERATION_PACKET_SCHEMA,
    REVIEW_DECISION_SCHEMA,
    IterationPacket,
    ReviewDecision,
    ReviewFinding,
    ReviewLedger,
    parse_review_decision,
)
from agentic_cicd.coordinator import SnapshotStore, TaskSnapshot  # noqa: E402


SHA_A = "a" * 40
SHA_B = "b" * 40


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
            )

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
        )

        ledger.record(decision)

        self.assertTrue(ledger.has_pass_for(SHA_B))
        self.assertFalse(ledger.has_pass_for("c" * 40))
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
        )
        snapshot.record_review_decision(decision)

        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "task-state.json")
            store.save(snapshot)
            restored = store.load()

        self.assertTrue(restored.has_review_pass_for(SHA_B))
        self.assertFalse(restored.has_review_pass_for("c" * 40))

    def test_host_rejects_model_claiming_another_head_or_reviewer_identity(self) -> None:
        decision = ReviewDecision(
            verdict="PASS",
            head_sha=SHA_B,
            reviewer_role="spec-evaluator",
            reviewer_session_id="reviewer-claimed",
            implementer_session_id="implementer-1",
            findings=(),
        ).to_json()

        with self.assertRaisesRegex(ValueError, "review session"):
            parse_review_decision(
                decision,
                expected_head_sha=SHA_B,
                reviewer_session_id="reviewer-actual",
                implementer_session_id="implementer-1",
            )

        decision["reviewer_session_id"] = "reviewer-actual"
        with self.assertRaisesRegex(ValueError, "head_sha"):
            parse_review_decision(
                decision,
                expected_head_sha="c" * 40,
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

    def test_runtime_lock_pins_codex_cli_and_protocol(self) -> None:
        lock = json.loads(
            (REPO_ROOT / "config" / "agentic-cicd" / "codex-app-server.lock.json").read_text(
                encoding="utf-8"
            )
        )

        self.assertEqual("0.146.0", lock["codex_cli_version"])
        self.assertEqual("v2", lock["protocol_version"])
        self.assertEqual("stdio-jsonl", lock["transport"])


if __name__ == "__main__":
    unittest.main()
