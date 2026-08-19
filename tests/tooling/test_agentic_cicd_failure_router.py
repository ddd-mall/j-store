from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.coordinator import (  # noqa: E402
    Coordinator,
    SnapshotStore,
    TaskSnapshot,
)
from agentic_cicd.failure_router import (  # noqa: E402
    FailureEvidence,
    FailureRoute,
    FailureRouter,
)


HEAD_SHA = "a" * 40
BASE_SHA = "b" * 40
CONTRACT_PATH = REPO_ROOT / "config" / "agentic-cicd" / "state-contract.json"


def evidence(
    event_id: str,
    *,
    root_cause_id: str = "ci:quality",
    source_kind: str = "ci",
    current_conclusion: str = "FAILURE",
    baseline_conclusion: str = "SUCCESS",
    same_head_conclusions: tuple[str, ...] = ("FAILURE",),
    infrastructure_category: str | None = None,
    human_decision_category: str | None = None,
    base_sha: str = BASE_SHA,
    head_sha: str = HEAD_SHA,
) -> FailureEvidence:
    return FailureEvidence(
        event_id=event_id,
        root_cause_id=root_cause_id,
        source_kind=source_kind,
        base_sha=base_sha,
        head_sha=head_sha,
        current_conclusion=current_conclusion,
        baseline_conclusion=baseline_conclusion,
        same_head_conclusions=same_head_conclusions,
        infrastructure_category=infrastructure_category,
        human_decision_category=human_decision_category,
    )


