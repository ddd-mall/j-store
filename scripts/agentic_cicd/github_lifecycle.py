from __future__ import annotations

import re
import subprocess
import hashlib
import json
from collections.abc import Callable, Mapping
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Protocol

from .candidate import CandidateRevision, CandidateSnapshotter
from .coordinator import SnapshotStore, TaskSnapshot
from .failure_router import FailureEvidence, FailureRouter
from .github_adapter import GitHubAdapterError
from .phase_bridge import SymphonyPhaseBridge
from .github_reconciler import GitHubReconciler, PullRequestState
from .github_receipt import GitHubEventReceipt
from .process_environment import trusted_process_environment
from .pr_packet import build_pull_request_packet
from .protocol import GateReceipt, GateRequest, ReviewDecision, ReviewPacket
from .workspace import BaseSyncResult, Workspace, WorkspaceManager


ISSUE_IDENTIFIER = re.compile(r"GH-([1-9][0-9]*)\Z")
FULL_SHA = re.compile(r"[0-9a-f]{40}\Z")
GIT_TIMEOUT_SECONDS = 120
MAXIMUM_CHECK_HISTORY = 8


class GitPusher(Protocol):
    def push(self, snapshot: TaskSnapshot, *, repository: str) -> str: ...


class BaseSynchronizer(Protocol):
    def sync(self, snapshot: TaskSnapshot) -> BaseSyncResult: ...


class WorkspaceBaseSynchronizer:
    """Synchronizes the trusted Symphony clone before any remote candidate write."""

    def sync(self, snapshot: TaskSnapshot) -> BaseSyncResult:
        if (
            not snapshot.workspace
            or not snapshot.base_sha
            or not snapshot.branch
            or not snapshot.head_sha
        ):
            raise RuntimeError("base synchronization requires trusted workspace identity")
        workspace_path = Path(snapshot.workspace).resolve()
        manager = WorkspaceManager(workspace_path, workspace_path.parent)
        return manager.sync_base(
            Workspace(
                snapshot.issue_identifier,
                snapshot.base_sha,
                snapshot.branch,
                workspace_path,
            ),
            expected_head_sha=snapshot.head_sha,
        )


class GitHubLifecycleClient(Protocol):
    def get_pull_request(
        self, repository: str, pull_request_number: int
    ) -> PullRequestState: ...

    def list_open_pull_requests(
        self, repository: str, head_branch: str
    ) -> tuple[PullRequestState, ...]: ...

    def create_draft_pull_request(
        self,
        repository: str,
        base_branch: str,
        head_branch: str,
        title: str,
        body: str,
    ) -> PullRequestState: ...

    def convert_pull_request_to_draft(
        self,
        repository: str,
        pull_request_number: int,
        expected_head_sha: str,
    ) -> PullRequestState: ...

    def reconcile_pull_request_body(
        self,
        repository: str,
        pull_request_number: int,
        expected_head_sha: str,
        body: str,
    ) -> PullRequestState: ...

    def mark_pull_request_ready(
        self,
        repository: str,
        pull_request_number: int,
        head_sha: str,
        body: str,
    ) -> GitHubEventReceipt: ...

    def upsert_workpad(
        self, repository: str, issue_number: int, marker: str, body: str
    ) -> GitHubEventReceipt: ...

    def replace_issue_state_label(
        self, repository: str, issue_number: int, label: str
    ) -> GitHubEventReceipt: ...

    def request_pull_request_review(
        self,
        repository: str,
        pull_request_number: int,
        head_sha: str,
        reviewer: str,
    ) -> GitHubEventReceipt: ...

    def collect_commit_checks(
        self, repository: str, head_sha: str
    ) -> Mapping[str, str]: ...

    def collect_review_packet(
        self, repository: str, pull_request_number: int, head_sha: str
    ) -> ReviewPacket: ...


@dataclass(frozen=True)
class LifecycleResult:
    pull_request_number: int
    ready: bool
    handoff_complete: bool
    blockers: tuple[str, ...] = ()


