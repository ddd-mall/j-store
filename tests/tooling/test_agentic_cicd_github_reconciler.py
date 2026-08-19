from __future__ import annotations

import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.coordinator import SnapshotStore, TaskSnapshot  # noqa: E402
from agentic_cicd.capabilities import validate_capabilities  # noqa: E402
from agentic_cicd.github_reconciler import (  # noqa: E402
    GitHubEventReceipt,
    GitHubReconciler,
    PullRequestState,
)
from agentic_cicd.pr_packet import AcceptanceCriterion, PullRequestPacket  # noqa: E402
from agentic_cicd.protocol import ReviewDecision  # noqa: E402


HEAD_SHA = "a" * 40
CANDIDATE_REVISION = "b" * 64
TEST_PACKET = PullRequestPacket(
    issue_identifier="GH-50",
    issue_title="Bounded test task",
    candidate_revision=CANDIDATE_REVISION,
    promoted_head_sha=HEAD_SHA,
    target_branch="develop",
    source_branch="codex/gh-50-doc-entry",
    intent="Exercise deterministic readiness.",
    in_scope="GitHub reconciler tests.",
    out_of_scope="Remote writes.",
    acceptance=(
        AcceptanceCriterion("AC-TEST-01", "Readiness is deterministic.", "test-command"),
    ),
    validation_commands=("test-command",),
    gate_id="gate-test",
    gate_log_sha256="1" * 64,
    compatibility="Internal test contract only.",
    recovery="Recreate the in-memory fixture.",
    reviewer_role="product-steward",
    reviewer_session_id="reviewer-session",
    required_human_approvals=("None",),
    residual_risks=("No unaccepted residual risk.",),
    skipped_checks=("No skipped checks.",),
)
PR_BODY = TEST_PACKET.render()


def capabilities(**overrides: bool) -> dict[str, bool]:
    values = {
        "create_remote_branch": True,
        "push_commit": True,
        "create_draft_pull_request": True,
        "write_issue_comment": True,
        "update_issue_labels": True,
        "mark_pull_request_ready": True,
        "request_pull_request_review": True,
    }
    values.update(overrides)
    return values


def accepted_snapshot() -> TaskSnapshot:
    snapshot = TaskSnapshot(
        issue_identifier="GH-50",
        state="queued",
        base_sha="c" * 40,
        head_sha=HEAD_SHA,
        branch="codex/gh-50-doc-entry",
        candidate_revision={
            "candidate_revision": CANDIDATE_REVISION,
            "base_sha": "c" * 40,
            "tree_sha": "d" * 40,
            "artifact_sha256": "e" * 64,
            "snapshot_policy_sha256": "f" * 64,
        },
        pull_request_packet=TEST_PACKET.to_json(),
    )
    snapshot.record_review_decision(
        ReviewDecision(
            verdict="PASS",
            head_sha=HEAD_SHA,
            candidate_revision=CANDIDATE_REVISION,
            reviewer_role="product-steward",
            reviewer_session_id="reviewer-session",
            implementer_session_id="implementer-session",
            findings=(),
        )
    )
    return snapshot


def ready_pr(**changes) -> PullRequestState:
    checks = {
        "branch-policy": "SUCCESS",
        "quality": "SUCCESS",
        "static-analysis": "SUCCESS",
        "dependency-vulnerability-scan": "SUCCESS",
        "dependency-license-audit": "SUCCESS",
        "secret-scan": "SUCCESS",
        "qodana": "SUCCESS",
    }
    pull_request = PullRequestState(
        number=51,
        base_branch="develop",
        head_branch="codex/gh-50-doc-entry",
        head_sha=HEAD_SHA,
        draft=True,
        body=PR_BODY,
        checks=checks,
        unresolved_review_threads=0,
    )
    return replace(pull_request, **changes)


