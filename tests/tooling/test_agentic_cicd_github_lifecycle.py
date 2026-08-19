from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.candidate import CandidateSnapshotter  # noqa: E402
from agentic_cicd.coordinator import (  # noqa: E402
    Coordinator,
    CoordinatorLimits,
    SnapshotStore,
    TaskSnapshot,
)
from agentic_cicd.failure_router import FailureRouter  # noqa: E402
from agentic_cicd.github_adapter import GitHubAdapterError  # noqa: E402
from agentic_cicd.github_lifecycle import (  # noqa: E402
    CandidatePromoter,
    GitHubLifecycleController,
)
from agentic_cicd.github_reconciler import PullRequestState  # noqa: E402
from agentic_cicd.github_receipt import GitHubEventReceipt  # noqa: E402
from agentic_cicd.pr_packet import AcceptanceCriterion, TaskBrief  # noqa: E402
from agentic_cicd.protocol import (  # noqa: E402
    GateReceipt,
    GateRequest,
    ReviewCommentFeedback,
    ReviewDecision,
    ReviewPacket,
    ReviewThreadFeedback,
)
from agentic_cicd.workspace import BaseSyncResult  # noqa: E402


REPOSITORY = "ddd-mall/j-store"
PR_BODY = """## Intent

Promote the exact reviewed candidate.

## Branch policy

- [x] Targets develop.

## Evidence

- [x] Exact candidate gates passed.

## Independent review

- [x] Independent reviewer passed the exact candidate.

## Residual risk

No unaccepted residual risk.
"""
REQUIRED_CHECKS = {
    "branch-policy",
    "quality",
    "static-analysis",
    "dependency-vulnerability-scan",
    "dependency-license-audit",
    "secret-scan",
}


class InjectedCrash(BaseException):
    pass


class BaseSynchronizerStub:
    def __init__(self, status: str = "UNCHANGED", *, advanced_base: str | None = None):
        self.status = status
        self.advanced_base = advanced_base
        self.calls = 0

    def sync(self, snapshot: TaskSnapshot) -> BaseSyncResult:
        self.calls += 1
        base_sha = snapshot.base_sha or ""
        head_sha = snapshot.head_sha or ""
        advanced_base = self.advanced_base or base_sha
        return BaseSyncResult(
            status=self.status,
            previous_base_sha=base_sha,
            base_sha=advanced_base,
            previous_head_sha=head_sha,
            head_sha=("d" * 40 if self.status == "UPDATED" else head_sha),
        )