class CandidatePromoter:
    """Promotes one exact reviewed CandidateRevision to a single-parent commit."""

    def __init__(
        self,
        artifact_root: Path,
        *,
        fault_injector: Callable[[str], None] | None = None,
    ):
        self.artifact_root = artifact_root.resolve()
        self.fault_injector = fault_injector or (lambda _stage: None)

    def promote(self, snapshot: TaskSnapshot) -> str:
        revision, decision = self._validated_evidence(snapshot)
        workspace = self._workspace(snapshot)
        branch = snapshot.branch or ""
        old_head = snapshot.head_sha or ""
        self._git(workspace, "check-ref-format", f"refs/heads/{branch}")
        actual_branch = self._git(
            workspace, "branch", "--show-current"
        ).stdout.strip()
        if actual_branch != branch:
            raise RuntimeError("candidate workspace branch differs from task state")
        actual_head = self._git(workspace, "rev-parse", "HEAD").stdout.strip()

        if actual_head == old_head:
            if revision.base_sha != old_head:
                raise RuntimeError("candidate revision does not bind the task head")
            observed = CandidateSnapshotter(
                workspace, self.artifact_root
            ).freeze(old_head)
            if observed != revision:
                raise RuntimeError("workspace differs from the reviewed candidate")
            promoted_head = self._create_commit(
                workspace, snapshot.issue_identifier, branch, old_head, revision
            )
            self._git(workspace, "reset", "--mixed", promoted_head)
            self.fault_injector("candidate_promoted")
        else:
            promoted_head = actual_head
            self._require_recoverable_commit(
                workspace, promoted_head, old_head, revision
            )
            self._git(workspace, "reset", "--mixed", promoted_head)

        snapshot.head_sha = promoted_head
        snapshot.candidate_commit_sha = promoted_head
        snapshot.record_review_decision(replace(decision, head_sha=promoted_head))
        return promoted_head

    def _create_commit(
        self,
        workspace: Path,
        issue_identifier: str,
        branch: str,
        old_head: str,
        revision: CandidateRevision,
    ) -> str:
        message = (
            f"ci(agent): promote {issue_identifier} candidate\n\n"
            f"Candidate-Revision: {revision.candidate_revision}\n"
        )
        result = self._git(
            workspace,
            "-c",
            "commit.gpgSign=false",
            "-c",
            "user.name=j-store Agentic CI/CD",
            "-c",
            "user.email=agentic-cicd@localhost.invalid",
            "commit-tree",
            revision.tree_sha,
            "-p",
            old_head,
            input_text=message,
        )
        promoted_head = result.stdout.strip()
        if not FULL_SHA.fullmatch(promoted_head):
            raise RuntimeError("candidate promotion did not produce a full commit SHA")
        self._git(
            workspace,
            "update-ref",
            f"refs/heads/{branch}",
            promoted_head,
            old_head,
        )
        self._require_recoverable_commit(
            workspace, promoted_head, old_head, revision
        )
        return promoted_head

    def _require_recoverable_commit(
        self,
        workspace: Path,
        promoted_head: str,
        old_head: str,
        revision: CandidateRevision,
    ) -> None:
        if not FULL_SHA.fullmatch(promoted_head):
            raise RuntimeError("workspace HEAD is not a full commit SHA")
        parent = self._git(
            workspace, "show", "-s", "--format=%P", promoted_head
        ).stdout.strip()
        tree = self._git(
            workspace, "show", "-s", "--format=%T", promoted_head
        ).stdout.strip()
        message = self._git(
            workspace, "show", "-s", "--format=%B", promoted_head
        ).stdout.splitlines()
        if parent != old_head:
            raise RuntimeError("promoted candidate is not a single-parent child")
        if tree != revision.tree_sha:
            raise RuntimeError("promoted candidate tree differs from reviewed evidence")
        if f"Candidate-Revision: {revision.candidate_revision}" not in message:
            raise RuntimeError("promoted candidate lacks its revision identity")

    @staticmethod
    def _validated_evidence(
        snapshot: TaskSnapshot,
    ) -> tuple[CandidateRevision, ReviewDecision]:
        if snapshot.iteration_phase != "complete":
            raise RuntimeError("candidate promotion requires a completed local loop")
        if (
            snapshot.candidate_revision is None
            or snapshot.gate_request is None
            or snapshot.gate_receipt is None
        ):
            raise RuntimeError("candidate promotion lacks exact gate evidence")
        revision = CandidateRevision.from_json(snapshot.candidate_revision)
        request = GateRequest.from_json(snapshot.gate_request)
        receipt = GateReceipt.from_json(snapshot.gate_receipt)
        if (
            receipt.verdict != "PASS"
            or request.issue_identifier != snapshot.issue_identifier
            or receipt.issue_identifier != snapshot.issue_identifier
            or request.candidate_revision != revision
            or receipt.candidate_revision != revision
            or request.gate_id != receipt.gate_id
            or request.runner_image != receipt.runner_image
            or request.command_policy_sha256 != receipt.command_policy_sha256
        ):
            raise RuntimeError("candidate promotion gate evidence does not match")
        decision = snapshot.review_decision_for(revision.candidate_revision)
        if (
            decision is None
            or decision.verdict != "PASS"
            or decision.head_sha != snapshot.head_sha
            or decision.candidate_revision != revision.candidate_revision
        ):
            raise RuntimeError("candidate promotion lacks exact independent review PASS")
        return revision, decision

    @staticmethod
    def _workspace(snapshot: TaskSnapshot) -> Path:
        if not snapshot.workspace:
            raise RuntimeError("task has no trusted workspace")
        workspace = Path(snapshot.workspace).resolve()
        if not workspace.is_dir():
            raise RuntimeError("trusted workspace does not exist")
        return workspace

    @staticmethod
    def _git(
        workspace: Path, *arguments: str, input_text: str | None = None
    ) -> subprocess.CompletedProcess[str]:
        environment = trusted_process_environment()
        environment.update(
            {
                "GIT_CONFIG_GLOBAL": "/dev/null",
                "GIT_CONFIG_SYSTEM": "/dev/null",
                "GIT_CONFIG_NOSYSTEM": "1",
                "GIT_TERMINAL_PROMPT": "0",
                "GIT_NO_REPLACE_OBJECTS": "1",
            }
        )
        try:
            return subprocess.run(
                ["git", *arguments],
                cwd=workspace,
                env=environment,
                check=True,
                capture_output=True,
                text=True,
                input=input_text,
                timeout=GIT_TIMEOUT_SECONDS,
            )
        except Exception:
            raise RuntimeError("candidate promotion Git operation failed") from None


