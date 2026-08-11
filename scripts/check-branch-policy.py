#!/usr/bin/env python3
"""Validate the repository's pull-request branch and title policy."""

import argparse
import re
import sys


SHORT_LIVED_PREFIXES = (
    "feature",
    "fix",
    "refactor",
    "perf",
    "docs",
    "test",
    "build",
    "ci",
    "chore",
    "revert",
    "codex",
    "dependabot",
)
SLUG = r"[a-z0-9][a-z0-9._-]*(?:/[a-z0-9][a-z0-9._-]*)*"
SHORT_LIVED_BRANCH = re.compile(
    rf"^(?:{'|'.join(SHORT_LIVED_PREFIXES)})/{SLUG}$"
)
SEMVER_PRERELEASE_IDENTIFIER = (
    r"(?:0|[1-9][0-9]*|[0-9a-z-]*[a-z-][0-9a-z-]*)"
)
SEMVER = (
    r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
    rf"(?:-{SEMVER_PRERELEASE_IDENTIFIER}"
    rf"(?:\.{SEMVER_PRERELEASE_IDENTIFIER})*)?"
)
MASTER_SOURCE_BRANCH = re.compile(rf"^(?:release|hotfix)/{SEMVER}$")
PULL_REQUEST_TITLE = re.compile(
    r"^(?:feat|fix|refactor|perf|docs|test|build|ci|chore|deps|revert)"
    r"(?:\([a-z0-9][a-z0-9._/-]*\))?!?: \S.*$"
)


def validate(base: str, head: str, title: str) -> list[str]:
    errors: list[str] = []

    if base == "develop":
        if head != "master" and not SHORT_LIVED_BRANCH.fullmatch(head):
            errors.append(
                "PRs into develop must come from master or a lowercase short-lived "
                f"branch with one of these prefixes: {', '.join(SHORT_LIVED_PREFIXES)}"
            )
    elif base == "master":
        if not MASTER_SOURCE_BRANCH.fullmatch(head):
            errors.append(
                "PRs into master must come from release/v<semver> or hotfix/v<semver>"
            )
    else:
        errors.append("managed PRs must target develop or master")

    if not PULL_REQUEST_TITLE.fullmatch(title):
        errors.append(
            "PR title must follow Conventional Commits, for example "
            "feat(order): reserve inventory"
        )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="pull request base branch")
    parser.add_argument("--head", required=True, help="pull request head branch")
    parser.add_argument("--title", required=True, help="pull request title")
    arguments = parser.parse_args()

    errors = validate(arguments.base, arguments.head, arguments.title)
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1

    print(
        f"PASS: {arguments.head} -> {arguments.base} and PR title comply with policy."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
