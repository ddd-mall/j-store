from __future__ import annotations

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
    "mark_pull_request_ready",
    "send_email",
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
    "mark_pull_request_ready",
    "send_email",
}


def validate_capabilities(level: int, capabilities: Any) -> list[str]:
    if level not in {0, 1}:
        return ["capability_level must be 0 or 1"]
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

    forbidden = PERMANENTLY_DISABLED | REMOTE_WRITE_CAPABILITIES
    enabled_forbidden = sorted(name for name in forbidden if capabilities[name])
    if enabled_forbidden:
        failures.append(
            "Level 0/1 remote or terminal capabilities must remain disabled: "
            + ", ".join(enabled_forbidden)
        )

    local_level_one = {
        "local_workspace_write",
        "freeze_local_candidate",
        "run_isolated_gate",
    }
    expected_local_value = level == 1
    mismatched_local = sorted(
        name
        for name in local_level_one
        if capabilities[name] is not expected_local_value
    )
    if mismatched_local:
        failures.append(
            f"Level {level} local capabilities are inconsistent: "
            + ", ".join(mismatched_local)
        )
    return failures
