from __future__ import annotations

import inspect
import json
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.coordinator import TaskSnapshot  # noqa: E402
from agentic_cicd.github_adapter import (  # noqa: E402
    EnvironmentInstallationTokenProvider,
    GitHubAdapterError,
    GitHubRestGraphqlAdapter,
    HostGitPusher,
    HttpResponse,
    InstallationToken,
)
from agentic_cicd.github_reconciler import WORKPAD_MARKER  # noqa: E402


TOKEN = "ghs_fixture_installation_token_not_a_secret"
HEAD_SHA = "a" * 40


class FakeTokenProvider:
    def __init__(self, token: InstallationToken | None):
        self.token = token
        self.calls = 0

    def get_token(self) -> InstallationToken | None:
        self.calls += 1
        return self.token


class FakeTransport:
    def __init__(self, responses: list[HttpResponse]):
        self.responses = list(responses)
        self.calls: list[dict[str, object]] = []

    def request(self, *, method, url, headers, body):
        self.calls.append(
            {"method": method, "url": url, "headers": dict(headers), "body": body}
        )
        if not self.responses:
            raise AssertionError("unexpected HTTP request")
        return self.responses.pop(0)


class FakeGitRunner:
    def __init__(self, *, fail_push: bool = False, raise_on_push: bool = False):
        self.fail_push = fail_push
        self.raise_on_push = raise_on_push
        self.calls: list[dict[str, object]] = []

    def __call__(self, arguments, *, cwd, env, **kwargs):
        call = {
            "arguments": tuple(arguments),
            "cwd": Path(cwd),
            "env": dict(env),
            **kwargs,
        }
        self.calls.append(call)
        if arguments[-2:] == ["branch", "--show-current"]:
            return subprocess.CompletedProcess(
                arguments, 0, "codex/gh-50-doc-entry\n", ""
            )
        if arguments[-2:] == ["rev-parse", "HEAD"]:
            return subprocess.CompletedProcess(arguments, 0, HEAD_SHA + "\n", "")
        if "check-ref-format" in arguments:
            return subprocess.CompletedProcess(arguments, 0, "", "")
        if "push" in arguments:
            if self.raise_on_push:
                raise RuntimeError(TOKEN)
            if self.fail_push:
                return subprocess.CompletedProcess(arguments, 1, "", TOKEN)
            return subprocess.CompletedProcess(arguments, 0, "ok\n", "")
        raise AssertionError(f"unexpected git invocation: {arguments}")


def valid_token() -> InstallationToken:
    return InstallationToken(TOKEN, expires_at_epoch_seconds=time.time() + 600)


def snapshot(workspace: Path) -> TaskSnapshot:
    return TaskSnapshot(
        issue_identifier="GH-50",
        state="waiting_ci",
        base_sha="b" * 40,
        head_sha=HEAD_SHA,
        branch="codex/gh-50-doc-entry",
        workspace=str(workspace),
    )


def json_response(
    status: int, payload: object, *, headers: dict[str, str] | None = None
) -> HttpResponse:
    return HttpResponse(
        status=status,
        headers=headers or {"content-type": "application/json"},
        body=json.dumps(payload).encode(),
    )


def pull_request_payload(
    *,
    number: int = 51,
    head_sha: str = "a" * 40,
    draft: bool = True,
    body: str = "body",
    node_id: str = "PR_node_51",
):
    return {
        "number": number,
        "node_id": node_id,
        "base": {"ref": "develop"},
        "head": {"ref": "codex/gh-50-doc-entry", "sha": head_sha},
        "draft": draft,
        "body": body,
    }


def workpad_comment(body: str, *, comment_id: int = 100, login: str = "j-store-bot"):
    return {
        "id": comment_id,
        "body": body,
        "user": {"login": login, "type": "Bot"},
        "updated_at": "2026-08-19T00:00:00Z",
    }


def check_run(
    name: str,
    *,
    run_id: int,
    status: str = "completed",
    conclusion: str | None = "success",
    app_id: int = 1,
    check_suite_id: int = 100,
    started_at: str = "2026-08-19T00:00:00Z",
):
    return {
        "id": run_id,
        "name": name,
        "status": status,
        "conclusion": conclusion,
        "started_at": started_at,
        "app": {"id": app_id},
        "check_suite": {"id": check_suite_id},
    }


def status_context(
    context: str,
    *,
    status_id: int,
    state: str,
    creator_id: int = 2,
    updated_at: str = "2026-08-19T00:00:00Z",
):
    return {
        "id": status_id,
        "context": context,
        "state": state,
        "updated_at": updated_at,
        "creator": {"id": creator_id},
    }


def review_comment_node(
    comment_id: str,
    *,
    body: str,
    commit_sha: str | None,
    outdated: bool,
    author: str | None = "reviewer",
):
    return {
        "id": comment_id,
        "body": body,
        "author": {"login": author} if author is not None else None,
        "commit": {"oid": commit_sha} if commit_sha is not None else None,
        "outdated": outdated,
        "createdAt": "2026-08-19T00:00:00Z",
        "updatedAt": "2026-08-19T00:00:00Z",
    }


def review_thread_node(
    thread_id: str,
    comments: list[dict[str, object]],
    *,
    resolved: bool = False,
    comments_has_next: bool = False,
    comments_cursor: str | None = None,
):
    return {
        "id": thread_id,
        "isResolved": resolved,
        "path": "src/example.py",
        "line": 10,
        "originalLine": 8,
        "comments": {
            "nodes": comments,
            "pageInfo": {
                "hasNextPage": comments_has_next,
                "endCursor": comments_cursor,
            },
        },
    }


