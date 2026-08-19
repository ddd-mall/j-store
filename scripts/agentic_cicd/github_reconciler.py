from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Callable, Mapping, Protocol

from .coordinator import TaskSnapshot
from .github_receipt import GitHubEventReceipt
from .pr_packet import PullRequestPacket


SUCCESSFUL_CHECK_CONCLUSIONS = {"SUCCESS", "NEUTRAL", "SKIPPED"}
REQUIRED_PR_SECTIONS = (
    "Intent",
    "Branch policy",
    "Evidence",
    "Independent review",
    "Residual risk",
)
WORKPAD_MARKER = "<!-- j-store-agentic-cicd-workpad -->"


@dataclass(frozen=True)
class PullRequestState:
    number: int
    base_branch: str
    head_branch: str
    head_sha: str
    draft: bool
    body: str
    checks: Mapping[str, str]
    unresolved_review_threads: int


@dataclass(frozen=True)
class HandoffResult:
    complete: bool
    events: Mapping[str, GitHubEventReceipt]
    failures: Mapping[str, str]


class GitHubMutationClient(Protocol):
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


class GitHubReconciler:
    """Applies capability-gated, exact-head GitHub state transitions."""

    def __init__(
        self,
        client: GitHubMutationClient,
        *,
        capabilities: Mapping[str, bool],
        required_checks: set[str] | frozenset[str],
    ):
        self.client = client
        self.capabilities = dict(capabilities)
        self.required_checks = frozenset(required_checks)
        if not self.required_checks:
            raise ValueError("required_checks must not be empty")

    def ensure_draft_pull_request(
        self,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        title: str,
        body: str,
    ) -> PullRequestState:
        self._require_draft_reconcile_state(snapshot)
        repository = self._nonblank(repository, "repository")
        title = self._nonblank(title, "pull request title")
        body = self._nonblank(body, "pull request body")
        branch, head_sha = self._candidate_identity(snapshot)
        self._require_exact_candidate_pass(snapshot)

        pull_requests = self.client.list_open_pull_requests(repository, branch)
        if len(pull_requests) > 1:
            raise RuntimeError("multiple open pull requests exist for the task branch")
        created = not pull_requests
        if pull_requests:
            pull_request = pull_requests[0]
            self._require_pull_request_identity(snapshot, pull_request)
            ready_intent = (
                f"ready-intent:{repository}:{pull_request.number}:{head_sha}"
                in snapshot.consumed_idempotency_keys
            )
            if not pull_request.draft and (
                pull_request.body != body or not ready_intent
            ):
                self._require_capability("create_draft_pull_request")
                pull_request = self.client.convert_pull_request_to_draft(
                    repository, pull_request.number, head_sha
                )
                self._require_pull_request_identity(snapshot, pull_request)
                if not pull_request.draft:
                    raise RuntimeError("reconciled pull request must be Draft")
            if pull_request.body != body:
                self._require_capability("create_draft_pull_request")
                pull_request = self.client.reconcile_pull_request_body(
                    repository, pull_request.number, head_sha, body
                )
                self._require_pull_request_identity(snapshot, pull_request)
                if not pull_request.draft or pull_request.body != body:
                    raise RuntimeError("Draft pull request body did not reconcile")
        else:
            for capability in (
                "create_remote_branch",
                "push_commit",
                "create_draft_pull_request",
            ):
                self._require_capability(capability)
            pull_request = self.client.create_draft_pull_request(
                repository, "develop", branch, title, body
            )
            self._require_pull_request_identity(snapshot, pull_request)
            if not pull_request.draft:
                raise RuntimeError("new pull request must be created as Draft")

        snapshot.pull_request_number = pull_request.number
        snapshot.github_events[f"draft-pr:{repository}:{branch}"] = GitHubEventReceipt(
            operation="draft-pr",
            repository=repository,
            resource_kind="pull_request",
            resource_id=str(pull_request.number),
            state="draft",
            source="mutation" if created else "observation",
            pull_request_number=pull_request.number,
            head_sha=pull_request.head_sha,
        ).to_json()
        snapshot.state = "waiting_ci"
        return pull_request

    @staticmethod
    def _require_draft_reconcile_state(snapshot: TaskSnapshot) -> None:
        if snapshot.state not in {"queued", "waiting_ci"}:
            raise RuntimeError(
                f"task state {snapshot.state} cannot reconcile a Draft pull request"
            )

    def readiness_blockers(
        self, snapshot: TaskSnapshot, pull_request: PullRequestState
    ) -> tuple[str, ...]:
        blockers: list[str] = []
        try:
            self._require_pull_request_identity(snapshot, pull_request)
        except RuntimeError as error:
            blockers.append(str(error))

        if snapshot.pull_request_number not in {None, pull_request.number}:
            blockers.append("pull request number differs from task state")
        if snapshot.state in {"blocked", "fused", "cancelled"}:
            blockers.append(f"task state {snapshot.state} cannot become Ready")
        if snapshot.pending_review_findings:
            blockers.append("unresolved independent review findings remain")
        try:
            self._require_exact_candidate_pass(snapshot)
        except RuntimeError as error:
            blockers.append(str(error))

        normalized_checks = {
            str(name): str(conclusion).upper()
            for name, conclusion in pull_request.checks.items()
        }
        failed_required = sorted(
            name
            for name in self.required_checks
            if normalized_checks.get(name) != "SUCCESS"
        )
        if failed_required:
            blockers.append(f"required checks are not successful: {failed_required}")
        incomplete_additional = sorted(
            name
            for name, conclusion in normalized_checks.items()
            if name not in self.required_checks
            and conclusion not in SUCCESSFUL_CHECK_CONCLUSIONS
        )
        if incomplete_additional:
            blockers.append(
                f"additional checks are failed or pending: {incomplete_additional}"
            )
        if pull_request.unresolved_review_threads != 0:
            blockers.append("actionable review threads remain unresolved")
        if not self._complete_pr_body(snapshot, pull_request.body):
            blockers.append("PR body is incomplete")
        return tuple(dict.fromkeys(blockers))

    def mark_ready(
        self,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        pull_request: PullRequestState,
    ) -> GitHubEventReceipt:
        repository = self._nonblank(repository, "repository")
        blockers = self.readiness_blockers(snapshot, pull_request)
        if blockers:
            raise RuntimeError("pull request is not ready: " + "; ".join(blockers))
        snapshot.pull_request_number = pull_request.number
        key = f"ready:{repository}:{pull_request.number}:{pull_request.head_sha}"
        existing = snapshot.github_events.get(key)
        if existing is not None and not pull_request.draft:
            receipt = GitHubEventReceipt.from_json(existing)
            self._require_ready_receipt(
                receipt, repository=repository, pull_request=pull_request
            )
            return receipt
        if not pull_request.draft:
            receipt = GitHubEventReceipt(
                operation="ready",
                repository=repository,
                resource_kind="pull_request",
                resource_id=str(pull_request.number),
                state="ready",
                source="observation",
                pull_request_number=pull_request.number,
                head_sha=pull_request.head_sha,
            )
        else:
            self._require_capability("mark_pull_request_ready")
            receipt = self.client.mark_pull_request_ready(
                repository,
                pull_request.number,
                pull_request.head_sha,
                pull_request.body,
            )
        self._require_ready_receipt(
            receipt, repository=repository, pull_request=pull_request
        )
        snapshot.github_events[key] = receipt.to_json()
        return receipt

    def handoff(
        self,
        snapshot: TaskSnapshot,
        *,
        repository: str,
        issue_number: int,
        pull_request: PullRequestState,
        workpad_body: str,
        reviewer: str | None,
        fact_guard: Callable[[], None] | None = None,
    ) -> HandoffResult:
        repository = self._nonblank(repository, "repository")
        if issue_number <= 0:
            raise ValueError("issue_number must be positive")
        if pull_request.draft:
            raise RuntimeError("pull request must already be Ready before handoff")
        if self.readiness_blockers(snapshot, pull_request):
            raise RuntimeError("pull request no longer satisfies Ready gates")

        signal_specs = []
        if self.capabilities.get("write_issue_comment"):
            signal_specs.append(
                (
                    "workpad",
                    lambda: self.client.upsert_workpad(
                        repository,
                        issue_number,
                        WORKPAD_MARKER,
                        self._nonblank(workpad_body, "workpad body"),
                    ),
                )
            )
        if self.capabilities.get("update_issue_labels"):
            signal_specs.append(
                (
                    "label",
                    lambda: self.client.replace_issue_state_label(
                        repository, issue_number, "agent:human-review"
                    ),
                )
            )
        if self.capabilities.get("request_pull_request_review"):
            signal_specs.append(
                (
                    "review-request",
                    lambda: self.client.request_pull_request_review(
                        repository,
                        pull_request.number,
                        pull_request.head_sha,
                        self._nonblank(reviewer, "configured reviewer"),
                    ),
                )
            )
        if not signal_specs:
            raise RuntimeError("no GitHub handoff capability is enabled")

        prefix = f"handoff:{repository}:{pull_request.number}:{pull_request.head_sha}"
        events: dict[str, GitHubEventReceipt] = {}
        failures: dict[str, str] = {}
        for signal, operation in signal_specs:
            if fact_guard is not None:
                fact_guard()
            key = f"{prefix}:{signal}"
            existing = snapshot.github_events.get(key)
            if existing is not None:
                receipt = GitHubEventReceipt.from_json(existing)
                self._require_handoff_receipt(
                    receipt,
                    signal=signal,
                    repository=repository,
                    issue_number=issue_number,
                    pull_request=pull_request,
                    reviewer=reviewer,
                )
                events[signal] = receipt
                snapshot.github_operational_findings.pop(key, None)
                if fact_guard is not None:
                    fact_guard()
                continue
            try:
                receipt = operation()
                self._require_handoff_receipt(
                    receipt,
                    signal=signal,
                    repository=repository,
                    issue_number=issue_number,
                    pull_request=pull_request,
                    reviewer=reviewer,
                )
            except Exception as error:  # The adapter owns retryable transport details.
                failure = f"{type(error).__name__}: GitHub {signal} signal failed"
                failures[signal] = failure
                snapshot.github_operational_findings[key] = failure
            else:
                snapshot.github_events[key] = receipt.to_json()
                snapshot.github_operational_findings.pop(key, None)
                events[signal] = receipt
            if fact_guard is not None:
                fact_guard()

        complete = bool(events)
        if complete:
            snapshot.github_events[prefix] = GitHubEventReceipt(
                operation="handoff",
                repository=repository,
                resource_kind="pull_request",
                resource_id=str(pull_request.number),
                state="complete",
                source="observation",
                pull_request_number=pull_request.number,
                head_sha=pull_request.head_sha,
            ).to_json()
            snapshot.handoff_head_sha = pull_request.head_sha
            snapshot.state = "human_review"
        return HandoffResult(complete=complete, events=events, failures=failures)

    @staticmethod
    def _require_ready_receipt(
        receipt: GitHubEventReceipt,
        *,
        repository: str,
        pull_request: PullRequestState,
    ) -> None:
        if not isinstance(receipt, GitHubEventReceipt):
            raise RuntimeError("GitHub ready receipt has invalid type")
        if receipt.operation != "ready":
            raise RuntimeError("GitHub ready receipt operation differs")
        if receipt.repository != repository:
            raise RuntimeError("GitHub ready receipt repository differs")
        if receipt.resource_kind != "pull_request" or receipt.state != "ready":
            raise RuntimeError("GitHub ready receipt state differs")
        if receipt.pull_request_number != pull_request.number:
            raise RuntimeError("GitHub ready receipt pull request differs")
        if receipt.head_sha != pull_request.head_sha:
            raise RuntimeError("GitHub ready receipt head differs")

    @staticmethod
    def _require_handoff_receipt(
        receipt: GitHubEventReceipt,
        *,
        signal: str,
        repository: str,
        issue_number: int,
        pull_request: PullRequestState,
        reviewer: str | None,
    ) -> None:
        if not isinstance(receipt, GitHubEventReceipt):
            raise RuntimeError(f"GitHub {signal} receipt has invalid type")
        if receipt.operation != signal:
            raise RuntimeError(f"GitHub {signal} receipt operation differs")
        if receipt.repository != repository:
            raise RuntimeError(f"GitHub {signal} receipt repository differs")
        if signal == "workpad":
            valid = (
                receipt.resource_kind == "issue_comment"
                and receipt.state == "current"
                and receipt.issue_number == issue_number
                and receipt.resource_id.isdigit()
                and receipt.detail is not None
            )
        elif signal == "label":
            valid = (
                receipt.resource_kind == "issue"
                and receipt.state == "applied"
                and receipt.issue_number == issue_number
                and receipt.resource_id == str(issue_number)
                and receipt.detail == "agent:human-review"
            )
        else:
            valid = (
                receipt.resource_kind == "pull_request"
                and receipt.state == "requested"
                and receipt.pull_request_number == pull_request.number
                and receipt.resource_id == str(pull_request.number)
                and receipt.head_sha == pull_request.head_sha
                and receipt.detail == reviewer
            )
        if not valid:
            raise RuntimeError(f"GitHub {signal} receipt target or state differs")

    def _require_pull_request_identity(
        self, snapshot: TaskSnapshot, pull_request: PullRequestState
    ) -> None:
        branch, head_sha = self._candidate_identity(snapshot)
        if pull_request.number <= 0:
            raise RuntimeError("pull request number is invalid")
        if pull_request.base_branch != "develop":
            raise RuntimeError("pull request base must be develop")
        if pull_request.head_branch != branch:
            raise RuntimeError("pull request head branch differs from task state")
        if pull_request.head_sha != head_sha:
            raise RuntimeError("pull request head SHA differs from task state")

    @staticmethod
    def _candidate_identity(snapshot: TaskSnapshot) -> tuple[str, str]:
        if not snapshot.branch or not snapshot.head_sha:
            raise RuntimeError("task has no trusted branch and head SHA")
        return snapshot.branch, snapshot.head_sha

    @staticmethod
    def _require_exact_candidate_pass(snapshot: TaskSnapshot) -> None:
        if snapshot.candidate_revision is None:
            raise RuntimeError("task has no frozen candidate")
        candidate_revision = snapshot.candidate_revision.get("candidate_revision")
        decision = (
            snapshot.review_decision_for(candidate_revision)
            if isinstance(candidate_revision, str)
            else None
        )
        if (
            decision is None
            or decision.verdict != "PASS"
            or decision.head_sha != snapshot.head_sha
            or decision.candidate_revision != candidate_revision
        ):
            raise RuntimeError("exact candidate has no independent review PASS")

    def _require_capability(self, capability: str) -> None:
        if self.capabilities.get(capability) is not True:
            raise RuntimeError(f"capability {capability} is disabled")

    @staticmethod
    def _nonblank(value: str | None, field_name: str) -> str:
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{field_name} must not be blank")
        return value.strip()

    @staticmethod
    def _complete_pr_body(snapshot: TaskSnapshot, body: str) -> bool:
        if snapshot.pull_request_packet is None:
            return False
        try:
            expected = PullRequestPacket.from_json(snapshot.pull_request_packet)
            observed = PullRequestPacket.parse(body)
        except (TypeError, ValueError):
            return False
        return (
            observed == expected
            and observed.issue_identifier == snapshot.issue_identifier
            and observed.candidate_revision
            == (snapshot.candidate_revision or {}).get("candidate_revision")
            and observed.promoted_head_sha == snapshot.head_sha
            and observed.source_branch == snapshot.branch
            and observed.target_branch == "develop"
            and observed.approvals_resolved
            and all(section in body for section in (f"## {name}" for name in REQUIRED_PR_SECTIONS))
        )
