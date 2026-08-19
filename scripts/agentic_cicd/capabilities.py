from __future__ import annotations

import re
from typing import Any


CAPABILITY_NAMES = {
    "read_only_observation",
    "bootstrap_local_workspace",
    "local_workspace_write",
    "freeze_local_candidate",
    "run_isolated_gate",
    "create_remote_branch",
    "push_commit",
    "create_draft_pull_request",
    "write_issue_comment",
    "update_issue_labels",
    "mark_pull_request_ready",
    "request_pull_request_review",
    "auto_approve",
    "auto_merge",
    "auto_release",
    "production_write",
}

PERMANENTLY_DISABLED = {
    "auto_approve",
    "auto_merge",
    "auto_release",
    "production_write",
}

REMOTE_WRITE_CAPABILITIES = {
    "create_remote_branch",
    "push_commit",
    "create_draft_pull_request",
    "write_issue_comment",
    "update_issue_labels",
    "mark_pull_request_ready",
    "request_pull_request_review",
}

LOCAL_LEVEL_ONE_CAPABILITIES = {
    "local_workspace_write",
    "freeze_local_candidate",
    "run_isolated_gate",
}

GITHUB_REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
PRODUCTION_REPOSITORY = "ddd-mall/j-store"


def validate_capabilities(level: int, capabilities: Any) -> list[str]:
    if level not in {0, 1, 2}:
        return ["capability_level must be 0, 1, or 2"]
    if not isinstance(capabilities, dict):
        return ["state contract capabilities must be an object"]

    failures: list[str] = []
    actual_names = set(capabilities)
    missing = CAPABILITY_NAMES - actual_names
    unknown = actual_names - CAPABILITY_NAMES
    if missing:
        failures.append(f"capabilities are missing required fields: {sorted(missing)}")
    if unknown:
        failures.append(f"capabilities contain unknown fields: {sorted(unknown)}")
    invalid_types = sorted(
        name for name, value in capabilities.items() if not isinstance(value, bool)
    )
    if invalid_types:
        failures.append(f"capabilities must be booleans: {invalid_types}")
    if failures:
        return failures

    if capabilities["bootstrap_local_workspace"] is not True:
        failures.append("trusted local workspace bootstrap must remain enabled")
    if capabilities["read_only_observation"] is not True:
        failures.append("read-only observation must remain enabled")

    enabled_terminal = sorted(
        name for name in PERMANENTLY_DISABLED if capabilities[name]
    )
    if enabled_terminal:
        failures.append(
            "terminal capabilities must remain disabled at every level: "
            + ", ".join(enabled_terminal)
        )

    expected_local_value = level >= 1
    mismatched_local = sorted(
        name
        for name in LOCAL_LEVEL_ONE_CAPABILITIES
        if capabilities[name] is not expected_local_value
    )
    if mismatched_local:
        failures.append(
            f"Level {level} local capabilities are inconsistent: "
            + ", ".join(mismatched_local)
        )

    enabled_remote = {
        name for name in REMOTE_WRITE_CAPABILITIES if capabilities[name]
    }
    if level < 2 and enabled_remote:
        failures.append(
            f"Level {level} remote capabilities must remain disabled: "
            + ", ".join(sorted(enabled_remote))
        )
    if level == 2 and not enabled_remote:
        failures.append("Level 2 must enable at least one remote capability")
    dependencies = {
        "push_commit": {"create_remote_branch"},
        "create_draft_pull_request": {"create_remote_branch", "push_commit"},
        "mark_pull_request_ready": {"create_draft_pull_request"},
        "request_pull_request_review": {"mark_pull_request_ready"},
    }
    for capability, required in dependencies.items():
        if not capabilities[capability]:
            continue
        missing_dependencies = sorted(
            dependency for dependency in required if not capabilities[dependency]
        )
        if missing_dependencies:
            failures.append(
                f"{capability} requires " + ", ".join(missing_dependencies)
            )
    return failures


def validate_disposable_github_e2e(
    *,
    repository: Any,
    repository_url: Any,
    contract: Any,
    authoritative_contract: Any,
) -> list[str]:
    """Validate a configuration-only Level 2 profile for a disposable repository."""

    failures: list[str] = []
    if not isinstance(repository, str) or GITHUB_REPOSITORY.fullmatch(repository) is None:
        failures.append("repository must use canonical owner/name form")
    elif repository.casefold() == PRODUCTION_REPOSITORY.casefold():
        failures.append("disposable E2E must not target ddd-mall/j-store")

    expected_url = (
        f"https://github.com/{repository}.git" if isinstance(repository, str) else None
    )
    if not isinstance(repository_url, str) or repository_url != expected_url:
        failures.append("repository URL must exactly match the disposable HTTPS repository")

    if not isinstance(contract, dict):
        failures.append("candidate state contract must be a JSON object")
        return failures

    level = contract.get("capability_level")
    capabilities = contract.get("capabilities")
    failures.extend(validate_capabilities(level, capabilities))
    if level != 2:
        failures.append("disposable GitHub E2E requires a Level 2 candidate contract")
    if isinstance(capabilities, dict) and CAPABILITY_NAMES <= set(capabilities):
        missing_local = sorted(
            name for name in LOCAL_LEVEL_ONE_CAPABILITIES if capabilities[name] is not True
        )
        missing_remote = sorted(
            name for name in REMOTE_WRITE_CAPABILITIES if capabilities[name] is not True
        )
        if missing_local:
            failures.append(
                "disposable GitHub E2E requires all Level 1 capabilities: "
                + ", ".join(missing_local)
            )
        if missing_remote:
            failures.append(
                "disposable GitHub E2E requires all Level 2 GitHub capabilities: "
                + ", ".join(missing_remote)
            )

    if not isinstance(authoritative_contract, dict):
        failures.append("authoritative state contract must be a JSON object")
        return failures
    authoritative_level = authoritative_contract.get("capability_level")
    authoritative_capabilities = authoritative_contract.get("capabilities")
    authoritative_failures = validate_capabilities(
        authoritative_level, authoritative_capabilities
    )
    if authoritative_level != 0:
        failures.append("authoritative state contract must remain at Level 0")
    failures.extend(
        "authoritative state contract: " + failure
        for failure in authoritative_failures
    )

    candidate_checks = contract.get("required_checks")
    expected_required_checks = authoritative_contract.get("required_checks")
    if (
        not isinstance(candidate_checks, list)
        or not candidate_checks
        or not all(isinstance(value, str) and value.strip() for value in candidate_checks)
        or len(candidate_checks) != len(set(candidate_checks))
    ):
        failures.append("candidate required_checks must be unique non-empty strings")
    if (
        not isinstance(expected_required_checks, list)
        or not expected_required_checks
        or not all(
            isinstance(value, str) and value.strip()
            for value in expected_required_checks
        )
    ):
        failures.append("authoritative required_checks are invalid")
    elif isinstance(candidate_checks, list) and set(candidate_checks) != set(
        expected_required_checks
    ):
        failures.append("candidate required_checks differ from the authoritative contract")
    return failures