def review_threads_response(
    nodes: list[dict[str, object]],
    *,
    head_sha: str = HEAD_SHA,
    has_next: bool = False,
    cursor: str | None = None,
):
    return {
        "data": {
            "repository": {
                "pullRequest": {
                    "number": 51,
                    "headRefOid": head_sha,
                    "reviewThreads": {
                        "nodes": nodes,
                        "pageInfo": {
                            "hasNextPage": has_next,
                            "endCursor": cursor,
                        },
                    },
                }
            }
        }
    }


def review_comments_response(
    thread_id: str,
    nodes: list[dict[str, object]],
    *,
    has_next: bool = False,
    cursor: str | None = None,
    head_sha: str = HEAD_SHA,
):
    return {
        "data": {
            "node": {
                "id": thread_id,
                "pullRequest": {"number": 51, "headRefOid": head_sha},
                "comments": {
                    "nodes": nodes,
                    "pageInfo": {
                        "hasNextPage": has_next,
                        "endCursor": cursor,
                    },
                },
            }
        }
    }


class InstallationTokenTest(unittest.TestCase):
    def test_environment_provider_requires_token_and_numeric_expiration(self) -> None:
        expires = str(time.time() + 600)
        for environment in (
            {},
            {"JSTORE_SYMPHONY_GITHUB_TOKEN": TOKEN},
            {"JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS": expires},
            {
                "JSTORE_SYMPHONY_GITHUB_TOKEN": TOKEN,
                "JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS": "invalid",
            },
        ):
            with self.subTest(environment=environment):
                self.assertIsNone(
                    EnvironmentInstallationTokenProvider(environment).get_token()
                )

        lease = EnvironmentInstallationTokenProvider(
            {
                "JSTORE_SYMPHONY_GITHUB_TOKEN": TOKEN,
                "JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS": expires,
            }
        ).get_token()
        self.assertIsNotNone(lease)
        self.assertEqual(
            TOKEN,
            lease.usable_value(now=time.time(), minimum_lifetime_seconds=65),
        )

    def test_token_string_representations_are_redacted(self) -> None:
        token = valid_token()

        self.assertNotIn(TOKEN, repr(token))
        self.assertNotIn(TOKEN, str(token))

    def test_absent_and_expired_tokens_fail_before_transport(self) -> None:
        for lease in (
            None,
            InstallationToken("", expires_at_epoch_seconds=time.time() + 600),
            InstallationToken(TOKEN, expires_at_epoch_seconds=time.time() - 1),
            InstallationToken(TOKEN, expires_at_epoch_seconds=time.time() + 10),
            InstallationToken(TOKEN, expires_at_epoch_seconds=float("nan")),
            InstallationToken(TOKEN, expires_at_epoch_seconds=float("inf")),
        ):
            with self.subTest(lease=lease):
                transport = FakeTransport([])
                adapter = GitHubRestGraphqlAdapter(
                    token_provider=FakeTokenProvider(lease), transport=transport
                )

                with self.assertRaisesRegex(GitHubAdapterError, "token_unavailable"):
                    adapter.list_open_pull_requests(
                        "ddd-mall/j-store", "codex/gh-50-doc-entry"
                    )

                self.assertEqual([], transport.calls)