class FakeGitHubClient:
    def __init__(self, pull_requests=()):
        self.pull_requests = list(pull_requests)
        self.list_calls = 0
        self.created = 0
        self.ready_calls = 0
        self.workpad_calls = 0
        self.label_calls = 0
        self.review_request_calls = 0
        self.failures: set[str] = set()

    def list_open_pull_requests(self, repository, head_branch):
        self.list_calls += 1
        return tuple(self.pull_requests)

    def get_pull_request(self, repository, pull_request_number):
        return next(
            pull_request
            for pull_request in self.pull_requests
            if pull_request.number == pull_request_number
        )

    def create_draft_pull_request(self, repository, base_branch, head_branch, title, body):
        self.created += 1
        pull_request = ready_pr(number=70, body=body)
        self.pull_requests.append(pull_request)
        return pull_request

    def convert_pull_request_to_draft(
        self, repository, pull_request_number, expected_head_sha
    ):
        pull_request = self.get_pull_request(repository, pull_request_number)
        converted = replace(pull_request, draft=True)
        self.pull_requests[self.pull_requests.index(pull_request)] = converted
        return converted

    def reconcile_pull_request_body(
        self, repository, pull_request_number, expected_head_sha, body
    ):
        pull_request = self.get_pull_request(repository, pull_request_number)
        updated = replace(pull_request, body=body)
        self.pull_requests[self.pull_requests.index(pull_request)] = updated
        return updated

    def mark_pull_request_ready(
        self, repository, pull_request_number, head_sha, body
    ):
        self.ready_calls += 1
        return GitHubEventReceipt(
            operation="ready",
            repository=repository,
            resource_kind="pull_request",
            resource_id="PR_node_51",
            state="ready",
            source="mutation",
            pull_request_number=pull_request_number,
            head_sha=head_sha,
        )

    def upsert_workpad(self, repository, issue_number, marker, body):
        self.workpad_calls += 1
        if "workpad" in self.failures:
            raise RuntimeError("workpad unavailable")
        return GitHubEventReceipt(
            operation="workpad",
            repository=repository,
            resource_kind="issue_comment",
            resource_id="9001",
            state="current",
            source="mutation",
            issue_number=issue_number,
            detail="2026-08-19T00:00:00Z",
        )

    def replace_issue_state_label(self, repository, issue_number, label):
        self.label_calls += 1
        if "label" in self.failures:
            raise RuntimeError("label unavailable")
        return GitHubEventReceipt(
            operation="label",
            repository=repository,
            resource_kind="issue",
            resource_id=str(issue_number),
            state="applied",
            source="mutation",
            issue_number=issue_number,
            detail=label,
        )

    def request_pull_request_review(
        self, repository, pull_request_number, head_sha, reviewer
    ):
        self.review_request_calls += 1
        if "review" in self.failures:
            raise RuntimeError("review request unavailable")
        return GitHubEventReceipt(
            operation="review-request",
            repository=repository,
            resource_kind="pull_request",
            resource_id=str(pull_request_number),
            state="requested",
            source="mutation",
            pull_request_number=pull_request_number,
            head_sha=head_sha,
            detail=reviewer,
        )


class GitHubReconcilerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.snapshot = accepted_snapshot()
        self.client = FakeGitHubClient()
        self.reconciler = GitHubReconciler(
            self.client,
            capabilities=capabilities(),
            required_checks={
                "branch-policy",
                "quality",
                "static-analysis",
                "dependency-vulnerability-scan",
                "dependency-license-audit",
                "secret-scan",
            },
        )

    def test_repeated_draft_reconcile_reuses_the_only_open_pull_request(self) -> None:
        first = self.reconciler.ensure_draft_pull_request(
            self.snapshot,
            repository="ddd-mall/j-store",
            title="docs: clarify local development",
            body=PR_BODY,
        )
        second = self.reconciler.ensure_draft_pull_request(
            self.snapshot,
            repository="ddd-mall/j-store",
            title="docs: clarify local development",
            body=PR_BODY,
        )

        self.assertEqual(70, first.number)
        self.assertEqual(first, second)
        self.assertEqual(1, self.client.created)
        self.assertEqual(70, self.snapshot.pull_request_number)
        self.assertEqual("waiting_ci", self.snapshot.state)

    def test_multiple_open_pull_requests_fail_closed(self) -> None:
        self.client.pull_requests = [ready_pr(number=51), ready_pr(number=52)]

        with self.assertRaisesRegex(RuntimeError, "multiple open pull requests"):
            self.reconciler.ensure_draft_pull_request(
                self.snapshot,
                repository="ddd-mall/j-store",
                title="docs: clarify local development",
                body=PR_BODY,
            )

    def test_draft_reconcile_rejects_non_candidate_states_before_github_calls(self) -> None:
        for state in ("blocked", "fused", "cancelled", "human_review"):
            with self.subTest(state=state):
                snapshot = accepted_snapshot()
                snapshot.state = state
                client = FakeGitHubClient()
                reconciler = GitHubReconciler(
                    client,
                    capabilities=capabilities(),
                    required_checks=self.reconciler.required_checks,
                )

                with self.assertRaisesRegex(RuntimeError, f"task state {state}"):
                    reconciler.ensure_draft_pull_request(
                        snapshot,
                        repository="ddd-mall/j-store",
                        title="docs: clarify local development",
                        body=PR_BODY,
                    )

                self.assertEqual(0, client.list_calls)
                self.assertEqual(0, client.created)
                self.assertEqual(state, snapshot.state)

    def test_draft_creation_requires_exact_candidate_pass_and_capabilities(self) -> None:
        self.snapshot.review_decisions = {}
        with self.assertRaisesRegex(RuntimeError, "independent review PASS"):
            self.reconciler.ensure_draft_pull_request(
                self.snapshot,
                repository="ddd-mall/j-store",
                title="docs: clarify local development",
                body=PR_BODY,
            )

        self.snapshot = accepted_snapshot()
        reconciler = GitHubReconciler(
            self.client,
            capabilities=capabilities(create_draft_pull_request=False),
            required_checks=self.reconciler.required_checks,
        )
        with self.assertRaisesRegex(RuntimeError, "create_draft_pull_request"):
            reconciler.ensure_draft_pull_request(
                self.snapshot,
                repository="ddd-mall/j-store",
                title="docs: clarify local development",
                body=PR_BODY,
            )

    def test_readiness_requires_current_head_checks_threads_review_and_complete_body(self) -> None:
        cases = (
            (ready_pr(head_sha="9" * 40), "head SHA"),
            (ready_pr(checks={"branch-policy": "SUCCESS"}), "required checks"),
            (
                ready_pr(checks={**ready_pr().checks, "quality": "FAILURE"}),
                "required checks",
            ),
            (
                ready_pr(checks={**ready_pr().checks, "optional": "IN_PROGRESS"}),
                "additional checks",
            ),
            (ready_pr(unresolved_review_threads=1), "review threads"),
            (ready_pr(body="## Intent\n\n- [ ] unfinished"), "PR body"),
        )

        for pull_request, expected in cases:
            with self.subTest(expected=expected):
                reasons = self.reconciler.readiness_blockers(self.snapshot, pull_request)
                self.assertTrue(any(expected in reason for reason in reasons), reasons)

    def test_readiness_rejects_review_pass_whose_head_does_not_match_candidate(self) -> None:
        self.snapshot.review_decisions[CANDIDATE_REVISION]["head_sha"] = "9" * 40

        reasons = self.reconciler.readiness_blockers(self.snapshot, ready_pr())

        self.assertTrue(
            any("independent review PASS" in reason for reason in reasons), reasons
        )

    def test_ready_transition_is_exact_head_and_idempotent(self) -> None:
        pull_request = ready_pr()

        first = self.reconciler.mark_ready(
            self.snapshot, repository="ddd-mall/j-store", pull_request=pull_request
        )
        second = self.reconciler.mark_ready(
            self.snapshot,
            repository="ddd-mall/j-store",
            pull_request=replace(pull_request, draft=False),
        )

        self.assertEqual("PR_node_51", first.resource_id)
        self.assertEqual(first, second)
        self.assertEqual(1, self.client.ready_calls)

    def test_stale_ready_receipt_does_not_override_current_draft_state(self) -> None:
        pull_request = ready_pr()
        self.reconciler.mark_ready(
            self.snapshot,
            repository="ddd-mall/j-store",
            pull_request=pull_request,
        )

        second = self.reconciler.mark_ready(
            self.snapshot,
            repository="ddd-mall/j-store",
            pull_request=pull_request,
        )

        self.assertEqual("mutation", second.source)
        self.assertEqual(2, self.client.ready_calls)

    def test_ready_rejects_cross_head_receipt_before_snapshot_persistence(self) -> None:
        self.client.mark_pull_request_ready = lambda repository, number, head, body: GitHubEventReceipt(
            operation="ready",
            repository=repository,
            resource_kind="pull_request",
            resource_id="PR_node_51",
            state="ready",
            source="mutation",
            pull_request_number=number,
            head_sha="9" * 40,
        )

        with self.assertRaisesRegex(RuntimeError, "receipt.*head"):
            self.reconciler.mark_ready(
                self.snapshot,
                repository="ddd-mall/j-store",
                pull_request=ready_pr(),
            )

        self.assertNotIn(
            f"ready:ddd-mall/j-store:51:{HEAD_SHA}", self.snapshot.github_events
        )

    def test_handoff_rejects_cross_signal_receipt_before_snapshot_persistence(self) -> None:
        self.client.upsert_workpad = lambda repository, number, marker, body: GitHubEventReceipt(
            operation="label",
            repository=repository,
            resource_kind="issue",
            resource_id=str(number),
            state="applied",
            source="observation",
            issue_number=number,
            detail="agent:human-review",
        )

        result = self.reconciler.handoff(
            self.snapshot,
            repository="ddd-mall/j-store",
            issue_number=50,
            pull_request=ready_pr(draft=False),
            workpad_body="ready for human review",
            reviewer="maintainer",
        )

        self.assertIn("workpad", result.failures)
        self.assertNotIn(
            f"handoff:ddd-mall/j-store:51:{HEAD_SHA}:workpad",
            self.snapshot.github_events,
        )

    def test_handoff_completes_after_one_signal_and_retries_failed_enhancements(self) -> None:
        pull_request = ready_pr(draft=False)
        self.client.failures = {"workpad", "review"}

        first = self.reconciler.handoff(
            self.snapshot,
            repository="ddd-mall/j-store",
            issue_number=50,
            pull_request=pull_request,
            workpad_body="ready for human review",
            reviewer="maintainer",
        )
        self.client.failures.clear()
        second = self.reconciler.handoff(
            self.snapshot,
            repository="ddd-mall/j-store",
            issue_number=50,
            pull_request=pull_request,
            workpad_body="ready for human review",
            reviewer="maintainer",
        )

        self.assertTrue(first.complete)
        self.assertEqual({"workpad", "review-request"}, set(first.failures))
        self.assertTrue(second.complete)
        self.assertEqual({}, second.failures)
        self.assertEqual(2, self.client.workpad_calls)
        self.assertEqual(1, self.client.label_calls)
        self.assertEqual(2, self.client.review_request_calls)

    def test_handoff_remains_pending_when_every_enabled_signal_fails(self) -> None:
        self.client.failures = {"workpad", "label", "review"}

        result = self.reconciler.handoff(
            self.snapshot,
            repository="ddd-mall/j-store",
            issue_number=50,
            pull_request=ready_pr(draft=False),
            workpad_body="ready for human review",
            reviewer="maintainer",
        )

        self.assertFalse(result.complete)
        self.assertEqual(
            {"workpad", "label", "review-request"}, set(result.failures)
        )

    def test_handoff_never_runs_before_pull_request_is_ready(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "must already be Ready"):
            self.reconciler.handoff(
                self.snapshot,
                repository="ddd-mall/j-store",
                issue_number=50,
                pull_request=ready_pr(draft=True),
                workpad_body="ready for human review",
                reviewer="maintainer",
            )

    def test_handoff_events_and_retry_findings_survive_snapshot_restart(self) -> None:
        self.client.failures = {"review"}
        self.reconciler.handoff(
            self.snapshot,
            repository="ddd-mall/j-store",
            issue_number=50,
            pull_request=ready_pr(draft=False),
            workpad_body="ready for human review",
            reviewer="maintainer",
        )

        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "GH-50.json")
            store.save(self.snapshot)
            restored = store.load()

        self.assertEqual(self.snapshot.github_events, restored.github_events)
        self.assertEqual(
            self.snapshot.github_operational_findings,
            restored.github_operational_findings,
        )
        self.assertEqual(HEAD_SHA, restored.handoff_head_sha)
        for receipt in restored.github_events.values():
            self.assertIsInstance(receipt, dict)

    def test_snapshot_restart_rejects_legacy_or_malformed_event_receipts(self) -> None:
        payload = self.snapshot.to_json()
        payload["github_events"] = {"ready:invalid": "locally-invented-event-id"}

        with self.assertRaisesRegex(ValueError, "receipt must be a JSON object"):
            TaskSnapshot.from_json(payload)