class GitHubLifecycleController:
    """Runs one recoverable host-side GitHub candidate reconciliation."""

    def __init__(
        self,
        *,
        state_root: Path,
        artifact_root: Path,
        client: GitHubLifecycleClient,
        pusher: GitPusher,
        base_synchronizer: BaseSynchronizer,
        failure_router: FailureRouter,
        capabilities: Mapping[str, bool],
        required_checks: set[str] | frozenset[str],
        promoter: CandidatePromoter | None = None,
        fault_injector: Callable[[str], None] | None = None,
    ):
        self.state_root = state_root.resolve()
        self.client = client
        self.pusher = pusher
        self.base_synchronizer = base_synchronizer
        self.failure_router = failure_router
        self.capabilities = dict(capabilities)
        self.reconciler = GitHubReconciler(
            client,
            capabilities=self.capabilities,
            required_checks=required_checks,
        )
        self.promoter = promoter or CandidatePromoter(artifact_root)
        self.fault_injector = fault_injector or (lambda _stage: None)

    def reconcile(
        self,
        issue_identifier: str,
        *,
        repository: str,
        title: str,
        workpad_body: str,
        reviewer: str | None,
    ) -> LifecycleResult:
        match = ISSUE_IDENTIFIER.fullmatch(issue_identifier)
        if match is None:
            raise ValueError("issue identifier must match GH-<positive-number>")
        store = SnapshotStore(
            self.state_root / "tasks" / f"{issue_identifier}.json"
        )
        snapshot = store.load()
        if (
            snapshot.state == "human_review"
            and snapshot.handoff_head_sha == snapshot.head_sha
            and snapshot.pull_request_number is not None
        ):
            return self._resume_handoff(
                store,
                snapshot,
                repository=repository,
                issue_number=int(match.group(1)),
                workpad_body=workpad_body,
                reviewer=reviewer,
            )
        if snapshot.state not in {"queued", "waiting_ci"}:
            raise RuntimeError(
                f"task state {snapshot.state} cannot enter GitHub lifecycle"
            )
        self._require_capability("push_commit")
        self._require_capability("create_remote_branch")

        if snapshot.candidate_commit_sha != snapshot.head_sha:
            self.promoter.promote(snapshot)
            store.save(snapshot)

        base_sync = self.base_synchronizer.sync(snapshot)
        SymphonyPhaseBridge.apply_base_sync(snapshot, base_sync)
        if base_sync.status != "UNCHANGED":
            store.save(snapshot)
            return LifecycleResult(
                snapshot.pull_request_number,
                False,
                False,
                (f"base-sync:{base_sync.status.lower()}",),
            )

        pull_request_packet = build_pull_request_packet(
            snapshot, target_branch="develop"
        )
        snapshot.pull_request_packet = pull_request_packet.to_json()
        store.save(snapshot)

        push_key = f"push:{repository}:{snapshot.branch}:{snapshot.head_sha}"
        if push_key not in snapshot.github_events:
            try:
                push_event = self.pusher.push(snapshot, repository=repository)
            except GitHubAdapterError as error:
                return self._handle_adapter_failure(
                    store, snapshot, repository=repository, stage="push", error=error
                )
            if push_event != f"push:{repository}:{snapshot.branch}:{snapshot.head_sha}":
                raise RuntimeError("GitHub push receipt differs from task identity")
            self.fault_injector("push_succeeded")
            snapshot.github_events[push_key] = GitHubEventReceipt(
                operation="push",
                repository=repository,
                resource_kind="branch",
                resource_id=snapshot.branch,
                state="pushed",
                source="mutation",
                head_sha=snapshot.head_sha,
            ).to_json()
            store.save(snapshot)

        try:
            pull_request = self.reconciler.ensure_draft_pull_request(
                snapshot,
                repository=repository,
                title=title,
                body=pull_request_packet.render(),
            )
        except GitHubAdapterError as error:
            return self._handle_adapter_failure(
                store, snapshot, repository=repository, stage="draft", error=error
            )
        self.fault_injector("draft_reconciled")
        store.save(snapshot)

        try:
            checks = self.client.collect_commit_checks(
                repository, snapshot.head_sha or ""
            )
            baseline_checks = self.client.collect_commit_checks(
                repository, snapshot.base_sha or ""
            )
            review_packet = self.client.collect_review_packet(
                repository, pull_request.number, snapshot.head_sha or ""
            )
        except GitHubAdapterError as error:
            return self._handle_adapter_failure(
                store, snapshot, repository=repository, stage="facts", error=error
            )
        if (
            review_packet.repository != repository
            or review_packet.pull_request_number != pull_request.number
            or review_packet.head_sha != snapshot.head_sha
        ):
            raise RuntimeError("review packet differs from current pull request identity")
        pull_request = replace(
            pull_request,
            checks=checks,
            unresolved_review_threads=review_packet.unresolved_actionable_threads,
        )
        failure_blockers = self._route_current_failures(
            snapshot,
            repository=repository,
            checks=checks,
            baseline_checks=baseline_checks,
            review_packet=review_packet,
        )
        if failure_blockers:
            store.save(snapshot)
            return LifecycleResult(
                pull_request.number, False, False, failure_blockers
            )
        blockers = self.reconciler.readiness_blockers(snapshot, pull_request)
        if blockers:
            store.save(snapshot)
            return LifecycleResult(pull_request.number, False, False, blockers)

        snapshot.consume_idempotency_key(
            f"ready-intent:{repository}:{pull_request.number}:{pull_request.head_sha}"
        )
        store.save(snapshot)
        self.reconciler.mark_ready(
            snapshot, repository=repository, pull_request=pull_request
        )
        self.fault_injector("ready_reconciled")
        store.save(snapshot)
        pull_request = self._current_pull_request_facts(
            snapshot, repository=repository, pull_request_number=pull_request.number
        )
        if pull_request.draft:
            raise RuntimeError("pull request remained Draft after Ready reconciliation")

        handoff = self.reconciler.handoff(
            snapshot,
            repository=repository,
            issue_number=int(match.group(1)),
            pull_request=pull_request,
            workpad_body=workpad_body,
            reviewer=reviewer,
            fact_guard=lambda: self._require_current_handoff_facts(
                snapshot,
                repository=repository,
                pull_request_number=pull_request.number,
            ),
        )
        verified_pull_request = self._current_pull_request_facts(
            snapshot,
            repository=repository,
            pull_request_number=pull_request.number,
        )
        if verified_pull_request.draft:
            raise RuntimeError("pull request became Draft during handoff")
        post_handoff_blockers = self.reconciler.readiness_blockers(
            snapshot, verified_pull_request
        )
        if post_handoff_blockers:
            raise RuntimeError(
                "pull request facts changed during handoff: "
                + "; ".join(post_handoff_blockers)
            )
        self.fault_injector("handoff_reconciled")
        store.save(snapshot)
        return LifecycleResult(
            pull_request.number,
            True,
            handoff.complete,
            tuple(sorted(handoff.failures)),
        )

    def _route_current_failures(
        self,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        checks: Mapping[str, str],
        baseline_checks: Mapping[str, str],
        review_packet: ReviewPacket,
    ) -> tuple[str, ...]:
        head_sha = snapshot.head_sha or ""
        base_sha = snapshot.base_sha or ""
        history_by_name: dict[str, tuple[str, ...]] = {}
        for name, conclusion in sorted(checks.items()):
            identity = self._stable_digest("ci-check", repository, head_sha, name)
            history = snapshot.check_history.setdefault(identity, [])
            normalized = str(conclusion).upper()
            if not history or history[-1] != normalized:
                history.append(normalized)
                del history[:-MAXIMUM_CHECK_HISTORY]
            history_by_name[name] = tuple(history)

        failed_checks = sorted(
            name
            for name, conclusion in checks.items()
            if str(conclusion).upper() in {"FAILURE", "CONFLICT"}
        )
        if failed_checks:
            name = failed_checks[0]
            conclusion = str(checks[name]).upper()
            baseline = str(baseline_checks.get(name, "UNKNOWN")).upper()
            digest = self._stable_digest("ci-root", repository, name)
            if baseline == "UNKNOWN":
                return (f"ci-baseline-unknown:{digest[:24]}",)
            history = history_by_name[name]
            event_digest = self._stable_digest(
                "ci-event", repository, base_sha, head_sha, name, *history
            )
            route = self.failure_router.route(
                snapshot,
                FailureEvidence(
                    event_id=f"ci:{event_digest}",
                    root_cause_id=f"ci:{digest[:24]}",
                    source_kind="ci",
                    base_sha=base_sha,
                    head_sha=head_sha,
                    current_conclusion=conclusion,
                    baseline_conclusion=baseline,
                    same_head_conclusions=history,
                ),
            )
            return (route.reason,)

        actionable = tuple(sorted(
            (thread for thread in review_packet.threads
            if thread.classification == "actionable"
            ), key=lambda thread: thread.thread_id
        ))
        if actionable:
            snapshot.github_review_packet = review_packet.to_json()
            thread = actionable[0]
            identities = tuple(
                sorted(
                    f"{thread.thread_id}:{comment.comment_id}:{comment.updated_at}"
                    for comment in thread.comments
                )
            )
            event_digest = self._stable_digest(
                "review-event", repository, str(review_packet.pull_request_number),
                head_sha, *identities,
            )
            root_digest = self._stable_digest(
                "review-root", repository, str(review_packet.pull_request_number),
                thread.thread_id,
            )
            route = self.failure_router.route(
                snapshot,
                FailureEvidence(
                    event_id=f"review:{event_digest}",
                    root_cause_id=f"review:{root_digest[:24]}",
                    source_kind="review",
                    base_sha=base_sha,
                    head_sha=head_sha,
                    current_conclusion="FAILURE",
                    baseline_conclusion="NOT_APPLICABLE",
                    same_head_conclusions=("FAILURE",),
                ),
            )
            return (route.reason,)
        snapshot.github_review_packet = None
        return ()

    @staticmethod
    def _stable_digest(*parts: str) -> str:
        encoded = json.dumps(parts, separators=(",", ":"), ensure_ascii=True).encode()
        return hashlib.sha256(encoded).hexdigest()

    def _handle_adapter_failure(
        self,
        store: SnapshotStore,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        stage: str,
        error: GitHubAdapterError,
    ) -> LifecycleResult:
        category = error.category
        permission_categories = {"token_unavailable", "http_401", "http_403"}
        infrastructure_categories = {
            "transport_error",
            "git_push_failed",
            "http_429",
            *(f"http_{status}" for status in range(500, 600)),
        }
        if category == "git_push_rejected":
            permission_categories.add(category)
        if category not in permission_categories | infrastructure_categories:
            raise error
        digest = self._stable_digest(
            "github-adapter", repository, stage, category,
            snapshot.base_sha or "", snapshot.head_sha or "",
        )
        route = self.failure_router.route(
            snapshot,
            FailureEvidence(
                event_id=f"github:{digest}",
                root_cause_id=f"github:{digest[:24]}",
                source_kind="ci",
                base_sha=snapshot.base_sha or "",
                head_sha=snapshot.head_sha or "",
                current_conclusion="FAILURE",
                baseline_conclusion="UNKNOWN",
                same_head_conclusions=("FAILURE",),
                infrastructure_category=(
                    None
                    if category in permission_categories
                    else ("network" if category == "transport_error" else "service")
                ),
                human_decision_category=(
                    "permission" if category in permission_categories else None
                ),
            ),
        )
        store.save(snapshot)
        return LifecycleResult(
            snapshot.pull_request_number or 0,
            False,
            False,
            (route.reason,),
        )

    def _current_pull_request_facts(
        self,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        pull_request_number: int,
    ) -> PullRequestState:
        pull_request = self.client.get_pull_request(repository, pull_request_number)
        checks = self.client.collect_commit_checks(repository, snapshot.head_sha or "")
        packet = self.client.collect_review_packet(
            repository, pull_request_number, snapshot.head_sha or ""
        )
        if (
            packet.repository != repository
            or packet.pull_request_number != pull_request_number
            or packet.head_sha != snapshot.head_sha
        ):
            raise RuntimeError("review packet differs from current pull request identity")
        return replace(
            pull_request,
            checks=checks,
            unresolved_review_threads=packet.unresolved_actionable_threads,
        )

    def _resume_handoff(
        self,
        store: SnapshotStore,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        issue_number: int,
        workpad_body: str,
        reviewer: str | None,
    ) -> LifecycleResult:
        branch = snapshot.branch or ""
        pull_requests = self.client.list_open_pull_requests(repository, branch)
        if len(pull_requests) != 1:
            raise RuntimeError("human handoff requires exactly one open pull request")
        pull_request = pull_requests[0]
        checks = self.client.collect_commit_checks(repository, snapshot.head_sha or "")
        packet = self.client.collect_review_packet(
            repository, pull_request.number, snapshot.head_sha or ""
        )
        if (
            packet.repository != repository
            or packet.pull_request_number != pull_request.number
            or packet.head_sha != snapshot.head_sha
        ):
            raise RuntimeError("review packet differs from current pull request identity")
        pull_request = replace(
            pull_request,
            checks=checks,
            unresolved_review_threads=packet.unresolved_actionable_threads,
        )
        if pull_request.draft:
            raise RuntimeError("human handoff pull request unexpectedly became Draft")
        handoff = self.reconciler.handoff(
            snapshot,
            repository=repository,
            issue_number=issue_number,
            pull_request=pull_request,
            workpad_body=workpad_body,
            reviewer=reviewer,
            fact_guard=lambda: self._require_current_handoff_facts(
                snapshot,
                repository=repository,
                pull_request_number=pull_request.number,
            ),
        )
        verified_pull_request = self._current_pull_request_facts(
            snapshot,
            repository=repository,
            pull_request_number=pull_request.number,
        )
        if verified_pull_request.draft:
            raise RuntimeError("pull request became Draft during handoff")
        post_handoff_blockers = self.reconciler.readiness_blockers(
            snapshot, verified_pull_request
        )
        if post_handoff_blockers:
            raise RuntimeError(
                "pull request facts changed during handoff: "
                + "; ".join(post_handoff_blockers)
            )
        store.save(snapshot)
        return LifecycleResult(
            pull_request.number,
            True,
            handoff.complete,
            tuple(sorted(handoff.failures)),
        )

    def _require_current_handoff_facts(
        self,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        pull_request_number: int,
    ) -> None:
        current = self._current_pull_request_facts(
            snapshot,
            repository=repository,
            pull_request_number=pull_request_number,
        )
        if current.draft:
            raise RuntimeError("pull request became Draft during handoff")
        blockers = self.reconciler.readiness_blockers(snapshot, current)
        if blockers:
            raise RuntimeError(
                "pull request facts changed during handoff: " + "; ".join(blockers)
            )

    def _require_capability(self, name: str) -> None:
        if self.capabilities.get(name) is not True:
            raise RuntimeError(f"capability {name} is disabled")