class FailureRouterTest(unittest.TestCase):
    def setUp(self) -> None:
        contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
        self.coordinator = Coordinator.from_contract(contract)
        self.router = FailureRouter(self.coordinator)
        self.snapshot = TaskSnapshot(
            issue_identifier="GH-50",
            state="waiting_ci",
            base_sha=BASE_SHA,
            head_sha=HEAD_SHA,
            candidate_revision={"candidate_revision": "c" * 64},
            claim_id="run-1",
            iteration_phase="complete",
        )

    def test_candidate_failure_returns_to_implementation_once_per_event(self) -> None:
        first = self.router.route(self.snapshot, evidence("check-run:1"))
        replay = self.router.route(self.snapshot, evidence("check-run:1"))

        self.assertEqual(
            FailureRoute(
                category="candidate",
                action="return_to_implementation",
                root_cause_id="ci:quality",
                reason="candidate-failure:ci:quality",
            ),
            first,
        )
        self.assertEqual(first, replay)
        self.assertEqual("queued", self.snapshot.state)
        self.assertEqual("implement", self.snapshot.iteration_phase)
        self.assertIsNone(self.snapshot.claim_id)
        self.assertEqual(
            ["c" * 64], self.snapshot.semantic_fix_strategies["ci:quality"]
        )
        self.assertEqual(1, len(self.snapshot.failure_routes))

    def test_third_distinct_candidate_strategy_fuses_without_extra_retry(self) -> None:
        for index, revision in enumerate(("c" * 64, "d" * 64, "e" * 64), start=1):
            self.snapshot.state = "waiting_ci"
            self.snapshot.claim_id = f"run-{index}"
            self.snapshot.candidate_revision = {"candidate_revision": revision}
            result = self.router.route(
                self.snapshot, evidence(f"check-run:{index}")
            )

        self.assertEqual("candidate", result.category)
        self.assertEqual("fused", result.action)
        self.assertEqual("fused", self.snapshot.state)
        self.assertEqual(
            ["c" * 64, "d" * 64],
            self.snapshot.semantic_fix_strategies["ci:quality"],
        )

    def test_same_candidate_strategy_is_blocked_as_no_progress(self) -> None:
        self.router.route(self.snapshot, evidence("check-run:1"))
        self.snapshot.state = "waiting_ci"
        self.snapshot.claim_id = "run-2"
        self.snapshot.candidate_revision = {"candidate_revision": "c" * 64}

        result = self.router.route(self.snapshot, evidence("check-run:2"))

        self.assertEqual("blocked_no_progress", result.action)
        self.assertEqual("blocked", self.snapshot.state)
        self.assertEqual("no-new-strategy:ci:quality", self.snapshot.blocked_reason)

    def test_infrastructure_and_flaky_use_independent_budgets(self) -> None:
        infrastructure = self.router.route(
            self.snapshot,
            evidence("check-run:infra", infrastructure_category="runner"),
        )
        self.assertEqual("infrastructure", infrastructure.category)
        self.assertEqual("retry_infrastructure", infrastructure.action)
        self.assertEqual(1, self.snapshot.infrastructure_retries)
        self.assertEqual({}, self.snapshot.flaky_reruns)
        self.assertEqual({}, self.snapshot.semantic_fix_strategies)

        flaky = self.router.route(
            self.snapshot,
            evidence(
                "check-run:flaky-1",
                same_head_conclusions=("FAILURE", "SUCCESS"),
            ),
        )
        self.assertEqual("flaky", flaky.category)
        self.assertEqual("await_authorized_rerun", flaky.action)
        self.assertEqual({"ci:quality": 1}, self.snapshot.flaky_reruns)
        self.assertEqual(1, self.snapshot.infrastructure_retries)

        limited = self.router.route(
            self.snapshot,
            evidence(
                "check-run:flaky-2",
                same_head_conclusions=("SUCCESS", "FAILURE"),
            ),
        )
        self.assertEqual("blocked", limited.action)
        self.assertEqual("blocked", self.snapshot.state)
        self.assertEqual(
            "flaky-rerun-limit:ci:quality", self.snapshot.blocked_reason
        )

    def test_baseline_and_human_decisions_block_without_retry(self) -> None:
        baseline = self.router.route(
            self.snapshot,
            evidence("check-run:baseline", baseline_conclusion="FAILURE"),
        )
        self.assertEqual("baseline", baseline.category)
        self.assertEqual("blocked", baseline.action)
        self.assertEqual("baseline-failure:ci:quality", self.snapshot.blocked_reason)

        for category in ("requirement", "permission"):
            with self.subTest(category=category):
                snapshot = TaskSnapshot(
                    issue_identifier="GH-50",
                    state="waiting_ci",
                    base_sha=BASE_SHA,
                    head_sha=HEAD_SHA,
                )
                result = self.router.route(
                    snapshot,
                    evidence(
                        f"review:{category}",
                        root_cause_id=f"review:{category}",
                        source_kind="review",
                        baseline_conclusion="NOT_APPLICABLE",
                        human_decision_category=category,
                    ),
                )
                self.assertEqual("requirement_permission", result.category)
                self.assertEqual("blocked", result.action)
                self.assertIn(category, snapshot.blocked_reason)
                self.assertEqual({}, snapshot.semantic_fix_strategies)

    def test_baseline_failure_precedes_flaky_and_review_is_never_flaky(self) -> None:
        for conclusion in ("FAILURE", "CONFLICT"):
            with self.subTest(baseline=conclusion):
                snapshot = TaskSnapshot(
                    issue_identifier="GH-50",
                    state="waiting_ci",
                    base_sha=BASE_SHA,
                    head_sha=HEAD_SHA,
                )
                baseline = self.router.route(
                    snapshot,
                    evidence(
                        f"check-run:baseline-flaky:{conclusion}",
                        baseline_conclusion=conclusion,
                        same_head_conclusions=("SUCCESS", "FAILURE"),
                    ),
                )
                self.assertEqual("baseline", baseline.category)
                self.assertEqual({}, snapshot.flaky_reruns)

        review_snapshot = TaskSnapshot(
            issue_identifier="GH-50",
            state="waiting_ci",
            base_sha=BASE_SHA,
            head_sha=HEAD_SHA,
            candidate_revision={"candidate_revision": "c" * 64},
        )
        review = self.router.route(
            review_snapshot,
            evidence(
                "review:changed",
                root_cause_id="review:changed",
                source_kind="review",
                baseline_conclusion="NOT_APPLICABLE",
                same_head_conclusions=("SUCCESS", "FAILURE"),
            ),
        )
        self.assertEqual("candidate", review.category)
        self.assertEqual({}, review_snapshot.flaky_reruns)

    def test_failure_event_identity_includes_base_sha(self) -> None:
        first = self.router.route(
            self.snapshot,
            evidence(
                "check-run:stable-event",
                same_head_conclusions=("SUCCESS", "FAILURE"),
            ),
        )
        self.assertEqual("await_authorized_rerun", first.action)

        advanced_base = "d" * 40
        self.snapshot.state = "waiting_ci"
        self.snapshot.base_sha = advanced_base
        second = self.router.route(
            self.snapshot,
            evidence(
                "check-run:stable-event",
                base_sha=advanced_base,
                same_head_conclusions=("SUCCESS", "FAILURE"),
            ),
        )

        self.assertEqual("blocked", second.action)
        self.assertEqual(2, len(self.snapshot.failure_routes))

        with self.assertRaises(RuntimeError):
            self.router.route(
                self.snapshot,
                evidence(
                    "check-run:stable-event",
                    same_head_conclusions=("SUCCESS", "FAILURE"),
                ),
            )

    def test_review_candidate_feedback_does_not_require_a_baseline_run(self) -> None:
        result = self.router.route(
            self.snapshot,
            evidence(
                "review:thread-1",
                root_cause_id="review:thread-1",
                source_kind="review",
                baseline_conclusion="NOT_APPLICABLE",
            ),
        )

        self.assertEqual("candidate", result.category)
        self.assertEqual("return_to_implementation", result.action)

    def test_incomplete_or_contradictory_evidence_does_not_mutate_state(self) -> None:
        for invalid in (
            evidence(
                "pending",
                current_conclusion="PENDING",
                same_head_conclusions=("PENDING",),
            ),
            evidence("unknown-base", baseline_conclusion="UNKNOWN"),
            evidence(
                "conflicting-categories",
                infrastructure_category="network",
                human_decision_category="permission",
            ),
        ):
            with self.subTest(event=invalid.event_id):
                snapshot = TaskSnapshot(
                    issue_identifier="GH-50",
                    state="waiting_ci",
                    base_sha=BASE_SHA,
                    head_sha=HEAD_SHA,
                )
                with self.assertRaises(ValueError):
                    self.router.route(snapshot, invalid)
                self.assertEqual("waiting_ci", snapshot.state)
                self.assertEqual({}, snapshot.failure_routes)

    def test_failure_route_survives_restart_without_reconsuming_budget(self) -> None:
        result = self.router.route(
            self.snapshot,
            evidence("check-run:flaky", same_head_conclusions=("SUCCESS", "FAILURE")),
        )

        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "GH-50.json")
            store.save(self.snapshot)
            restored = store.load()

        replay = self.router.route(
            restored,
            evidence("check-run:flaky", same_head_conclusions=("SUCCESS", "FAILURE")),
        )
        self.assertEqual(result, replay)
        self.assertEqual({"ci:quality": 1}, restored.flaky_reruns)
        self.assertEqual(result, FailureRoute.from_json(result.to_json()))


if __name__ == "__main__":
    unittest.main()