class GitHubRestGraphqlAdapterTest(unittest.TestCase):
    def adapter(self, transport: FakeTransport) -> GitHubRestGraphqlAdapter:
        return GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()),
            transport=transport,
            workpad_author_login="j-store-bot",
        )

    def test_lists_open_pull_requests_using_fixed_rest_endpoint(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    [
                        {
                            "number": 51,
                            "base": {"ref": "develop"},
                            "head": {
                                "ref": "codex/gh-50-doc-entry",
                                "sha": HEAD_SHA,
                            },
                            "draft": True,
                            "body": "draft body",
                        }
                    ],
                )
            ]
        )
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=transport
        )

        pull_requests = adapter.list_open_pull_requests(
            "ddd-mall/j-store", "codex/gh-50-doc-entry"
        )

        self.assertEqual(51, pull_requests[0].number)
        call = transport.calls[0]
        self.assertEqual("GET", call["method"])
        self.assertEqual(
            "https://api.github.com/repos/ddd-mall/j-store/pulls"
            "?state=open&head=ddd-mall%3Acodex%2Fgh-50-doc-entry&per_page=100",
            call["url"],
        )
        self.assertEqual(f"Bearer {TOKEN}", call["headers"]["Authorization"])
        self.assertIsNone(call["body"])

    def test_creates_only_a_draft_pull_request(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    201,
                    {
                        "number": 52,
                        "base": {"ref": "develop"},
                        "head": {
                            "ref": "codex/gh-50-doc-entry",
                            "sha": HEAD_SHA,
                        },
                        "draft": True,
                        "body": "body",
                    },
                )
            ]
        )
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=transport
        )

        adapter.create_draft_pull_request(
            "ddd-mall/j-store",
            "develop",
            "codex/gh-50-doc-entry",
            "title",
            "body",
        )

        call = transport.calls[0]
        self.assertEqual("POST", call["method"])
        self.assertEqual(
            {
                "base": "develop",
                "head": "codex/gh-50-doc-entry",
                "title": "title",
                "body": "body",
                "draft": True,
            },
            json.loads(call["body"]),
        )

    def test_converts_ready_pull_request_to_draft_and_reobserves_head(self) -> None:
        transport = FakeTransport(
            [
                json_response(200, pull_request_payload(draft=False)),
                json_response(
                    200,
                    {
                        "data": {
                            "convertPullRequestToDraft": {
                                "pullRequest": {"id": "PR_node_51", "isDraft": True}
                            }
                        }
                    },
                ),
                json_response(200, pull_request_payload(draft=True)),
            ]
        )

        observed = self.adapter(transport).convert_pull_request_to_draft(
            "ddd-mall/j-store", 51, HEAD_SHA
        )

        self.assertTrue(observed.draft)
        mutation = json.loads(transport.calls[1]["body"])
        self.assertIn("convertPullRequestToDraft", mutation["query"])
        self.assertNotIn("mergePullRequest", mutation["query"])
        self.assertEqual({"pullRequestId": "PR_node_51"}, mutation["variables"])

    def test_pull_request_body_update_retries_etag_conflict(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    pull_request_payload(body="old"),
                    headers={"ETag": '"pr-v1"'},
                ),
                json_response(412, {"message": TOKEN}),
                json_response(
                    200,
                    pull_request_payload(body="other writer"),
                    headers={"ETag": '"pr-v2"'},
                ),
                json_response(200, pull_request_payload(body="canonical")),
            ]
        )

        observed = self.adapter(transport).reconcile_pull_request_body(
            "ddd-mall/j-store", 51, HEAD_SHA, "canonical"
        )

        self.assertEqual("canonical", observed.body)
        updates = [call for call in transport.calls if call["method"] == "PATCH"]
        self.assertEqual(
            ['"pr-v1"', '"pr-v2"'],
            [call["headers"]["If-Match"] for call in updates],
        )

    def test_ready_uses_one_fixed_graphql_mutation(self) -> None:
        transport = FakeTransport(
            [
                json_response(200, pull_request_payload()),
                json_response(
                    200,
                    {
                        "data": {
                            "markPullRequestReadyForReview": {
                                "pullRequest": {"id": "PR_node_51", "isDraft": False}
                            }
                        }
                    },
                ),
                json_response(200, pull_request_payload(draft=False)),
            ]
        )
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=transport
        )

        event = adapter.mark_pull_request_ready(
            "ddd-mall/j-store", 51, "a" * 40, "body"
        )

        self.assertEqual("ready", event.operation)
        self.assertEqual("PR_node_51", event.resource_id)
        self.assertEqual("a" * 40, event.head_sha)
        self.assertEqual("mutation", event.source)
        graphql = transport.calls[1]
        payload = json.loads(graphql["body"])
        self.assertEqual("POST", graphql["method"])
        self.assertEqual("https://api.github.com/graphql", graphql["url"])
        self.assertEqual({"pullRequestId": "PR_node_51"}, payload["variables"])
        self.assertIn("markPullRequestReadyForReview", payload["query"])
        self.assertNotIn("mergePullRequest", payload["query"])

    def test_ready_rejects_changed_head_before_graphql_mutation(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200, pull_request_payload(head_sha="9" * 40)
                )
            ]
        )

        with self.assertRaisesRegex(
            GitHubAdapterError, "pull_request_ready_precondition_conflict"
        ):
            self.adapter(transport).mark_pull_request_ready(
                "ddd-mall/j-store", 51, "a" * 40, "body"
            )

        self.assertEqual(["GET"], [call["method"] for call in transport.calls])

    def test_ready_rejects_head_or_body_drift_after_graphql_mutation(self) -> None:
        for changed in (
            pull_request_payload(draft=False, head_sha="9" * 40),
            pull_request_payload(draft=False, body="changed"),
        ):
            with self.subTest(changed=changed):
                transport = FakeTransport(
                    [
                        json_response(200, pull_request_payload()),
                        json_response(
                            200,
                            {
                                "data": {
                                    "markPullRequestReadyForReview": {
                                        "pullRequest": {
                                            "id": "PR_node_51",
                                            "isDraft": False,
                                        }
                                    }
                                }
                            },
                        ),
                        json_response(200, changed),
                    ]
                )

                with self.assertRaises(GitHubAdapterError):
                    self.adapter(transport).mark_pull_request_ready(
                        "ddd-mall/j-store", 51, HEAD_SHA, "body"
                    )

                self.assertEqual(["GET", "POST", "GET"], [
                    call["method"] for call in transport.calls
                ])

    def test_review_request_uses_fixed_repository_endpoint(self) -> None:
        transport = FakeTransport(
            [
                json_response(200, pull_request_payload(draft=False)),
                json_response(200, {"users": [], "teams": []}),
                json_response(201, {"id": 1}),
                json_response(
                    200,
                    {"users": [{"login": "maintainer"}], "teams": []},
                ),
                json_response(200, pull_request_payload(draft=False)),
            ]
        )
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=transport
        )

        event = adapter.request_pull_request_review(
            "ddd-mall/j-store", 51, "a" * 40, "maintainer"
        )

        self.assertEqual("review-request", event.operation)
        self.assertEqual(51, event.pull_request_number)
        self.assertEqual("a" * 40, event.head_sha)
        self.assertEqual("maintainer", event.detail)
        self.assertEqual("mutation", event.source)
        call = transport.calls[2]
        self.assertEqual(
            "https://api.github.com/repos/ddd-mall/j-store/pulls/51/requested_reviewers",
            call["url"],
        )
        self.assertEqual({"reviewers": ["maintainer"]}, json.loads(call["body"]))

    def test_existing_review_request_is_observed_without_duplicate_post(self) -> None:
        transport = FakeTransport(
            [
                json_response(200, pull_request_payload(draft=False)),
                json_response(
                    200,
                    {"users": [{"login": "maintainer"}], "teams": []},
                ),
                json_response(200, pull_request_payload(draft=False)),
            ]
        )
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=transport
        )

        event = adapter.request_pull_request_review(
            "ddd-mall/j-store", 51, "a" * 40, "maintainer"
        )

        self.assertEqual("review-request", event.operation)
        self.assertEqual("observation", event.source)
        self.assertEqual("a" * 40, event.head_sha)
        self.assertEqual(["GET", "GET", "GET"], [call["method"] for call in transport.calls])

    def test_review_request_rejects_head_drift_before_or_after_write(self) -> None:
        cases = (
            [json_response(200, pull_request_payload(head_sha="9" * 40))],
            [
                json_response(200, pull_request_payload(draft=False)),
                json_response(200, {"users": [], "teams": []}),
                json_response(201, {"id": 1}),
                json_response(
                    200,
                    {"users": [{"login": "maintainer"}], "teams": []},
                ),
                json_response(
                    200, pull_request_payload(draft=False, head_sha="9" * 40)
                ),
            ],
        )
        for responses in cases:
            with self.subTest(response_count=len(responses)):
                transport = FakeTransport(responses)
                with self.assertRaisesRegex(GitHubAdapterError, "pull_request_head_conflict"):
                    self.adapter(transport).request_pull_request_review(
                        "ddd-mall/j-store", 51, HEAD_SHA, "maintainer"
                    )

    def test_forbidden_operations_are_structurally_unavailable(self) -> None:
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=FakeTransport([])
        )

        for operation in (
            "approve_pull_request",
            "merge_pull_request",
            "create_release",
            "create_deployment",
            "dispatch_workflow",
            "request",
            "graphql",
        ):
            self.assertFalse(hasattr(adapter, operation), operation)

    def test_remote_error_body_and_token_are_not_exposed(self) -> None:
        transport = FakeTransport(
            [HttpResponse(status=403, headers={}, body=(TOKEN + " denied").encode())]
        )
        adapter = GitHubRestGraphqlAdapter(
            token_provider=FakeTokenProvider(valid_token()), transport=transport
        )

        with self.assertRaises(GitHubAdapterError) as raised:
            adapter.list_open_pull_requests(
                "ddd-mall/j-store", "codex/gh-50-doc-entry"
            )

        self.assertEqual("http_403", raised.exception.category)
        self.assertNotIn(TOKEN, str(raised.exception))
        self.assertNotIn("denied", str(raised.exception))

    def test_creates_and_verifies_the_only_workpad_comment(self) -> None:
        desired = f"{WORKPAD_MARKER}\n\nReady for human review."
        transport = FakeTransport(
            [
                json_response(200, []),
                json_response(201, workpad_comment(desired)),
                json_response(200, [workpad_comment(desired)]),
            ]
        )

        event = self.adapter(transport).upsert_workpad(
            "ddd-mall/j-store",
            50,
            WORKPAD_MARKER,
            "Ready for human review.",
        )

        self.assertEqual("100", event.resource_id)
        self.assertEqual("2026-08-19T00:00:00Z", event.detail)
        self.assertEqual("mutation", event.source)
        create = transport.calls[1]
        self.assertEqual("POST", create["method"])
        self.assertEqual(
            "https://api.github.com/repos/ddd-mall/j-store/issues/50/comments",
            create["url"],
        )
        self.assertEqual({"body": desired}, json.loads(create["body"]))

    def test_existing_identical_workpad_is_observed_without_update(self) -> None:
        desired = f"{WORKPAD_MARKER}\n\nReady for human review."
        transport = FakeTransport(
            [
                json_response(200, [workpad_comment(desired)]),
                json_response(
                    200,
                    workpad_comment(desired),
                    headers={"ETag": '"comment-v1"'},
                ),
            ]
        )

        event = self.adapter(transport).upsert_workpad(
            "ddd-mall/j-store",
            50,
            WORKPAD_MARKER,
            "Ready for human review.",
        )

        self.assertEqual("100", event.resource_id)
        self.assertEqual("observation", event.source)
        self.assertEqual(["GET", "GET"], [call["method"] for call in transport.calls])

    def test_stale_workpad_uses_etag_and_retries_one_conflict(self) -> None:
        stale = f"{WORKPAD_MARKER}\n\nWaiting."
        desired = f"{WORKPAD_MARKER}\n\nReady for human review."
        transport = FakeTransport(
            [
                json_response(200, [workpad_comment(stale)]),
                json_response(
                    200,
                    workpad_comment(stale),
                    headers={"etag": '"comment-v1"'},
                ),
                json_response(412, {"message": TOKEN}),
                json_response(200, [workpad_comment(stale)]),
                json_response(
                    200,
                    workpad_comment(stale),
                    headers={"ETag": '"comment-v2"'},
                ),
                json_response(200, workpad_comment(desired)),
            ]
        )

        event = self.adapter(transport).upsert_workpad(
            "ddd-mall/j-store",
            50,
            WORKPAD_MARKER,
            "Ready for human review.",
        )

        self.assertEqual("100", event.resource_id)
        self.assertEqual("mutation", event.source)
        updates = [call for call in transport.calls if call["method"] == "PATCH"]
        self.assertEqual(['"comment-v1"', '"comment-v2"'], [
            call["headers"]["If-Match"] for call in updates
        ])
        self.assertNotIn(TOKEN, str(event))

    def test_workpad_create_conflict_is_reread_without_second_create(self) -> None:
        desired = f"{WORKPAD_MARKER}\n\nReady for human review."
        transport = FakeTransport(
            [
                json_response(200, []),
                json_response(409, {"message": TOKEN}),
                json_response(200, [workpad_comment(desired)]),
                json_response(
                    200,
                    workpad_comment(desired),
                    headers={"ETag": '"comment-v1"'},
                ),
            ]
        )

        event = self.adapter(transport).upsert_workpad(
            "ddd-mall/j-store",
            50,
            WORKPAD_MARKER,
            "Ready for human review.",
        )

        self.assertEqual("100", event.resource_id)
        self.assertEqual("observation", event.source)
        self.assertEqual(
            1, len([call for call in transport.calls if call["method"] == "POST"])
        )

    def test_workpad_conflict_retry_is_bounded(self) -> None:
        stale = f"{WORKPAD_MARKER}\n\nWaiting."
        responses: list[HttpResponse] = []
        for attempt in range(3):
            responses.extend(
                [
                    json_response(200, [workpad_comment(stale)]),
                    json_response(
                        200,
                        workpad_comment(stale),
                        headers={"ETag": f'"comment-v{attempt}"'},
                    ),
                    json_response(412, {"message": TOKEN}),
                ]
            )
        transport = FakeTransport(responses)

        with self.assertRaisesRegex(
            GitHubAdapterError, "workpad_conflict"
        ) as raised:
            self.adapter(transport).upsert_workpad(
                "ddd-mall/j-store", 50, WORKPAD_MARKER, "Desired."
            )

        self.assertEqual(
            3,
            len([call for call in transport.calls if call["method"] == "PATCH"]),
        )
        self.assertNotIn(TOKEN, str(raised.exception))

    def test_duplicate_or_foreign_workpad_marker_fails_closed(self) -> None:
        body = f"{WORKPAD_MARKER}\n\nExisting."
        for comments, category in (
            (
                [workpad_comment(body), workpad_comment(body, comment_id=101)],
                "workpad_not_unique",
            ),
            ([workpad_comment(body, login="some-user")], "workpad_owner_conflict"),
        ):
            with self.subTest(category=category):
                transport = FakeTransport([json_response(200, comments)])

                with self.assertRaisesRegex(GitHubAdapterError, category):
                    self.adapter(transport).upsert_workpad(
                        "ddd-mall/j-store", 50, WORKPAD_MARKER, "Desired."
                    )

                self.assertEqual(["GET"], [call["method"] for call in transport.calls])

    def test_workpad_rejects_caller_supplied_marker(self) -> None:
        transport = FakeTransport([])

        with self.assertRaisesRegex(ValueError, "must not contain"):
            self.adapter(transport).upsert_workpad(
                "ddd-mall/j-store",
                50,
                WORKPAD_MARKER,
                f"duplicated {WORKPAD_MARKER}",
            )

        self.assertEqual([], transport.calls)

    def test_replaces_only_mutually_exclusive_agent_state_labels(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "labels": [
                            {"name": "agent:waiting-ci"},
                            {"name": "risk:human-approval"},
                            {"name": "area:tooling"},
                        ]
                    },
                    headers={"ETag": '"issue-v1"'},
                ),
                json_response(
                    200,
                    [
                        {"name": "agent:human-review"},
                        {"name": "area:tooling"},
                        {"name": "risk:human-approval"},
                    ],
                ),
            ]
        )

        event = self.adapter(transport).replace_issue_state_label(
            "ddd-mall/j-store", 50, "agent:human-review"
        )

        self.assertEqual("50", event.resource_id)
        self.assertEqual("agent:human-review", event.detail)
        self.assertEqual("mutation", event.source)
        update = transport.calls[1]
        self.assertEqual("PUT", update["method"])
        self.assertEqual('"issue-v1"', update["headers"]["If-Match"])
        self.assertEqual(
            {
                "labels": [
                    "agent:human-review",
                    "area:tooling",
                    "risk:human-approval",
                ]
            },
            json.loads(update["body"]),
        )

    def test_matching_state_label_is_observed_without_write(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "labels": [
                            {"name": "agent:human-review"},
                            {"name": "risk:human-approval"},
                        ]
                    },
                    headers={"ETag": '"issue-v1"'},
                )
            ]
        )

        event = self.adapter(transport).replace_issue_state_label(
            "ddd-mall/j-store", 50, "agent:human-review"
        )

        self.assertEqual("50", event.resource_id)
        self.assertEqual("observation", event.source)
        self.assertEqual(["GET"], [call["method"] for call in transport.calls])

    def test_label_conflict_is_reread_before_retry(self) -> None:
        waiting = {"labels": [{"name": "agent:waiting-ci"}]}
        desired = [{"name": "agent:human-review"}]
        transport = FakeTransport(
            [
                json_response(200, waiting, headers={"ETag": '"issue-v1"'}),
                json_response(409, {"message": TOKEN}),
                json_response(200, waiting, headers={"ETag": '"issue-v2"'}),
                json_response(200, desired),
            ]
        )

        event = self.adapter(transport).replace_issue_state_label(
            "ddd-mall/j-store", 50, "agent:human-review"
        )

        self.assertEqual("50", event.resource_id)
        self.assertEqual("mutation", event.source)
        updates = [call for call in transport.calls if call["method"] == "PUT"]
        self.assertEqual(
            ['"issue-v1"', '"issue-v2"'],
            [call["headers"]["If-Match"] for call in updates],
        )

    def test_unknown_state_label_and_missing_etag_fail_before_write(self) -> None:
        adapter = self.adapter(FakeTransport([]))
        with self.assertRaisesRegex(ValueError, "unsupported agent state"):
            adapter.replace_issue_state_label(
                "ddd-mall/j-store", 50, "agent:invented"
            )

        transport = FakeTransport(
            [json_response(200, {"labels": [{"name": "agent:queued"}]})]
        )
        with self.assertRaisesRegex(GitHubAdapterError, "missing_etag"):
            self.adapter(transport).replace_issue_state_label(
                "ddd-mall/j-store", 50, "agent:human-review"
            )
        self.assertEqual(["GET"], [call["method"] for call in transport.calls])

    def test_collects_current_head_check_runs_and_status_contexts(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "total_count": 4,
                        "check_runs": [
                            check_run("quality", run_id=10),
                            check_run(
                                "qodana",
                                run_id=11,
                                conclusion="neutral",
                            ),
                            check_run(
                                "integration",
                                run_id=12,
                                status="in_progress",
                                conclusion=None,
                            ),
                            check_run(
                                "deployment-preview",
                                run_id=13,
                                status="waiting",
                                conclusion=None,
                            ),
                        ],
                    },
                ),
                json_response(
                    200,
                    {
                        "total_count": 2,
                        "statuses": [
                            status_context("branch-policy", status_id=20, state="success"),
                            status_context("legacy", status_id=21, state="error"),
                        ],
                    },
                ),
            ]
        )

        checks = self.adapter(transport).collect_commit_checks(
            "ddd-mall/j-store", HEAD_SHA
        )

        self.assertEqual(
            {
                "quality": "SUCCESS",
                "qodana": "NEUTRAL",
                "integration": "PENDING",
                "deployment-preview": "PENDING",
                "branch-policy": "SUCCESS",
                "legacy": "FAILURE",
            },
            checks,
        )
        self.assertEqual(
            "https://api.github.com/repos/ddd-mall/j-store/commits/"
            f"{HEAD_SHA}/check-runs?filter=latest&per_page=100&page=1",
            transport.calls[0]["url"],
        )
        self.assertEqual(
            "https://api.github.com/repos/ddd-mall/j-store/commits/"
            f"{HEAD_SHA}/status?per_page=100&page=1",
            transport.calls[1]["url"],
        )

    def test_new_rerun_state_supersedes_old_result_from_same_producer(self) -> None:
        for latest, expected in (
            (
                check_run(
                    "quality",
                    run_id=31,
                    status="in_progress",
                    conclusion=None,
                    started_at="2026-08-19T00:01:00Z",
                ),
                "PENDING",
            ),
            (
                check_run(
                    "quality",
                    run_id=32,
                    started_at="2026-08-19T00:02:00Z",
                ),
                "SUCCESS",
            ),
        ):
            with self.subTest(expected=expected):
                old = check_run(
                    "quality",
                    run_id=30,
                    conclusion="failure",
                    started_at="2026-08-19T00:00:00Z",
                )
                transport = FakeTransport(
                    [
                        json_response(
                            200,
                            {"total_count": 2, "check_runs": [old, latest]},
                        ),
                        json_response(200, {"total_count": 0, "statuses": []}),
                    ]
                )

                checks = self.adapter(transport).collect_commit_checks(
                    "ddd-mall/j-store", HEAD_SHA
                )

                self.assertEqual(expected, checks["quality"])

    def test_conflicting_latest_producers_fail_closed_by_display_name(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "total_count": 2,
                        "check_runs": [
                            check_run("quality", run_id=40, app_id=1),
                            check_run(
                                "quality",
                                run_id=41,
                                app_id=2,
                                conclusion="failure",
                            ),
                        ],
                    },
                ),
                json_response(
                    200,
                    {
                        "total_count": 1,
                        "statuses": [
                            status_context("quality", status_id=42, state="success")
                        ],
                    },
                ),
            ]
        )

        checks = self.adapter(transport).collect_commit_checks(
            "ddd-mall/j-store", HEAD_SHA
        )

        self.assertEqual("CONFLICT", checks["quality"])

    def test_duplicate_name_in_different_check_suites_is_not_a_rerun(self) -> None:
        transport = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "total_count": 2,
                        "check_runs": [
                            check_run(
                                "quality",
                                run_id=50,
                                app_id=1,
                                check_suite_id=100,
                            ),
                            check_run(
                                "quality",
                                run_id=51,
                                app_id=1,
                                check_suite_id=101,
                                conclusion="failure",
                            ),
                        ],
                    },
                ),
                json_response(200, {"total_count": 0, "statuses": []}),
            ]
        )

        checks = self.adapter(transport).collect_commit_checks(
            "ddd-mall/j-store", HEAD_SHA
        )

        self.assertEqual("CONFLICT", checks["quality"])

    def test_check_pagination_and_declared_total_must_be_complete(self) -> None:
        first_page = [
            check_run(f"check-{index}", run_id=1000 + index)
            for index in range(100)
        ]
        last = check_run("check-100", run_id=1100)
        transport = FakeTransport(
            [
                json_response(
                    200, {"total_count": 101, "check_runs": first_page}
                ),
                json_response(200, {"total_count": 101, "check_runs": [last]}),
                json_response(200, {"total_count": 0, "statuses": []}),
            ]
        )

        checks = self.adapter(transport).collect_commit_checks(
            "ddd-mall/j-store", HEAD_SHA
        )

        self.assertEqual(101, len(checks))
        self.assertIn("page=2", transport.calls[1]["url"])

        incomplete = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "total_count": 2,
                        "check_runs": [check_run("quality", run_id=1)],
                    },
                )
            ]
        )
        with self.assertRaisesRegex(GitHubAdapterError, "incomplete_check_runs"):
            self.adapter(incomplete).collect_commit_checks(
                "ddd-mall/j-store", HEAD_SHA
            )

    def test_invalid_head_or_check_state_returns_no_partial_facts(self) -> None:
        transport = FakeTransport([])
        with self.assertRaisesRegex(ValueError, "head SHA"):
            self.adapter(transport).collect_commit_checks(
                "ddd-mall/j-store", "main"
            )
        self.assertEqual([], transport.calls)

        malformed = FakeTransport(
            [
                json_response(
                    200,
                    {
                        "total_count": 1,
                        "check_runs": [
                            check_run(
                                "quality",
                                run_id=1,
                                status="mystery",
                                conclusion=None,
                            )
                        ],
                    },
                ),
                json_response(200, {"total_count": 0, "statuses": []}),
            ]
        )
        with self.assertRaisesRegex(GitHubAdapterError, "invalid_check_state"):
            self.adapter(malformed).collect_commit_checks(
                "ddd-mall/j-store", HEAD_SHA
            )

    def test_review_packet_separates_current_actionable_and_audit_comments(self) -> None:
        old = review_comment_node(
            "PRRC_old", body="Old finding", commit_sha="b" * 40, outdated=True
        )
        current = review_comment_node(
            "PRRC_current",
            body="Current finding",
            commit_sha=HEAD_SHA,
            outdated=False,
        )
        resolved = review_comment_node(
            "PRRC_resolved",
            body="Already fixed",
            commit_sha=HEAD_SHA,
            outdated=False,
        )
        transport = FakeTransport(
            [
                json_response(
                    200,
                    review_threads_response(
                        [
                            review_thread_node("PRRT_mixed", [old, current]),
                            review_thread_node(
                                "PRRT_resolved", [resolved], resolved=True
                            ),
                        ]
                    ),
                )
            ]
        )

        packet = self.adapter(transport).collect_review_packet(
            "ddd-mall/j-store", 51, HEAD_SHA
        )

        self.assertEqual(1, packet.unresolved_actionable_threads)
        self.assertEqual(
            ["PRRC_current"],
            [comment.comment_id for comment in packet.actionable_comments],
        )
        self.assertEqual(
            ["PRRC_old", "PRRC_resolved"],
            [comment.comment_id for comment in packet.audit_comments],
        )
        self.assertNotIn("url", str(packet.to_json()).lower())
        graphql = json.loads(transport.calls[0]["body"])
        self.assertEqual(
            {
                "owner": "ddd-mall",
                "name": "j-store",
                "number": 51,
                "after": None,
            },
            graphql["variables"],
        )
        self.assertIn("ReviewThreads", graphql["query"])

    def test_review_thread_and_comment_pagination_use_fixed_queries(self) -> None:
        old = review_comment_node(
            "PRRC_old", body="Old", commit_sha="b" * 40, outdated=True
        )
        current = review_comment_node(
            "PRRC_current", body="Current", commit_sha=HEAD_SHA, outdated=False
        )
        transport = FakeTransport(
            [
                json_response(
                    200,
                    review_threads_response([], has_next=True, cursor="threads-1"),
                ),
                json_response(
                    200,
                    review_threads_response(
                        [
                            review_thread_node(
                                "PRRT_page",
                                [old],
                                comments_has_next=True,
                                comments_cursor="comments-1",
                            )
                        ]
                    ),
                ),
                json_response(
                    200,
                    review_comments_response("PRRT_page", [current]),
                ),
            ]
        )

        packet = self.adapter(transport).collect_review_packet(
            "ddd-mall/j-store", 51, HEAD_SHA
        )

        self.assertEqual(1, packet.unresolved_actionable_threads)
        self.assertEqual(3, len(transport.calls))
        second = json.loads(transport.calls[1]["body"])
        third = json.loads(transport.calls[2]["body"])
        self.assertEqual("threads-1", second["variables"]["after"])
        self.assertEqual(
            {"threadId": "PRRT_page", "after": "comments-1"},
            third["variables"],
        )
        self.assertIn("ReviewThreadComments", third["query"])

    def test_review_collection_rejects_head_change_and_invalid_cursor(self) -> None:
        for response, category in (
            (review_threads_response([], head_sha="b" * 40), "head_changed"),
            (
                review_threads_response([], has_next=True, cursor=None),
                "invalid_page_info",
            ),
        ):
            with self.subTest(category=category):
                transport = FakeTransport([json_response(200, response)])

                with self.assertRaisesRegex(GitHubAdapterError, category):
                    self.adapter(transport).collect_review_packet(
                        "ddd-mall/j-store", 51, HEAD_SHA
                    )

        comment = review_comment_node(
            "PRRC_old", body="Old", commit_sha="b" * 40, outdated=True
        )
        nested = FakeTransport(
            [
                json_response(
                    200,
                    review_threads_response(
                        [
                            review_thread_node(
                                "PRRT_head-change",
                                [comment],
                                comments_has_next=True,
                                comments_cursor="comments-1",
                            )
                        ]
                    ),
                ),
                json_response(
                    200,
                    review_comments_response(
                        "PRRT_head-change", [], head_sha="b" * 40
                    ),
                ),
            ]
        )
        with self.assertRaisesRegex(GitHubAdapterError, "head_changed"):
            self.adapter(nested).collect_review_packet(
                "ddd-mall/j-store", 51, HEAD_SHA
            )

    def test_duplicate_review_comment_id_fails_closed(self) -> None:
        comment = review_comment_node(
            "PRRC_duplicate",
            body="Duplicate",
            commit_sha=HEAD_SHA,
            outdated=False,
        )
        transport = FakeTransport(
            [
                json_response(
                    200,
                    review_threads_response(
                        [review_thread_node("PRRT_duplicate", [comment, comment])]
                    ),
                )
            ]
        )

        with self.assertRaisesRegex(GitHubAdapterError, "duplicate_review_comment"):
            self.adapter(transport).collect_review_packet(
                "ddd-mall/j-store", 51, HEAD_SHA
            )


