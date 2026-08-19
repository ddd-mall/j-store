from __future__ import annotations

import json
import math
import os
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Protocol

from .coordinator import TaskSnapshot
from .github_receipt import GitHubEventReceipt
from .github_reconciler import PullRequestState, WORKPAD_MARKER
from .process_environment import trusted_process_environment
from .protocol import ReviewCommentFeedback, ReviewPacket, ReviewThreadFeedback


HTTP_TIMEOUT_SECONDS = 30
MAXIMUM_RESPONSE_BYTES = 4 * 1024 * 1024
GIT_TIMEOUT_SECONDS = 60
TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS = 5
MAXIMUM_GITHUB_PAGES = 100
MAXIMUM_CONFLICT_ATTEMPTS = 3
GITHUB_HUMAN_LOGIN = re.compile(
    r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?"
)
GITHUB_APP_BOT_LOGIN = re.compile(
    r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,98}[A-Za-z0-9])?\[bot\]"
)
AGENT_STATE_LABELS = frozenset(
    {
        "agent:candidate",
        "agent:queued",
        "agent:waiting-ci",
        "agent:human-review",
        "agent:blocked",
        "agent:fused",
        "agent:cancelled",
    }
)
READY_MUTATION = """mutation MarkPullRequestReady($pullRequestId: ID!) {
  markPullRequestReadyForReview(input: {pullRequestId: $pullRequestId}) {
    pullRequest { id isDraft }
  }
}"""
DRAFT_MUTATION = """mutation ConvertPullRequestToDraft($pullRequestId: ID!) {
  convertPullRequestToDraft(input: {pullRequestId: $pullRequestId}) {
    pullRequest { id isDraft }
  }
}"""
REVIEW_THREADS_QUERY = """query ReviewThreads(
  $owner: String!, $name: String!, $number: Int!, $after: String
) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      number
      headRefOid
      reviewThreads(first: 100, after: $after) {
        nodes {
          id isResolved path line originalLine
          comments(first: 100) {
            nodes {
              id body outdated createdAt updatedAt
              author { login }
              commit { oid }
            }
            pageInfo { hasNextPage endCursor }
          }
        }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}"""
REVIEW_THREAD_COMMENTS_QUERY = """query ReviewThreadComments(
  $threadId: ID!, $after: String
) {
  node(id: $threadId) {
    ... on PullRequestReviewThread {
      id
      pullRequest { number headRefOid }
      comments(first: 100, after: $after) {
        nodes {
          id body outdated createdAt updatedAt
          author { login }
          commit { oid }
        }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}"""


def _parse_repository(repository: str) -> tuple[str, str]:
    if not isinstance(repository, str) or repository.count("/") != 1:
        raise ValueError("repository must use owner/name")
    owner, name = repository.split("/", 1)
    allowed = frozenset(
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_."
    )
    if not owner or not name or set(owner + name) - allowed:
        raise ValueError("repository must use a safe owner/name")
    return owner, name


class GitHubAdapterError(RuntimeError):
    def __init__(self, category: str):
        self.category = category
        super().__init__(f"GitHub host adapter failed: {category}")


class InstallationToken:
    __slots__ = ("_value", "expires_at_epoch_seconds")

    def __init__(self, value: str, *, expires_at_epoch_seconds: float):
        self._value = value
        self.expires_at_epoch_seconds = expires_at_epoch_seconds

    def __repr__(self) -> str:
        return "InstallationToken(value=<redacted>)"

    __str__ = __repr__

    def usable_value(self, *, now: float, minimum_lifetime_seconds: int) -> str:
        if (
            not isinstance(self._value, str)
            or not self._value
            or any(character.isspace() for character in self._value)
            or not isinstance(self.expires_at_epoch_seconds, (int, float))
            or not math.isfinite(self.expires_at_epoch_seconds)
            or self.expires_at_epoch_seconds - now < minimum_lifetime_seconds
        ):
            raise GitHubAdapterError("token_unavailable")
        return self._value


class InstallationTokenProvider(Protocol):
    def get_token(self) -> InstallationToken | None: ...


class EnvironmentInstallationTokenProvider:
    """Builds one redacted token lease from host-injected runtime metadata."""

    def __init__(self, environment: Mapping[str, str] | None = None):
        self._environment = environment

    def get_token(self) -> InstallationToken | None:
        environment = self._environment if self._environment is not None else os.environ
        value = environment.get("JSTORE_SYMPHONY_GITHUB_TOKEN")
        expires = environment.get("JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS")
        if value is None or expires is None:
            return None
        try:
            expires_at = float(expires)
        except ValueError:
            return None
        return InstallationToken(value, expires_at_epoch_seconds=expires_at)


def validate_handoff_logins(
    *,
    github_app_login: str | None,
    reviewer: str | None,
    require_app_login: bool,
    require_reviewer: bool,
) -> tuple[str | None, str | None]:
    normalized_app_login = github_app_login if require_app_login else None
    normalized_reviewer = reviewer if require_reviewer else None
    if (
        isinstance(normalized_app_login, str)
        and isinstance(normalized_reviewer, str)
        and normalized_app_login.casefold() == normalized_reviewer.casefold()
    ):
        raise ValueError("GitHub App and reviewer login must be different")
    if require_app_login and (
        not isinstance(normalized_app_login, str)
        or GITHUB_APP_BOT_LOGIN.fullmatch(normalized_app_login) is None
    ):
        raise ValueError("GitHub App login must be a safe <app>[bot] login")
    if require_reviewer and (
        not isinstance(normalized_reviewer, str)
        or GITHUB_HUMAN_LOGIN.fullmatch(normalized_reviewer) is None
    ):
        raise ValueError("reviewer login must be a safe human GitHub login")
    return normalized_app_login, normalized_reviewer