class RemoteCapabilityLevelTest(unittest.TestCase):
    def setUp(self) -> None:
        self.capabilities = {
            "read_only_observation": True,
            "bootstrap_local_workspace": True,
            "local_workspace_write": True,
            "freeze_local_candidate": True,
            "run_isolated_gate": True,
            "create_remote_branch": True,
            "push_commit": True,
            "create_draft_pull_request": True,
            "write_issue_comment": True,
            "update_issue_labels": True,
            "mark_pull_request_ready": True,
            "request_pull_request_review": True,
            "auto_approve": False,
            "auto_merge": False,
            "auto_release": False,
            "production_write": False,
        }

    def test_level_two_enables_the_complete_remote_handoff_without_terminal_actions(self) -> None:
        self.assertEqual([], validate_capabilities(2, self.capabilities))

    def test_level_two_allows_least_privilege_staged_profiles(self) -> None:
        for capability in (
            "write_issue_comment",
            "update_issue_labels",
            "mark_pull_request_ready",
            "request_pull_request_review",
        ):
            self.capabilities[capability] = False

        self.assertEqual([], validate_capabilities(2, self.capabilities))

    def test_level_two_rejects_remote_capabilities_without_dependencies(self) -> None:
        self.capabilities["push_commit"] = False

        failures = validate_capabilities(2, self.capabilities)

        self.assertTrue(any("requires push_commit" in failure for failure in failures))

    def test_level_two_still_rejects_merge_release_and_production(self) -> None:
        self.capabilities["auto_merge"] = True

        failures = validate_capabilities(2, self.capabilities)

        self.assertTrue(any("terminal" in failure for failure in failures))


if __name__ == "__main__":
    unittest.main()