class HostGitPusherTest(unittest.TestCase):
    def test_push_is_exact_candidate_bound_non_force_and_token_is_not_in_argv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory) / "workspace"
            workspace.mkdir()
            runner = FakeGitRunner()
            pusher = HostGitPusher(
                token_provider=FakeTokenProvider(valid_token()),
                capabilities={"create_remote_branch": True, "push_commit": True},
                runner=runner,
            )

            with patch.dict(
                "os.environ",
                {
                    "GITHUB_TOKEN": TOKEN,
                    "GH_TOKEN": TOKEN,
                    "GITHUB_ENTERPRISE_TOKEN": TOKEN,
                    "GH_ENTERPRISE_TOKEN": TOKEN,
                },
            ):
                event = pusher.push(
                    snapshot(workspace), repository="ddd-mall/j-store"
                )

            self.assertEqual(
                f"push:ddd-mall/j-store:codex/gh-50-doc-entry:{HEAD_SHA}", event
            )
            push_call = next(
                call for call in runner.calls if "push" in call["arguments"]
            )
            arguments = push_call["arguments"]
            self.assertEqual(
                f"{HEAD_SHA}:refs/heads/codex/gh-50-doc-entry", arguments[-1]
            )
            self.assertEqual("https://github.com/ddd-mall/j-store.git", arguments[-2])
            self.assertNotIn("--force", arguments)
            self.assertNotIn("--force-with-lease", arguments)
            self.assertNotIn(TOKEN, " ".join(arguments))
            self.assertNotIn(TOKEN, str(workspace))
            self.assertFalse(
                any(
                    TOKEN in path.read_text(errors="ignore")
                    for path in workspace.rglob("*")
                    if path.is_file()
                )
            )
            for alias in (
                "GITHUB_TOKEN",
                "GH_TOKEN",
                "GITHUB_ENTERPRISE_TOKEN",
                "GH_ENTERPRISE_TOKEN",
            ):
                self.assertNotIn(alias, push_call["env"])
            self.assertNotIn("force", inspect.signature(pusher.push).parameters)

    def test_disabled_capability_and_identity_mismatch_fail_before_token(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            provider = FakeTokenProvider(valid_token())
            disabled = HostGitPusher(
                token_provider=provider,
                capabilities={"create_remote_branch": True, "push_commit": False},
                runner=FakeGitRunner(),
            )

            with self.assertRaisesRegex(RuntimeError, "push_commit"):
                disabled.push(snapshot(workspace), repository="ddd-mall/j-store")
            self.assertEqual(0, provider.calls)

            runner = FakeGitRunner()
            mismatched = snapshot(workspace)
            mismatched.branch = "codex/other"
            pusher = HostGitPusher(
                token_provider=provider,
                capabilities={"create_remote_branch": True, "push_commit": True},
                runner=runner,
            )
            with self.assertRaisesRegex(RuntimeError, "current branch"):
                pusher.push(mismatched, repository="ddd-mall/j-store")
            self.assertEqual(0, provider.calls)

    def test_absent_and_expiring_tokens_fail_before_push(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            for lease in (
                None,
                InstallationToken(TOKEN, expires_at_epoch_seconds=time.time() - 1),
                InstallationToken(TOKEN, expires_at_epoch_seconds=time.time() + 10),
                InstallationToken(TOKEN, expires_at_epoch_seconds=time.time() + 45),
            ):
                with self.subTest(lease=lease):
                    runner = FakeGitRunner()
                    pusher = HostGitPusher(
                        token_provider=FakeTokenProvider(lease),
                        capabilities={
                            "create_remote_branch": True,
                            "push_commit": True,
                        },
                        runner=runner,
                    )

                    with self.assertRaisesRegex(
                        GitHubAdapterError, "token_unavailable"
                    ):
                        pusher.push(
                            snapshot(Path(directory)), repository="ddd-mall/j-store"
                        )

                    self.assertFalse(
                        any("push" in call["arguments"] for call in runner.calls)
                    )

    def test_push_failure_does_not_expose_remote_stderr_or_token(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            pusher = HostGitPusher(
                token_provider=FakeTokenProvider(valid_token()),
                capabilities={"create_remote_branch": True, "push_commit": True},
                runner=FakeGitRunner(fail_push=True),
            )

            with self.assertRaises(GitHubAdapterError) as raised:
                pusher.push(
                    snapshot(Path(directory)), repository="ddd-mall/j-store"
                )

            self.assertEqual("git_push_rejected", raised.exception.category)
            self.assertNotIn(TOKEN, str(raised.exception))

    def test_push_runner_exception_is_sanitized(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            pusher = HostGitPusher(
                token_provider=FakeTokenProvider(valid_token()),
                capabilities={"create_remote_branch": True, "push_commit": True},
                runner=FakeGitRunner(raise_on_push=True),
            )

            with self.assertRaises(GitHubAdapterError) as raised:
                pusher.push(
                    snapshot(Path(directory)), repository="ddd-mall/j-store"
                )

            self.assertEqual("git_push_failed", raised.exception.category)
            self.assertNotIn(TOKEN, str(raised.exception))


if __name__ == "__main__":
    unittest.main()