def validate_github_runtime_prerequisites(
    *,
    token_provider: InstallationTokenProvider,
    capabilities: Mapping[str, bool],
    github_app_login: str | None,
    reviewer: str | None,
    now: float | None = None,
) -> tuple[str | None, str | None]:
    """Fails before promotion, Git, or GitHub calls when Level 2 inputs drift."""
    try:
        token = token_provider.get_token()
    except Exception:
        raise GitHubAdapterError("token_unavailable") from None
    if token is None:
        raise GitHubAdapterError("token_unavailable")
    try:
        token.usable_value(
            now=time.time() if now is None else now,
            minimum_lifetime_seconds=(
                max(GIT_TIMEOUT_SECONDS, HTTP_TIMEOUT_SECONDS)
                + TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS
            ),
        )
    except GitHubAdapterError:
        raise
    except Exception:
        raise GitHubAdapterError("token_unavailable") from None
    return validate_handoff_logins(
        github_app_login=github_app_login,
        reviewer=reviewer,
        require_app_login=capabilities.get("write_issue_comment") is True,
        require_reviewer=capabilities.get("request_pull_request_review") is True,
    )


@dataclass(frozen=True)
class HttpResponse:
    status: int
    headers: Mapping[str, str]
    body: bytes


class HttpTransport(Protocol):
    def request(
        self,
        *,
        method: str,
        url: str,
        headers: Mapping[str, str],
        body: bytes | None,
    ) -> HttpResponse: ...


@dataclass(frozen=True)
class _CheckFact:
    name: str
    producer: tuple[object, ...]
    order: tuple[float, int]
    state: str