class StatefulGitHub:
    def __init__(self):
        self.pull_request: PullRequestState | None = None
        self.remote_head: str | None = None
        self.workpad = False
        self.label = False
        self.review_request = False
        self.writes = {
            "push": 0,
            "draft": 0,
            "convert-draft": 0,
            "body": 0,
            "ready": 0,
            "workpad": 0,
            "label": 0,
            "review-request": 0,
        }
        self.crash_after_write: str | None = None
        self.drift_head_after_write: str | None = None
        self.failures: set[str] = set()
        self.checks = {name: "SUCCESS" for name in REQUIRED_CHECKS}
        self.baseline_checks = {name: "SUCCESS" for name in REQUIRED_CHECKS}
        self.review_threads: tuple[ReviewThreadFeedback, ...] = ()
        self.push_failure: GitHubAdapterError | None = None
        self.fact_failure: GitHubAdapterError | None = None

    def _write(self, name: str) -> None:
        self.writes[name] += 1
        if self.drift_head_after_write == name and self.pull_request is not None:
            self.pull_request = replace(self.pull_request, head_sha="9" * 40)
            self.drift_head_after_write = None
        if self.crash_after_write == name:
            self.crash_after_write = None
            raise InjectedCrash(name)

    def push(self, snapshot: TaskSnapshot, *, repository: str) -> str:
        if self.push_failure is not None:
            raise self.push_failure
        if self.remote_head != snapshot.head_sha:
            self.remote_head = snapshot.head_sha
            self._write("push")
        return f"push:{repository}:{snapshot.branch}:{snapshot.head_sha}"

    def list_open_pull_requests(self, repository, head_branch):
        return () if self.pull_request is None else (self.pull_request,)

    def get_pull_request(self, repository, pull_request_number):
        if self.pull_request is None or self.pull_request.number != pull_request_number:
            raise AssertionError("missing pull request")
        return self.pull_request

    def create_draft_pull_request(
        self, repository, base_branch, head_branch, title, body
    ):
        self.pull_request = PullRequestState(
            number=50,
            base_branch=base_branch,
            head_branch=head_branch,
            head_sha=self.remote_head or "",
            draft=True,
            body=body,
            checks={},
            unresolved_review_threads=0,
        )
        self._write("draft")
        return self.pull_request

    def convert_pull_request_to_draft(
        self, repository, pull_request_number, expected_head_sha
    ):
        if self.pull_request is None:
            raise AssertionError("missing pull request")
        if not self.pull_request.draft:
            self.pull_request = replace(self.pull_request, draft=True)
            self._write("convert-draft")
        return self.pull_request

    def reconcile_pull_request_body(
        self, repository, pull_request_number, expected_head_sha, body
    ):
        if self.pull_request is None:
            raise AssertionError("missing pull request")
        if self.pull_request.body != body:
            self.pull_request = replace(self.pull_request, body=body)
            self._write("body")
        return self.pull_request

    def mark_pull_request_ready(
        self, repository, pull_request_number, head_sha, body
    ):
        if self.pull_request is None:
            raise AssertionError("missing pull request")
        if self.pull_request.draft:
            self.pull_request = replace(self.pull_request, draft=False)
            self._write("ready")
        return GitHubEventReceipt(
            operation="ready",
            repository=repository,
            resource_kind="pull_request",
            resource_id=str(pull_request_number),
            state="ready",
            source="mutation",
            pull_request_number=pull_request_number,
            head_sha=head_sha,
        )

    def upsert_workpad(self, repository, issue_number, marker, body):
        if "workpad" in self.failures:
            raise RuntimeError("workpad unavailable")
        if not self.workpad:
            self.workpad = True
            self._write("workpad")
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
        if "label" in self.failures:
            raise RuntimeError("label unavailable")
        if not self.label:
            self.label = True
            self._write("label")
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
        if "review-request" in self.failures:
            raise RuntimeError("review request unavailable")
        if not self.review_request:
            self.review_request = True
            self._write("review-request")
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

    def collect_commit_checks(self, repository, head_sha):
        if self.fact_failure is not None:
            raise self.fact_failure
        if head_sha == self.base_sha:
            return dict(self.baseline_checks)
        return dict(self.checks)

    def collect_review_packet(self, repository, pull_request_number, head_sha):
        return ReviewPacket(
            repository, pull_request_number, head_sha, self.review_threads
        )


class GitHubLifecycleTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.workspace = self.root / "workspace"
        self.artifacts = self.root / "artifacts"
        self.state_root = self.root / "state"
        self.snapshot_path = self.state_root / "tasks" / "GH-50.json"
        self.git(self.root, "init", "-b", "codex/gh-50-task", self.workspace.as_posix())
        self.git(self.workspace, "config", "user.name", "Test")
        self.git(self.workspace, "config", "user.email", "test@example.invalid")
        (self.workspace / "value.txt").write_text("base\n", encoding="utf-8")
        self.git(self.workspace, "add", "value.txt")
        self.git(self.workspace, "commit", "-m", "base")
        self.base_sha = self.git(self.workspace, "rev-parse", "HEAD").stdout.strip()
        (self.workspace / "value.txt").write_text("candidate\n", encoding="utf-8")
        self.revision = CandidateSnapshotter(
            self.workspace, self.artifacts
        ).freeze(self.base_sha)
        commands = ("./scripts/quality-gate.sh",)
        policy = GateRequest.calculate_command_policy_sha256(commands)
        request = GateRequest(
            gate_id="gate-gh-50",
            issue_identifier="GH-50",
            candidate_revision=self.revision,
            runner_image="registry.example/gate@sha256:" + "1" * 64,
            command_policy_sha256=policy,
            validation_commands=commands,
            timeout_seconds=60,
            requested_at="2026-08-19T00:00:00Z",
        )
        receipt = GateReceipt(
            gate_id=request.gate_id,
            issue_identifier="GH-50",
            candidate_revision=self.revision,
            runner_image=request.runner_image,
            command_policy_sha256=policy,
            verdict="PASS",
            started_at="2026-08-19T00:00:00Z",
            finished_at="2026-08-19T00:01:00Z",
            exit_code=0,
            log_sha256="2" * 64,
            job_uid="job-gh-50",
            pod_uid="pod-gh-50",
            findings=(),
        )
        snapshot = TaskSnapshot(
            issue_identifier="GH-50",
            state="queued",
            base_sha=self.base_sha,
            head_sha=self.base_sha,
            branch="codex/gh-50-task",
            workspace=str(self.workspace.resolve()),
            candidate_revision=self.revision.to_json(),
            gate_request=request.to_json(),
            gate_receipt=receipt.to_json(),
            task_brief=TaskBrief(
                issue_identifier="GH-50",
                title="[Agent Goal]: lifecycle test",
                intent="Promote the exact reviewed candidate.",
                value="Provide deterministic handoff evidence.",
                in_scope="Candidate promotion and GitHub lifecycle.",
                out_of_scope="Merge, release and deployment.",
                acceptance=(
                    AcceptanceCriterion(
                        "AC-LIFECYCLE-01",
                        "The lifecycle completes only for exact evidence.",
                        "./scripts/quality-gate.sh",
                    ),
                ),
                validation_commands=commands,
                compatibility="Internal host-side contract only.",
                recovery="Resume from the atomic task snapshot.",
                required_human_approvals=("None",),
                residual_risks=("Real GitHub E2E remains pending.",),
                risk="Low risk internal implementation.",
            ).to_json(),
            iteration_phase="complete",
        )
        snapshot.record_review_decision(
            ReviewDecision(
                verdict="PASS",
                head_sha=self.base_sha,
                candidate_revision=self.revision.candidate_revision,
                reviewer_role="spec-evaluator",
                reviewer_session_id="reviewer-session",
                implementer_session_id="implementer-session",
                findings=(),
            )
        )
        SnapshotStore(self.snapshot_path).save(snapshot)
        self.github = StatefulGitHub()
        self.github.base_sha = self.base_sha

    def tearDown(self) -> None:
        self.temp.cleanup()

    @staticmethod
    def git(cwd: Path, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *args], cwd=cwd, check=True, capture_output=True, text=True
        )

    def controller(self, *, promoter=None, base_synchronizer=None) -> GitHubLifecycleController:
        capabilities = {
            "create_remote_branch": True,
            "push_commit": True,
            "create_draft_pull_request": True,
            "write_issue_comment": True,
            "update_issue_labels": True,
            "mark_pull_request_ready": True,
            "request_pull_request_review": True,
        }
        return GitHubLifecycleController(
            state_root=self.state_root,
            artifact_root=self.artifacts,
            client=self.github,
            pusher=self.github,
            base_synchronizer=base_synchronizer or BaseSynchronizerStub(),
            failure_router=FailureRouter(
                Coordinator(
                    transitions={
                        "queued": {"waiting_ci", "blocked", "fused"},
                        "waiting_ci": {"queued", "blocked", "fused"},
                    },
                    claimable_states={"queued"},
                    limits=CoordinatorLimits(2, 3, 1, 48, 14400, 5000000),
                )
            ),
            capabilities=capabilities,
            required_checks=REQUIRED_CHECKS,
            promoter=promoter,
        )

    def test_advanced_base_returns_to_implement_before_remote_write(self) -> None:
        synchronizer = BaseSynchronizerStub("UPDATED", advanced_base="c" * 40)

        result = self.reconcile(self.controller(base_synchronizer=synchronizer))
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertEqual(("base-sync:updated",), result.blockers)
        self.assertEqual("queued", restored.state)
        self.assertEqual("implement", restored.iteration_phase)
        self.assertEqual("c" * 40, restored.base_sha)
        self.assertEqual("d" * 40, restored.head_sha)
        self.assertIsNone(restored.candidate_revision)
        self.assertEqual(0, sum(self.github.writes.values()))

    def test_base_conflict_returns_to_implement_before_remote_write(self) -> None:
        synchronizer = BaseSynchronizerStub("CONFLICT", advanced_base="c" * 40)

        result = self.reconcile(self.controller(base_synchronizer=synchronizer))
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertEqual(("base-sync:conflict",), result.blockers)
        self.assertEqual("queued", restored.state)
        self.assertEqual("implement", restored.iteration_phase)
        self.assertEqual(self.base_sha, restored.base_sha)
        self.assertEqual("c" * 40, restored.base_sync["base_sha"])
        self.assertIsNone(restored.candidate_revision)
        self.assertEqual(0, sum(self.github.writes.values()))

    def reconcile(self, controller=None):
        return (controller or self.controller()).reconcile(
            "GH-50",
            repository=REPOSITORY,
            title="ci(agent): promote reviewed candidate",
            workpad_body="Exact candidate is ready for human review.",
            reviewer="maintainer",
        )

    def test_promotion_crash_recovers_the_same_commit_and_completes(self) -> None:
        crash_once = {"armed": True}

        def fault(stage: str) -> None:
            if stage == "candidate_promoted" and crash_once["armed"]:
                crash_once["armed"] = False
                raise InjectedCrash(stage)

        promoter = CandidatePromoter(self.artifacts, fault_injector=fault)
        with self.assertRaises(InjectedCrash):
            self.reconcile(self.controller(promoter=promoter))
        promoted_head = self.git(self.workspace, "rev-parse", "HEAD").stdout.strip()
        self.assertEqual(self.base_sha, SnapshotStore(self.snapshot_path).load().head_sha)

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertTrue(result.handoff_complete)
        self.assertEqual(promoted_head, restored.head_sha)
        self.assertEqual(promoted_head, restored.candidate_commit_sha)
        self.assertEqual(
            self.revision.tree_sha,
            self.git(self.workspace, "show", "-s", "--format=%T", promoted_head).stdout.strip(),
        )
        self.assertEqual(
            self.base_sha,
            self.git(self.workspace, "show", "-s", "--format=%P", promoted_head).stdout.strip(),
        )

    def test_remote_success_before_snapshot_save_is_recovered_without_duplicate_write(self) -> None:
        for stage in ("push", "draft", "ready"):
            with self.subTest(stage=stage):
                self.tearDown()
                self.setUp()
                self.github.crash_after_write = stage
                with self.assertRaises(InjectedCrash):
                    self.reconcile()

                result = self.reconcile()

                self.assertTrue(result.handoff_complete)
                self.assertEqual(1, self.github.writes[stage])

    def test_existing_draft_body_is_reconciled_for_current_candidate(self) -> None:
        self.github.crash_after_write = "push"
        with self.assertRaises(InjectedCrash):
            self.reconcile()
        self.github.pull_request = PullRequestState(
            number=50,
            base_branch="develop",
            head_branch="codex/gh-50-task",
            head_sha=self.github.remote_head or "",
            draft=True,
            body="old candidate body",
            checks={},
            unresolved_review_threads=0,
        )

        result = self.reconcile()

        self.assertTrue(result.handoff_complete)
        self.assertNotEqual("old candidate body", self.github.pull_request.body)
        self.assertEqual(1, self.github.writes["body"])
        self.assertEqual(0, self.github.writes["convert-draft"])

    def test_existing_ready_body_returns_to_draft_before_reconciliation(self) -> None:
        self.github.crash_after_write = "push"
        with self.assertRaises(InjectedCrash):
            self.reconcile()
        self.github.pull_request = PullRequestState(
            number=50,
            base_branch="develop",
            head_branch="codex/gh-50-task",
            head_sha=self.github.remote_head or "",
            draft=False,
            body="old candidate body",
            checks={},
            unresolved_review_threads=0,
        )

        result = self.reconcile()

        self.assertTrue(result.handoff_complete)
        self.assertEqual(1, self.github.writes["convert-draft"])
        self.assertEqual(1, self.github.writes["body"])
        self.assertEqual(1, self.github.writes["ready"])

    def test_each_handoff_signal_recovers_without_duplicate_remote_effect(self) -> None:
        for signal in ("workpad", "label", "review-request"):
            with self.subTest(signal=signal):
                self.tearDown()
                self.setUp()
                self.github.crash_after_write = signal
                with self.assertRaises(InjectedCrash):
                    self.reconcile()

                result = self.reconcile()

                self.assertTrue(result.handoff_complete)
                self.assertEqual(1, self.github.writes[signal])

    def test_pending_current_head_check_keeps_pull_request_draft(self) -> None:
        self.github.checks["quality"] = "PENDING"

        result = self.reconcile()

        self.assertFalse(result.ready)
        self.assertFalse(result.handoff_complete)
        self.assertTrue(self.github.pull_request.draft)
        self.assertEqual(0, self.github.writes["ready"])
        self.assertEqual("waiting_ci", SnapshotStore(self.snapshot_path).load().state)

    def test_candidate_ci_failure_routes_back_to_implementation(self) -> None:
        self.github.checks["quality"] = "FAILURE"

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertFalse(result.ready)
        self.assertEqual("queued", restored.state)
        self.assertEqual("implement", restored.iteration_phase)
        self.assertEqual(1, len(restored.failure_routes))
        self.assertEqual(0, self.github.writes["ready"])

    def test_same_ci_root_across_heads_shares_semantic_fix_budget(self) -> None:
        self.github.checks["quality"] = "FAILURE"
        self.reconcile()
        snapshot = SnapshotStore(self.snapshot_path).load()
        first_root = next(iter(snapshot.semantic_fix_strategies))
        snapshot.state = "waiting_ci"
        snapshot.iteration_phase = "complete"
        snapshot.head_sha = "e" * 40
        snapshot.candidate_revision = {"candidate_revision": "f" * 64}

        blockers = self.controller()._route_current_failures(
            snapshot,
            repository=REPOSITORY,
            checks={"quality": "FAILURE"},
            baseline_checks={"quality": "SUCCESS"},
            review_packet=ReviewPacket(REPOSITORY, 50, "e" * 40, ()),
        )

        self.assertTrue(blockers)
        self.assertEqual([first_root], list(snapshot.semantic_fix_strategies))
        self.assertEqual(2, len(snapshot.semantic_fix_strategies[first_root]))

    def test_transport_failure_consumes_infrastructure_budget(self) -> None:
        self.github.fact_failure = GitHubAdapterError("transport_error")

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertIn("infrastructure:network", result.blockers[0])
        self.assertEqual("waiting_ci", restored.state)
        self.assertEqual(1, restored.infrastructure_retries)

    def test_push_permission_failure_blocks_before_remote_write(self) -> None:
        self.github.push_failure = GitHubAdapterError("http_403")

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertIn("human-decision:permission", result.blockers[0])
        self.assertEqual("blocked", restored.state)
        self.assertEqual(0, sum(self.github.writes.values()))

    def test_push_execution_failure_consumes_infrastructure_budget(self) -> None:
        self.github.push_failure = GitHubAdapterError("git_push_failed")

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertIn("infrastructure:service", result.blockers[0])
        self.assertEqual(1, restored.infrastructure_retries)
        self.assertEqual(1, len(restored.failure_routes))

    def test_push_rejection_routes_to_permission_block(self) -> None:
        self.github.push_failure = GitHubAdapterError("git_push_rejected")

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertIn("human-decision:permission", result.blockers[0])
        self.assertEqual("blocked", restored.state)
        self.assertEqual(1, len(restored.failure_routes))

    def test_baseline_ci_failure_blocks_without_candidate_retry(self) -> None:
        self.github.checks["quality"] = "FAILURE"
        self.github.baseline_checks["quality"] = "FAILURE"

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertFalse(result.ready)
        self.assertEqual("blocked", restored.state)
        self.assertIn("baseline-failure", restored.blocked_reason)
        self.assertEqual({}, restored.semantic_fix_strategies)

    def test_same_head_success_then_failure_routes_as_flaky(self) -> None:
        self.github.checks["static-analysis"] = "PENDING"
        first = self.reconcile()
        self.assertFalse(first.ready)

        self.github.checks["static-analysis"] = "SUCCESS"
        self.github.checks["quality"] = "FAILURE"
        second = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertFalse(second.ready)
        self.assertEqual("waiting_ci", restored.state)
        self.assertEqual(1, sum(restored.flaky_reruns.values()))
        route = next(iter(restored.failure_routes.values()))
        self.assertEqual("flaky", route["category"])

    def test_actionable_review_routes_back_to_implementation(self) -> None:
        self.github.checks["static-analysis"] = "PENDING"
        first = self.reconcile()
        self.assertFalse(first.ready)
        self.github.checks["static-analysis"] = "SUCCESS"
        self.github.review_threads = (
            ReviewThreadFeedback(
                thread_id="thread-1",
                path="src/example.py",
                line=10,
                original_line=8,
                resolved=False,
                classification="actionable",
                comments=(
                    ReviewCommentFeedback(
                        comment_id="comment-1",
                        author_login="reviewer",
                        body="Fix the exact-head defect.",
                        commit_sha=self.github.remote_head,
                        outdated=False,
                        created_at="2026-08-19T00:00:00Z",
                        updated_at="2026-08-19T00:00:00Z",
                    ),
                ),
            ),
        )

        result = self.reconcile()
        restored = SnapshotStore(self.snapshot_path).load()

        self.assertFalse(result.ready)
        self.assertEqual("queued", restored.state)
        route = next(iter(restored.failure_routes.values()))
        self.assertEqual("candidate", route["category"])
        self.assertEqual(
            "thread-1", restored.github_review_packet["threads"][0]["thread_id"]
        )
        self.assertIsNone(restored.candidate_revision)

    def test_same_review_thread_across_heads_shares_semantic_fix_budget(self) -> None:
        def packet(head_sha: str, updated_at: str) -> ReviewPacket:
            return ReviewPacket(
                REPOSITORY,
                50,
                head_sha,
                (
                    ReviewThreadFeedback(
                        thread_id="thread-stable",
                        path="src/example.py",
                        line=10,
                        original_line=8,
                        resolved=False,
                        classification="actionable",
                        comments=(
                            ReviewCommentFeedback(
                                comment_id="comment-stable",
                                author_login="reviewer",
                                body="Fix this defect.",
                                commit_sha=head_sha,
                                outdated=False,
                                created_at="2026-08-19T00:00:00Z",
                                updated_at=updated_at,
                            ),
                        ),
                    ),
                ),
            )

        snapshot = SnapshotStore(self.snapshot_path).load()
        snapshot.state = "waiting_ci"
        first_head = snapshot.head_sha or ""
        self.controller()._route_current_failures(
            snapshot,
            repository=REPOSITORY,
            checks={},
            baseline_checks={},
            review_packet=packet(first_head, "2026-08-19T00:00:00Z"),
        )
        first_root = next(iter(snapshot.semantic_fix_strategies))
        snapshot.state = "waiting_ci"
        snapshot.iteration_phase = "complete"
        snapshot.head_sha = "e" * 40
        snapshot.candidate_revision = {"candidate_revision": "f" * 64}

        self.controller()._route_current_failures(
            snapshot,
            repository=REPOSITORY,
            checks={},
            baseline_checks={},
            review_packet=packet("e" * 40, "2026-08-19T00:01:00Z"),
        )

        self.assertEqual([first_root], list(snapshot.semantic_fix_strategies))
        self.assertEqual(2, len(snapshot.semantic_fix_strategies[first_root]))

    def test_persistent_review_thread_keeps_root_when_other_thread_resolves(self) -> None:
        def thread(thread_id: str, head_sha: str) -> ReviewThreadFeedback:
            return ReviewThreadFeedback(
                thread_id=thread_id,
                path=f"src/{thread_id}.py",
                line=10,
                original_line=8,
                resolved=False,
                classification="actionable",
                comments=(
                    ReviewCommentFeedback(
                        comment_id=f"comment-{thread_id}",
                        author_login="reviewer",
                        body="Fix this defect.",
                        commit_sha=head_sha,
                        outdated=False,
                        created_at="2026-08-19T00:00:00Z",
                        updated_at="2026-08-19T00:00:00Z",
                    ),
                ),
            )

        snapshot = SnapshotStore(self.snapshot_path).load()
        snapshot.state = "waiting_ci"
        first_head = snapshot.head_sha or ""
        self.controller()._route_current_failures(
            snapshot,
            repository=REPOSITORY,
            checks={},
            baseline_checks={},
            review_packet=ReviewPacket(
                REPOSITORY, 50, first_head,
                (thread("a", first_head), thread("b", first_head)),
            ),
        )
        first_root = next(iter(snapshot.semantic_fix_strategies))
        snapshot.state = "waiting_ci"
        snapshot.iteration_phase = "complete"
        snapshot.head_sha = "e" * 40
        snapshot.candidate_revision = {"candidate_revision": "f" * 64}

        self.controller()._route_current_failures(
            snapshot,
            repository=REPOSITORY,
            checks={},
            baseline_checks={},
            review_packet=ReviewPacket(
                REPOSITORY, 50, "e" * 40, (thread("a", "e" * 40),)
            ),
        )

        self.assertEqual([first_root], list(snapshot.semantic_fix_strategies))
        self.assertEqual(2, len(snapshot.semantic_fix_strategies[first_root]))

    def test_human_review_state_retries_only_failed_enhancement(self) -> None:
        self.github.failures = {"label"}

        first = self.reconcile()
        self.github.failures.clear()
        second = self.reconcile()

        self.assertTrue(first.handoff_complete)
        self.assertEqual(("label",), first.blockers)
        self.assertTrue(second.handoff_complete)
        self.assertEqual(1, self.github.writes["workpad"])
        self.assertEqual(1, self.github.writes["label"])
        self.assertEqual(1, self.github.writes["review-request"])

    def test_head_drift_during_handoff_is_not_persisted_as_human_review(self) -> None:
        self.github.drift_head_after_write = "workpad"

        with self.assertRaisesRegex(RuntimeError, "changed during handoff"):
            self.reconcile()

        restored = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("waiting_ci", restored.state)
        self.assertIsNone(restored.handoff_head_sha)
        self.assertEqual(0, self.github.writes["label"])
        self.assertEqual(0, self.github.writes["review-request"])

    def test_terminal_task_state_is_rejected_before_git_or_remote_write(self) -> None:
        snapshot = SnapshotStore(self.snapshot_path).load()
        snapshot.state = "blocked"
        SnapshotStore(self.snapshot_path).save(snapshot)

        with self.assertRaisesRegex(RuntimeError, "cannot enter GitHub lifecycle"):
            self.reconcile()

        self.assertEqual(
            self.base_sha,
            self.git(self.workspace, "rev-parse", "HEAD").stdout.strip(),
        )
        self.assertEqual(0, sum(self.github.writes.values()))


if __name__ == "__main__":
    unittest.main()