class _RejectRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class UrllibGitHubTransport:
    """Small HTTPS transport; endpoint selection stays in the adapter."""

    def __init__(self):
        self._opener = urllib.request.build_opener(_RejectRedirects())

    def request(
        self,
        *,
        method: str,
        url: str,
        headers: Mapping[str, str],
        body: bytes | None,
    ) -> HttpResponse:
        request = urllib.request.Request(
            url, data=body, headers=dict(headers), method=method
        )
        try:
            with self._opener.open(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
                return HttpResponse(
                    status=response.status,
                    headers=dict(response.headers.items()),
                    body=self._bounded_body(response),
                )
        except urllib.error.HTTPError as error:
            return HttpResponse(
                status=error.code,
                headers=dict(error.headers.items()) if error.headers else {},
                body=self._bounded_body(error),
            )

    @staticmethod
    def _bounded_body(response) -> bytes:
        body = response.read(MAXIMUM_RESPONSE_BYTES + 1)
        if len(body) > MAXIMUM_RESPONSE_BYTES:
            raise GitHubAdapterError("response_too_large")
        return body


class GitHubRestGraphqlAdapter:
    """Allowlisted GitHub operations for the Level 2 pull-request loop."""

    def __init__(
        self,
        *,
        token_provider: InstallationTokenProvider,
        transport: HttpTransport | None = None,
        api_url: str = "https://api.github.com",
        workpad_author_login: str | None = None,
    ):
        if api_url != "https://api.github.com":
            raise ValueError("only the canonical GitHub API endpoint is supported")
        self._token_provider = token_provider
        self._transport = transport or UrllibGitHubTransport()
        self._api_url = api_url
        self._workpad_author_login = (
            self._nonblank(workpad_author_login, "workpad author login")
            if workpad_author_login is not None
            else None
        )

    def list_open_pull_requests(
        self, repository: str, head_branch: str
    ) -> tuple[PullRequestState, ...]:
        owner, repository_name = _parse_repository(repository)
        head_branch = self._nonblank(head_branch, "head branch")
        query = urllib.parse.urlencode(
            {
                "state": "open",
                "head": f"{owner}:{head_branch}",
                "per_page": "100",
            }
        )
        payload = self._request_json(
            "GET",
            f"/repos/{owner}/{repository_name}/pulls?{query}",
            expected_status=200,
        )
        if not isinstance(payload, list):
            raise GitHubAdapterError("invalid_response")
        return tuple(self._pull_request(item) for item in payload)

    def get_pull_request(
        self, repository: str, pull_request_number: int
    ) -> PullRequestState:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(pull_request_number, "pull request number")
        return self._pull_request(
            self._request_json(
                "GET",
                f"/repos/{owner}/{repository_name}/pulls/{number}",
                expected_status=200,
            )
        )

    def create_draft_pull_request(
        self,
        repository: str,
        base_branch: str,
        head_branch: str,
        title: str,
        body: str,
    ) -> PullRequestState:
        owner, repository_name = _parse_repository(repository)
        payload = self._request_json(
            "POST",
            f"/repos/{owner}/{repository_name}/pulls",
            payload={
                "base": self._nonblank(base_branch, "base branch"),
                "head": self._nonblank(head_branch, "head branch"),
                "title": self._nonblank(title, "pull request title"),
                "body": self._nonblank(body, "pull request body"),
                "draft": True,
            },
            expected_status=201,
        )
        return self._pull_request(payload)

    def convert_pull_request_to_draft(
        self,
        repository: str,
        pull_request_number: int,
        expected_head_sha: str,
    ) -> PullRequestState:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(pull_request_number, "pull request number")
        self._full_sha(expected_head_sha)
        path = f"/repos/{owner}/{repository_name}/pulls/{number}"
        current_payload = self._request_json("GET", path, expected_status=200)
        current = self._pull_request(current_payload)
        self._require_expected_pull_request(current, expected_head_sha=expected_head_sha)
        if current.draft:
            return current
        try:
            node_id = self._nonblank(current_payload["node_id"], "pull request node id")
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        response = self._request_json(
            "POST",
            "/graphql",
            payload={
                "query": DRAFT_MUTATION,
                "variables": {"pullRequestId": node_id},
            },
            expected_status=200,
        )
        try:
            draft = response["data"]["convertPullRequestToDraft"]["pullRequest"]
            if draft["id"] != node_id or draft["isDraft"] is not True:
                raise KeyError
        except (KeyError, TypeError):
            raise GitHubAdapterError("graphql_rejected") from None
        observed = self.get_pull_request(repository, number)
        self._require_expected_pull_request(
            observed, expected_head_sha=expected_head_sha, expected_draft=True
        )
        return observed

    def reconcile_pull_request_body(
        self,
        repository: str,
        pull_request_number: int,
        expected_head_sha: str,
        body: str,
    ) -> PullRequestState:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(pull_request_number, "pull request number")
        self._full_sha(expected_head_sha)
        body = self._nonblank(body, "pull request body")
        path = f"/repos/{owner}/{repository_name}/pulls/{number}"
        for _attempt in range(MAXIMUM_CONFLICT_ATTEMPTS):
            payload, headers = self._request_json_response(
                "GET", path, expected_status=200
            )
            current = self._pull_request(payload)
            self._require_expected_pull_request(
                current, expected_head_sha=expected_head_sha, expected_draft=True
            )
            if current.body == body:
                return current
            try:
                updated_payload = self._request_json(
                    "PATCH",
                    path,
                    payload={"body": body},
                    expected_status=200,
                    if_match=self._etag(headers),
                )
            except GitHubAdapterError as error:
                if error.category in {"http_409", "http_412"}:
                    continue
                raise
            updated = self._pull_request(updated_payload)
            self._require_expected_pull_request(
                updated, expected_head_sha=expected_head_sha, expected_draft=True
            )
            if updated.body != body:
                raise GitHubAdapterError("pull_request_body_response_conflict")
            return updated
        raise GitHubAdapterError("pull_request_body_conflict")

    def mark_pull_request_ready(
        self,
        repository: str,
        pull_request_number: int,
        head_sha: str,
        body: str,
    ) -> GitHubEventReceipt:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(pull_request_number, "pull request number")
        self._full_sha(head_sha)
        body = self._nonblank(body, "pull request body")
        pull_request = self._request_json(
            "GET",
            f"/repos/{owner}/{repository_name}/pulls/{number}",
            expected_status=200,
        )
        if not isinstance(pull_request, dict):
            raise GitHubAdapterError("invalid_response")
        try:
            node_id = self._nonblank(
                pull_request.get("node_id"), "pull request node id"
            )
            observed_head_sha = self._nonblank(
                pull_request["head"]["sha"], "pull request head"
            )
            observed_base = self._nonblank(
                pull_request["base"]["ref"], "pull request base"
            )
            observed_body = pull_request["body"]
            observed_draft = pull_request["draft"]
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        if (
            observed_head_sha != head_sha
            or observed_base != "develop"
            or observed_body != body
            or observed_draft is not True
        ):
            raise GitHubAdapterError("pull_request_ready_precondition_conflict")
        response = self._request_json(
            "POST",
            "/graphql",
            payload={
                "query": READY_MUTATION,
                "variables": {"pullRequestId": node_id},
            },
            expected_status=200,
        )
        try:
            ready = response["data"]["markPullRequestReadyForReview"]["pullRequest"]
            if ready["id"] != node_id or ready["isDraft"] is not False:
                raise KeyError
        except (KeyError, TypeError):
            raise GitHubAdapterError("graphql_rejected") from None
        observed = self.get_pull_request(repository, number)
        self._require_expected_pull_request(
            observed,
            expected_head_sha=head_sha,
            expected_body=body,
            expected_draft=False,
        )
        return GitHubEventReceipt(
            operation="ready",
            repository=repository,
            resource_kind="pull_request",
            resource_id=node_id,
            state="ready",
            source="mutation",
            pull_request_number=number,
            head_sha=observed_head_sha,
        )

    def request_pull_request_review(
        self,
        repository: str,
        pull_request_number: int,
        head_sha: str,
        reviewer: str,
    ) -> GitHubEventReceipt:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(pull_request_number, "pull request number")
        if len(head_sha) != 40 or any(
            character not in "0123456789abcdef" for character in head_sha
        ):
            raise ValueError("head SHA must be a full lowercase commit SHA")
        reviewer = self._nonblank(reviewer, "reviewer")
        before = self.get_pull_request(repository, number)
        self._require_expected_pull_request(before, expected_head_sha=head_sha)
        path = (
            f"/repos/{owner}/{repository_name}/pulls/{number}/requested_reviewers"
        )
        existing = self._request_json("GET", path, expected_status=200)
        if reviewer in self._requested_reviewer_logins(existing):
            after = self.get_pull_request(repository, number)
            self._require_expected_pull_request(after, expected_head_sha=head_sha)
            return self._review_request_receipt(
                repository, number, head_sha, reviewer, source="observation"
            )
        self._request_json(
            "POST",
            path,
            payload={"reviewers": [reviewer]},
            expected_status=201,
        )
        observed = self._request_json("GET", path, expected_status=200)
        if reviewer not in self._requested_reviewer_logins(observed):
            raise GitHubAdapterError("review_request_response_conflict")
        after = self.get_pull_request(repository, number)
        self._require_expected_pull_request(after, expected_head_sha=head_sha)
        return self._review_request_receipt(
            repository, number, head_sha, reviewer, source="mutation"
        )

    def upsert_workpad(
        self, repository: str, issue_number: int, marker: str, body: str
    ) -> GitHubEventReceipt:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(issue_number, "issue number")
        if marker != WORKPAD_MARKER:
            raise ValueError("unsupported Workpad marker")
        body = self._nonblank(body, "workpad body")
        if marker in body:
            raise ValueError("workpad body must not contain the marker")
        if self._workpad_author_login is None:
            raise RuntimeError("workpad author login is not configured")
        desired_body = f"{marker}\n\n{body}"

        for _attempt in range(MAXIMUM_CONFLICT_ATTEMPTS):
            comments = self._list_issue_comments(owner, repository_name, number)
            matches = [
                comment
                for comment in comments
                if isinstance(comment, dict)
                and isinstance(comment.get("body"), str)
                and marker in comment["body"]
            ]
            if len(matches) > 1:
                raise GitHubAdapterError("workpad_not_unique")
            if not matches:
                try:
                    created = self._request_json(
                        "POST",
                        f"/repos/{owner}/{repository_name}/issues/{number}/comments",
                        payload={"body": desired_body},
                        expected_status=201,
                    )
                except GitHubAdapterError as error:
                    if error.category in {"http_409", "http_412"}:
                        continue
                    raise
                self._workpad_identity(created, expected_body=desired_body)
                verified = self._list_issue_comments(owner, repository_name, number)
                verified_matches = [
                    comment
                    for comment in verified
                    if isinstance(comment, dict)
                    and isinstance(comment.get("body"), str)
                    and marker in comment["body"]
                ]
                if len(verified_matches) != 1:
                    raise GitHubAdapterError("workpad_not_unique")
                comment_id, updated_at = self._workpad_identity(
                    verified_matches[0], expected_body=desired_body
                )
                return self._workpad_receipt(
                    repository, number, comment_id, updated_at, source="mutation"
                )

            listed_id, _ = self._workpad_identity(matches[0])
            current, headers = self._request_json_response(
                "GET",
                f"/repos/{owner}/{repository_name}/issues/comments/{listed_id}",
                expected_status=200,
            )
            comment_id, updated_at = self._workpad_identity(current)
            if comment_id != listed_id:
                raise GitHubAdapterError("workpad_identity_conflict")
            etag = self._etag(headers)
            if current["body"] == desired_body:
                return self._workpad_receipt(
                    repository, number, comment_id, updated_at, source="observation"
                )
            try:
                updated = self._request_json(
                    "PATCH",
                    f"/repos/{owner}/{repository_name}/issues/comments/{comment_id}",
                    payload={"body": desired_body},
                    expected_status=200,
                    if_match=etag,
                )
            except GitHubAdapterError as error:
                if error.category in {"http_409", "http_412"}:
                    continue
                raise
            updated_id, updated_at = self._workpad_identity(
                updated, expected_body=desired_body
            )
            if updated_id != comment_id:
                raise GitHubAdapterError("workpad_identity_conflict")
            return self._workpad_receipt(
                repository, number, updated_id, updated_at, source="mutation"
            )
        raise GitHubAdapterError("workpad_conflict")

    def replace_issue_state_label(
        self, repository: str, issue_number: int, label: str
    ) -> GitHubEventReceipt:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(issue_number, "issue number")
        if label not in AGENT_STATE_LABELS:
            raise ValueError("unsupported agent state label")

        for _attempt in range(MAXIMUM_CONFLICT_ATTEMPTS):
            issue, headers = self._request_json_response(
                "GET",
                f"/repos/{owner}/{repository_name}/issues/{number}",
                expected_status=200,
            )
            if not isinstance(issue, dict):
                raise GitHubAdapterError("invalid_response")
            current = self._label_names(issue.get("labels"))
            etag = self._etag(headers)
            desired = (current - AGENT_STATE_LABELS) | {label}
            if current == desired:
                return self._label_receipt(
                    repository, number, label, source="observation"
                )
            try:
                updated = self._request_json(
                    "PUT",
                    f"/repos/{owner}/{repository_name}/issues/{number}/labels",
                    payload={"labels": sorted(desired)},
                    expected_status=200,
                    if_match=etag,
                )
            except GitHubAdapterError as error:
                if error.category in {"http_409", "http_412"}:
                    continue
                raise
            if self._label_names(updated) != desired:
                raise GitHubAdapterError("label_response_conflict")
            return self._label_receipt(repository, number, label, source="mutation")
        raise GitHubAdapterError("label_conflict")

    def collect_commit_checks(
        self, repository: str, head_sha: str
    ) -> Mapping[str, str]:
        owner, repository_name = _parse_repository(repository)
        if len(head_sha) != 40 or any(
            character not in "0123456789abcdef" for character in head_sha
        ):
            raise ValueError("head SHA must be a full lowercase commit SHA")

        check_runs = self._paginated_collection(
            path_prefix=(
                f"/repos/{owner}/{repository_name}/commits/{head_sha}/check-runs"
                "?filter=latest&per_page=100&page="
            ),
            collection_name="check_runs",
            incomplete_category="incomplete_check_runs",
        )
        statuses = self._paginated_collection(
            path_prefix=(
                f"/repos/{owner}/{repository_name}/commits/{head_sha}/status"
                "?per_page=100&page="
            ),
            collection_name="statuses",
            incomplete_category="incomplete_statuses",
        )
        facts = [self._check_run_fact(value) for value in check_runs]
        facts.extend(self._status_context_fact(value) for value in statuses)

        latest: dict[tuple[str, tuple[object, ...]], _CheckFact] = {}
        for fact in facts:
            key = (fact.name, fact.producer)
            existing = latest.get(key)
            if existing is None or fact.order > existing.order:
                latest[key] = fact
            elif fact.order == existing.order and fact.state != existing.state:
                raise GitHubAdapterError("conflicting_check_attempt")

        states_by_name: dict[str, set[str]] = {}
        for fact in latest.values():
            states_by_name.setdefault(fact.name, set()).add(fact.state)
        return {
            name: next(iter(states)) if len(states) == 1 else "CONFLICT"
            for name, states in sorted(states_by_name.items())
        }

    def collect_review_packet(
        self, repository: str, pull_request_number: int, head_sha: str
    ) -> ReviewPacket:
        owner, repository_name = _parse_repository(repository)
        number = self._positive_number(
            pull_request_number, "pull request number"
        )
        if len(head_sha) != 40 or any(
            character not in "0123456789abcdef" for character in head_sha
        ):
            raise ValueError("head SHA must be a full lowercase commit SHA")

        raw_threads: list[dict[str, object]] = []
        after: str | None = None
        seen_cursors: set[str] = set()
        seen_thread_ids: set[str] = set()
        for _page in range(MAXIMUM_GITHUB_PAGES):
            response = self._request_json(
                "POST",
                "/graphql",
                payload={
                    "query": REVIEW_THREADS_QUERY,
                    "variables": {
                        "owner": owner,
                        "name": repository_name,
                        "number": number,
                        "after": after,
                    },
                },
                expected_status=200,
            )
            pull_request = self._review_pull_request(response, number, head_sha)
            connection = pull_request.get("reviewThreads")
            nodes, has_next, cursor = self._connection_page(connection)
            for node in nodes:
                if not isinstance(node, dict):
                    raise GitHubAdapterError("invalid_response")
                thread_id = self._required_node_id(node, "review thread id")
                if thread_id in seen_thread_ids:
                    raise GitHubAdapterError("duplicate_review_thread")
                seen_thread_ids.add(thread_id)
                raw_threads.append(node)
            if not has_next:
                break
            after = self._next_cursor(cursor, seen_cursors)
        else:
            raise GitHubAdapterError("pagination_limit")

        feedback: list[ReviewThreadFeedback] = []
        seen_comment_ids: set[str] = set()
        for thread in raw_threads:
            feedback.extend(
                self._review_thread_feedback(
                    thread,
                    pull_request_number=number,
                    head_sha=head_sha,
                    seen_comment_ids=seen_comment_ids,
                )
            )
        try:
            return ReviewPacket(
                repository=repository,
                pull_request_number=number,
                head_sha=head_sha,
                threads=tuple(feedback),
            )
        except ValueError:
            raise GitHubAdapterError("invalid_response") from None

    def _request_json(
        self,
        method: str,
        path: str,
        *,
        payload: object | None = None,
        expected_status: int,
        if_match: str | None = None,
    ) -> object:
        decoded, _headers = self._request_json_response(
            method,
            path,
            payload=payload,
            expected_status=expected_status,
            if_match=if_match,
        )
        return decoded

    def _request_json_response(
        self,
        method: str,
        path: str,
        *,
        payload: object | None = None,
        expected_status: int,
        if_match: str | None = None,
    ) -> tuple[object, Mapping[str, str]]:
        token = self._token()
        body = (
            json.dumps(payload, separators=(",", ":")).encode()
            if payload is not None
            else None
        )
        headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "j-store-agentic-cicd",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if body is not None:
            headers["Content-Type"] = "application/json"
        if if_match is not None:
            headers["If-Match"] = if_match
        try:
            response = self._transport.request(
                method=method,
                url=self._api_url + path,
                headers=headers,
                body=body,
            )
        except GitHubAdapterError:
            raise
        except Exception:
            raise GitHubAdapterError("transport_error") from None
        if response.status != expected_status:
            raise GitHubAdapterError(f"http_{response.status}")
        try:
            decoded = json.loads(response.body)
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise GitHubAdapterError("invalid_json") from None
        return decoded, response.headers

    def _list_issue_comments(
        self, owner: str, repository_name: str, issue_number: int
    ) -> tuple[object, ...]:
        comments: list[object] = []
        for page in range(1, MAXIMUM_GITHUB_PAGES + 1):
            payload = self._request_json(
                "GET",
                f"/repos/{owner}/{repository_name}/issues/{issue_number}/comments"
                f"?per_page=100&page={page}",
                expected_status=200,
            )
            if not isinstance(payload, list):
                raise GitHubAdapterError("invalid_response")
            comments.extend(payload)
            if len(payload) < 100:
                return tuple(comments)
        raise GitHubAdapterError("pagination_limit")

    def _paginated_collection(
        self,
        *,
        path_prefix: str,
        collection_name: str,
        incomplete_category: str,
    ) -> tuple[object, ...]:
        collected: list[object] = []
        expected_total: int | None = None
        for page in range(1, MAXIMUM_GITHUB_PAGES + 1):
            payload = self._request_json(
                "GET", f"{path_prefix}{page}", expected_status=200
            )
            if not isinstance(payload, dict):
                raise GitHubAdapterError("invalid_response")
            total_count = payload.get("total_count")
            values = payload.get(collection_name)
            if (
                not isinstance(total_count, int)
                or isinstance(total_count, bool)
                or total_count < 0
                or not isinstance(values, list)
            ):
                raise GitHubAdapterError("invalid_response")
            if expected_total is None:
                expected_total = total_count
            elif total_count != expected_total:
                raise GitHubAdapterError(incomplete_category)
            collected.extend(values)
            if len(values) < 100:
                if len(collected) != expected_total:
                    raise GitHubAdapterError(incomplete_category)
                return tuple(collected)
        raise GitHubAdapterError("pagination_limit")

    def _review_pull_request(
        self, response: object, expected_number: int, expected_head_sha: str
    ) -> dict[str, object]:
        try:
            if not isinstance(response, dict) or response.get("errors"):
                raise KeyError
            pull_request = response["data"]["repository"]["pullRequest"]
            if not isinstance(pull_request, dict):
                raise KeyError
            number = pull_request["number"]
            head_sha = pull_request["headRefOid"]
        except (KeyError, TypeError):
            raise GitHubAdapterError("graphql_rejected") from None
        if number != expected_number:
            raise GitHubAdapterError("pull_request_identity_changed")
        if head_sha != expected_head_sha:
            raise GitHubAdapterError("head_changed")
        return pull_request

    def _review_thread_feedback(
        self,
        thread: dict[str, object],
        *,
        pull_request_number: int,
        head_sha: str,
        seen_comment_ids: set[str],
    ) -> tuple[ReviewThreadFeedback, ...]:
        try:
            thread_id = self._required_node_id(thread, "review thread id")
            resolved = thread["isResolved"]
            path = self._nonblank(thread["path"], "review path")
            line = thread.get("line")
            original_line = thread.get("originalLine")
            if not isinstance(resolved, bool):
                raise KeyError
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None

        comments_connection = thread.get("comments")
        nodes, has_next, cursor = self._connection_page(comments_connection)
        raw_comments = list(nodes)
        after = cursor
        seen_cursors: set[str] = set()
        pages = 1
        while has_next:
            if pages >= MAXIMUM_GITHUB_PAGES:
                raise GitHubAdapterError("pagination_limit")
            after = self._next_cursor(after, seen_cursors)
            response = self._request_json(
                "POST",
                "/graphql",
                payload={
                    "query": REVIEW_THREAD_COMMENTS_QUERY,
                    "variables": {"threadId": thread_id, "after": after},
                },
                expected_status=200,
            )
            try:
                if not isinstance(response, dict) or response.get("errors"):
                    raise KeyError
                node = response["data"]["node"]
                if not isinstance(node, dict) or node["id"] != thread_id:
                    raise KeyError
                pull_request = node["pullRequest"]
                if not isinstance(pull_request, dict):
                    raise KeyError
                if pull_request["number"] != pull_request_number:
                    raise GitHubAdapterError("pull_request_identity_changed")
                if pull_request["headRefOid"] != head_sha:
                    raise GitHubAdapterError("head_changed")
                comments_connection = node["comments"]
            except GitHubAdapterError:
                raise
            except (KeyError, TypeError):
                raise GitHubAdapterError("graphql_rejected") from None
            page_nodes, has_next, cursor = self._connection_page(
                comments_connection
            )
            raw_comments.extend(page_nodes)
            after = cursor
            pages += 1
        if not raw_comments:
            raise GitHubAdapterError("invalid_response")

        actionable: list[ReviewCommentFeedback] = []
        audit: list[ReviewCommentFeedback] = []
        for raw_comment in raw_comments:
            comment = self._review_comment(raw_comment)
            if comment.comment_id in seen_comment_ids:
                raise GitHubAdapterError("duplicate_review_comment")
            seen_comment_ids.add(comment.comment_id)
            if not resolved and comment.commit_sha == head_sha and not comment.outdated:
                actionable.append(comment)
            else:
                audit.append(comment)

        segments: list[ReviewThreadFeedback] = []
        for classification, comments in (
            ("actionable", actionable),
            ("audit", audit),
        ):
            if not comments:
                continue
            try:
                segments.append(
                    ReviewThreadFeedback(
                        thread_id=thread_id,
                        path=path,
                        line=line,
                        original_line=original_line,
                        resolved=resolved,
                        classification=classification,
                        comments=tuple(comments),
                    )
                )
            except ValueError:
                raise GitHubAdapterError("invalid_response") from None
        return tuple(segments)

    @staticmethod
    def _review_comment(payload: object) -> ReviewCommentFeedback:
        try:
            if not isinstance(payload, dict):
                raise KeyError
            author = payload.get("author")
            author_login = None if author is None else author["login"]
            commit = payload.get("commit")
            commit_sha = None if commit is None else commit["oid"]
            comment = ReviewCommentFeedback(
                comment_id=payload["id"],
                author_login=author_login,
                body=payload["body"],
                commit_sha=commit_sha,
                outdated=payload["outdated"],
                created_at=payload["createdAt"],
                updated_at=payload["updatedAt"],
            )
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        return comment

    @staticmethod
    def _connection_page(
        connection: object,
    ) -> tuple[list[object], bool, object]:
        try:
            if not isinstance(connection, dict):
                raise KeyError
            nodes = connection["nodes"]
            page_info = connection["pageInfo"]
            has_next = page_info["hasNextPage"]
            cursor = page_info["endCursor"]
            if not isinstance(nodes, list) or not isinstance(has_next, bool):
                raise KeyError
        except (KeyError, TypeError):
            raise GitHubAdapterError("invalid_response") from None
        if has_next and (not isinstance(cursor, str) or not cursor):
            raise GitHubAdapterError("invalid_page_info")
        if not has_next and cursor is not None and not isinstance(cursor, str):
            raise GitHubAdapterError("invalid_page_info")
        return nodes, has_next, cursor

    @staticmethod
    def _next_cursor(cursor: object, seen: set[str]) -> str:
        if not isinstance(cursor, str) or not cursor or cursor in seen:
            raise GitHubAdapterError("invalid_page_info")
        seen.add(cursor)
        return cursor

    @staticmethod
    def _required_node_id(payload: Mapping[str, object], field_name: str) -> str:
        value = payload.get("id")
        if not isinstance(value, str) or not value:
            raise ValueError(f"{field_name} must not be blank")
        return value

    def _check_run_fact(self, payload: object) -> _CheckFact:
        try:
            if not isinstance(payload, dict):
                raise KeyError
            run_id = self._positive_number(payload["id"], "check run id")
            name = self._nonblank(payload["name"], "check name")
            status = self._nonblank(payload["status"], "check status").lower()
            conclusion = payload.get("conclusion")
            app_id = self._positive_number(payload["app"]["id"], "check app id")
            check_suite_id = self._positive_number(
                payload["check_suite"]["id"], "check suite id"
            )
            started_at = self._timestamp(payload["started_at"])
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None

        if status in {
            "queued",
            "in_progress",
            "waiting",
            "requested",
            "pending",
        }:
            if conclusion is not None:
                raise GitHubAdapterError("invalid_check_state")
            state = "PENDING"
        elif status == "completed":
            if not isinstance(conclusion, str):
                raise GitHubAdapterError("invalid_check_state")
            normalized_conclusion = conclusion.lower()
            if normalized_conclusion in {"success", "neutral", "skipped"}:
                state = normalized_conclusion.upper()
            elif normalized_conclusion in {
                "failure",
                "cancelled",
                "timed_out",
                "action_required",
                "stale",
                "startup_failure",
            }:
                state = "FAILURE"
            else:
                raise GitHubAdapterError("invalid_check_state")
        else:
            raise GitHubAdapterError("invalid_check_state")
        return _CheckFact(
            name=name,
            producer=("check-run", app_id, check_suite_id),
            order=(started_at, run_id),
            state=state,
        )

    def _status_context_fact(self, payload: object) -> _CheckFact:
        try:
            if not isinstance(payload, dict):
                raise KeyError
            status_id = self._positive_number(payload["id"], "status id")
            name = self._nonblank(payload["context"], "status context")
            state_value = self._nonblank(payload["state"], "status state").lower()
            creator_id = self._positive_number(
                payload["creator"]["id"], "status creator id"
            )
            updated_at = self._timestamp(payload["updated_at"])
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        if state_value == "success":
            state = "SUCCESS"
        elif state_value == "pending":
            state = "PENDING"
        elif state_value in {"failure", "error"}:
            state = "FAILURE"
        else:
            raise GitHubAdapterError("invalid_check_state")
        return _CheckFact(
            name=name,
            producer=("status-context", creator_id),
            order=(updated_at, status_id),
            state=state,
        )

    @staticmethod
    def _timestamp(value: object) -> float:
        if not isinstance(value, str) or not value:
            raise ValueError("timestamp must not be blank")
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            raise ValueError("timestamp is invalid") from None
        if parsed.tzinfo is None:
            raise ValueError("timestamp must include an offset")
        return parsed.timestamp()

    def _workpad_identity(
        self, payload: object, *, expected_body: str | None = None
    ) -> tuple[int, str]:
        try:
            if not isinstance(payload, dict):
                raise KeyError
            comment_id = self._positive_number(payload["id"], "comment id")
            body = payload["body"]
            user = payload["user"]
            login = user["login"]
            user_type = user["type"]
            updated_at = self._nonblank(payload["updated_at"], "updated at")
            if not isinstance(body, str) or not isinstance(login, str):
                raise KeyError
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        if login != self._workpad_author_login or user_type != "Bot":
            raise GitHubAdapterError("workpad_owner_conflict")
        if body.count(WORKPAD_MARKER) != 1:
            raise GitHubAdapterError("workpad_marker_conflict")
        if expected_body is not None and body != expected_body:
            raise GitHubAdapterError("workpad_response_conflict")
        return comment_id, updated_at

    @staticmethod
    def _workpad_receipt(
        repository: str,
        issue_number: int,
        comment_id: int,
        updated_at: str,
        *,
        source: str,
    ) -> GitHubEventReceipt:
        return GitHubEventReceipt(
            operation="workpad",
            repository=repository,
            resource_kind="issue_comment",
            resource_id=str(comment_id),
            state="current",
            source=source,
            issue_number=issue_number,
            detail=updated_at,
        )

    @staticmethod
    def _label_receipt(
        repository: str,
        issue_number: int,
        label: str,
        *,
        source: str,
    ) -> GitHubEventReceipt:
        return GitHubEventReceipt(
            operation="label",
            repository=repository,
            resource_kind="issue",
            resource_id=str(issue_number),
            state="applied",
            source=source,
            issue_number=issue_number,
            detail=label,
        )

    @staticmethod
    def _review_request_receipt(
        repository: str,
        pull_request_number: int,
        head_sha: str,
        reviewer: str,
        *,
        source: str,
    ) -> GitHubEventReceipt:
        return GitHubEventReceipt(
            operation="review-request",
            repository=repository,
            resource_kind="pull_request",
            resource_id=str(pull_request_number),
            state="requested",
            source=source,
            pull_request_number=pull_request_number,
            head_sha=head_sha,
            detail=reviewer,
        )

    @staticmethod
    def _label_names(payload: object) -> set[str]:
        if not isinstance(payload, list):
            raise GitHubAdapterError("invalid_response")
        labels: list[str] = []
        try:
            for item in payload:
                if not isinstance(item, dict):
                    raise KeyError
                name = item["name"]
                if not isinstance(name, str) or not name:
                    raise KeyError
                labels.append(name)
        except (KeyError, TypeError):
            raise GitHubAdapterError("invalid_response") from None
        if len(labels) != len(set(labels)):
            raise GitHubAdapterError("invalid_response")
        return set(labels)

    @staticmethod
    def _etag(headers: Mapping[str, str]) -> str:
        for name, value in headers.items():
            if name.lower() == "etag" and isinstance(value, str) and value.strip():
                return value.strip()
        raise GitHubAdapterError("missing_etag")

    def _token(self) -> str:
        try:
            token = self._token_provider.get_token()
        except Exception:
            raise GitHubAdapterError("token_unavailable") from None
        if token is None:
            raise GitHubAdapterError("token_unavailable")
        return token.usable_value(
            now=time.time(),
            minimum_lifetime_seconds=(
                HTTP_TIMEOUT_SECONDS + TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS
            ),
        )

    @staticmethod
    def _nonblank(value: object, field_name: str) -> str:
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{field_name} must not be blank")
        return value.strip()

    @staticmethod
    def _positive_number(value: int, field_name: str) -> int:
        if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
            raise ValueError(f"{field_name} must be positive")
        return value

    @staticmethod
    def _full_sha(value: str) -> str:
        if not isinstance(value, str) or len(value) != 40 or any(
            character not in "0123456789abcdef" for character in value
        ):
            raise ValueError("head SHA must be a full lowercase commit SHA")
        return value

    @staticmethod
    def _require_expected_pull_request(
        pull_request: PullRequestState,
        *,
        expected_head_sha: str,
        expected_body: str | None = None,
        expected_draft: bool | None = None,
    ) -> None:
        if pull_request.base_branch != "develop":
            raise GitHubAdapterError("pull_request_base_conflict")
        if pull_request.head_sha != expected_head_sha:
            raise GitHubAdapterError("pull_request_head_conflict")
        if expected_body is not None and pull_request.body != expected_body:
            raise GitHubAdapterError("pull_request_body_conflict")
        if expected_draft is not None and pull_request.draft is not expected_draft:
            raise GitHubAdapterError("pull_request_draft_conflict")

    @classmethod
    def _requested_reviewer_logins(cls, payload: object) -> set[str]:
        try:
            if not isinstance(payload, dict):
                raise KeyError
            users = payload["users"]
            teams = payload["teams"]
            if not isinstance(users, list) or not isinstance(teams, list):
                raise KeyError
            logins = [cls._nonblank(user["login"], "reviewer login") for user in users]
            if any(not isinstance(team, dict) for team in teams):
                raise KeyError
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        if len(logins) != len(set(logins)):
            raise GitHubAdapterError("invalid_response")
        return set(logins)

    @classmethod
    def _pull_request(cls, payload: object) -> PullRequestState:
        try:
            if not isinstance(payload, dict):
                raise KeyError
            number = cls._positive_number(payload["number"], "pull request number")
            base_branch = cls._nonblank(payload["base"]["ref"], "base branch")
            head_branch = cls._nonblank(payload["head"]["ref"], "head branch")
            head_sha = cls._nonblank(payload["head"]["sha"], "head SHA")
            draft = payload["draft"]
            body = payload.get("body") or ""
            if not isinstance(draft, bool) or not isinstance(body, str):
                raise KeyError
        except (KeyError, TypeError, ValueError):
            raise GitHubAdapterError("invalid_response") from None
        return PullRequestState(
            number=number,
            base_branch=base_branch,
            head_branch=head_branch,
            head_sha=head_sha,
            draft=draft,
            body=body,
            checks={},
            unresolved_review_threads=0,
        )


GitRunner = Callable[..., subprocess.CompletedProcess[str]]


class HostGitPusher:
    """Pushes one trusted candidate SHA to its task branch without force."""

    def __init__(
        self,
        *,
        token_provider: InstallationTokenProvider,
        capabilities: Mapping[str, bool],
        runner: GitRunner = subprocess.run,
    ):
        self._token_provider = token_provider
        self._capabilities = dict(capabilities)
        self._runner = runner

    def push(self, snapshot: TaskSnapshot, *, repository: str) -> str:
        for capability in ("create_remote_branch", "push_commit"):
            if self._capabilities.get(capability) is not True:
                raise RuntimeError(f"capability {capability} is disabled")
        if not snapshot.workspace or not snapshot.branch or not snapshot.head_sha:
            raise RuntimeError("task has no trusted workspace, branch, and head SHA")
        workspace = Path(snapshot.workspace).resolve()
        if not workspace.is_dir():
            raise RuntimeError("trusted workspace does not exist")
        owner, repository_name = _parse_repository(repository)
        remote_url = f"https://github.com/{owner}/{repository_name}.git"

        branch = snapshot.branch
        head_sha = snapshot.head_sha
        if len(head_sha) != 40 or any(
            character not in "0123456789abcdef" for character in head_sha
        ):
            raise RuntimeError("trusted head SHA is invalid")
        self._require_git_success(
            workspace,
            ["git", "check-ref-format", f"refs/heads/{branch}"],
            "branch_invalid",
        )
        current_branch = self._require_git_success(
            workspace, ["git", "branch", "--show-current"], "identity_unavailable"
        ).stdout.strip()
        if current_branch != branch:
            raise RuntimeError("current branch differs from trusted task branch")
        current_head = self._require_git_success(
            workspace, ["git", "rev-parse", "HEAD"], "identity_unavailable"
        ).stdout.strip()
        if current_head != head_sha:
            raise RuntimeError("current HEAD differs from trusted task head SHA")

        token = self._token()
        with tempfile.TemporaryDirectory(prefix="jstore-github-askpass-") as directory:
            askpass = Path(directory) / "askpass"
            askpass.write_text(
                "#!/bin/sh\n"
                "case \"$1\" in\n"
                "  *Username*) printf '%s\\n' 'x-access-token' ;;\n"
                "  *Password*) printf '%s\\n' \"$JSTORE_GITHUB_PUSH_TOKEN\" ;;\n"
                "  *) exit 1 ;;\n"
                "esac\n",
                encoding="utf-8",
            )
            askpass.chmod(0o700)
            environment = self._git_environment()
            environment.update(
                {
                    "GIT_ASKPASS": str(askpass),
                    "JSTORE_GITHUB_PUSH_TOKEN": token,
                }
            )
            try:
                result = self._runner(
                    [
                        "git",
                        "push",
                        "--porcelain",
                        "--no-verify",
                        remote_url,
                        f"{head_sha}:refs/heads/{branch}",
                    ],
                    cwd=workspace,
                    env=environment,
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=GIT_TIMEOUT_SECONDS,
                )
            except Exception:
                raise GitHubAdapterError("git_push_failed") from None
        if result.returncode != 0:
            raise GitHubAdapterError("git_push_rejected")
        return f"push:{repository}:{branch}:{head_sha}"

    def _token(self) -> str:
        try:
            token = self._token_provider.get_token()
        except Exception:
            raise GitHubAdapterError("token_unavailable") from None
        if token is None:
            raise GitHubAdapterError("token_unavailable")
        return token.usable_value(
            now=time.time(),
            minimum_lifetime_seconds=(
                GIT_TIMEOUT_SECONDS + TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS
            ),
        )

    def _require_git_success(
        self, workspace: Path, arguments: list[str], category: str
    ) -> subprocess.CompletedProcess[str]:
        try:
            result = self._runner(
                arguments,
                cwd=workspace,
                env=self._git_environment(),
                check=False,
                capture_output=True,
                text=True,
                timeout=GIT_TIMEOUT_SECONDS,
            )
        except Exception:
            raise GitHubAdapterError(category) from None
        if result.returncode != 0:
            raise GitHubAdapterError(category)
        return result

    @staticmethod
    def _git_environment() -> dict[str, str]:
        environment = trusted_process_environment()
        git_config = (
            ("credential.helper", ""),
            ("core.hooksPath", "/dev/null"),
            ("http.followRedirects", "false"),
            ("http.saveCookies", "false"),
            ("protocol.allow", "never"),
            ("protocol.https.allow", "always"),
        )
        environment.update(
            {
                "GIT_CONFIG_GLOBAL": os.devnull,
                "GIT_CONFIG_SYSTEM": os.devnull,
                "GIT_CONFIG_NOSYSTEM": "1",
                "GIT_TERMINAL_PROMPT": "0",
                "GIT_CONFIG_COUNT": str(len(git_config)),
            }
        )
        for index, (key, value) in enumerate(git_config):
            environment[f"GIT_CONFIG_KEY_{index}"] = key
            environment[f"GIT_CONFIG_VALUE_{index}"] = value
        return environment
