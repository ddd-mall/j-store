#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]


def load_json(relative_path: str) -> dict:
    path = REPO_ROOT / relative_path
    if not path.is_file():
        raise ValueError(f"missing required file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"invalid JSON in {relative_path}: {error}") from error


def load_text(relative_path: str) -> str:
    path = REPO_ROOT / relative_path
    if not path.is_file():
        raise ValueError(f"missing required file: {relative_path}")
    return path.read_text(encoding="utf-8")


def validate() -> list[str]:
    failures: list[str] = []

    try:
        contract = load_json("config/agentic-cicd/state-contract.json")
        lock = load_json("config/agentic-cicd/symphony.lock.json")
        app_server_lock = load_json(
            "config/agentic-cicd/codex-app-server.lock.json"
        )
        iteration_schema = load_json(
            "config/agentic-cicd/iteration-packet.schema.json"
        )
        review_schema = load_json(
            "config/agentic-cicd/review-decision.schema.json"
        )
        role_routing = load_json("config/agentic-cicd/role-routing.json")
        ruleset = load_json(".github/rulesets/develop.json")
        workflow = load_text("WORKFLOW.md")
        issue_form = load_text(".github/ISSUE_TEMPLATE/agent-goal.yml")
        runbook = load_text("docs/operations/agentic-cicd-runbook.md")
    except ValueError as error:
        return [str(error)]

    states = contract.get("states", {})
    if not isinstance(states, dict) or not states:
        failures.append("state contract must define states")
    elif len(states.values()) != len(set(states.values())):
        failures.append("state labels must be unique")

    known_states = set(states)
    claimable_states = set(contract.get("claimable_states", []))
    if claimable_states != {"queued"}:
        failures.append("only the queued state may accept a new execution claim")
    for source, targets in contract.get("transitions", {}).items():
        if source not in known_states:
            failures.append(f"transition source is unknown: {source}")
        unknown_targets = set(targets) - known_states
        if unknown_targets:
            failures.append(
                f"transition targets are unknown for {source}: {sorted(unknown_targets)}"
            )

    capabilities = contract.get("capabilities", {})
    forbidden_enabled = [
        name
        for name in (
            "create_branch",
            "push_commit",
            "create_draft_pull_request",
            "mark_pull_request_ready",
            "send_email",
            "auto_approve",
            "auto_merge",
            "auto_release",
            "production_write",
        )
        if capabilities.get(name) is not False
    ]
    if forbidden_enabled:
        failures.append(
            "Level 0 capabilities must remain disabled: " + ", ".join(forbidden_enabled)
        )

    limits = contract.get("limits", {})
    if not limits or any(
        not isinstance(value, int) or isinstance(value, bool) or value <= 0
        for value in limits.values()
    ):
        failures.append("all Agentic CI/CD limits must be positive integers")

    try:
        status_rule = next(
            rule
            for rule in ruleset["rules"]
            if rule.get("type") == "required_status_checks"
        )
        ruleset_checks = {
            item["context"]
            for item in status_rule["parameters"]["required_status_checks"]
        }
        if set(contract.get("required_checks", [])) != ruleset_checks:
            failures.append("state contract required checks differ from develop ruleset")
    except (KeyError, StopIteration, TypeError):
        failures.append("develop ruleset has no readable required status checks")

    commit = lock.get("commit", "")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        failures.append("Symphony lock must use a full lowercase commit SHA")
    if not lock.get("source_url", "").endswith(commit):
        failures.append("Symphony source URL must end with the pinned commit")
    if "8001b52e3062495a16e520e4ceaf8f9de868c4d0" not in lock.get(
        "required_ancestor_commits", []
    ):
        failures.append("Symphony lock is missing the GitHub token isolation baseline")

    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", app_server_lock.get("codex_cli_version", "")):
        failures.append("Codex App Server lock must use an exact CLI version")
    if app_server_lock.get("protocol_version") != "v2":
        failures.append("Codex App Server protocol must be pinned to v2")
    if app_server_lock.get("transport") != "stdio-jsonl":
        failures.append("Codex App Server must use the local stdio JSONL transport")
    if app_server_lock.get("documentation") != "https://developers.openai.com/codex/app-server/":
        failures.append("Codex App Server lock must reference the official documentation")

    expected_packet_fields = {
        "issue_identifier",
        "objective",
        "base_sha",
        "head_sha",
        "acceptance",
        "review_findings",
        "ci_failures",
        "attempts_by_root_cause",
        "budget_remaining",
        "validation_commands",
        "implementer_session_id",
    }
    if set(iteration_schema.get("required", [])) != expected_packet_fields:
        failures.append("IterationPacket schema is missing required planning inputs")
    if set(review_schema.get("properties", {}).get("verdict", {}).get("enum", [])) != {"PASS", "FAIL"}:
        failures.append("ReviewDecision schema must allow only PASS or FAIL")
    if review_schema.get("properties", {}).get("head_sha", {}).get("pattern") != "^[0-9a-f]{40}$":
        failures.append("ReviewDecision must bind to a full lowercase head SHA")

    implementer = role_routing.get("implementer", {})
    reviewer = role_routing.get("independent_reviewer", {})
    if implementer.get("sandbox") != "workspace-write" or implementer.get("may_approve_candidate") is not False:
        failures.append("Implementer role must write only the workspace and cannot approve")
    if reviewer.get("sandbox") != "read-only" or reviewer.get("may_modify_candidate") is not False:
        failures.append("Independent reviewer must be read-only")
    if not {"product-steward", "spec-evaluator"}.issubset(set(reviewer.get("roles", []))):
        failures.append("Independent review routing is missing required reviewer roles")

    workflow_requirements = (
        "kind: github",
        "- agent:queued",
        "max_concurrent_agents: 1",
        "max_turns: 12",
        "thread_sandbox: read-only",
        "type: readOnly",
        "sandbox_approval: true",
        "rules: true",
        "mcp_elicitations: true",
        "不得自动合并",
    )
    for fragment in workflow_requirements:
        if fragment not in workflow:
            failures.append(f"WORKFLOW.md is missing required boundary: {fragment}")
    if "approval_policy: never" in workflow or re.search(
        r"(?m)^\s*thread_sandbox:\s*danger-full-access\s*$", workflow
    ):
        failures.append("WORKFLOW.md enables an unsafe Codex policy")

    if "agent:candidate" not in issue_form:
        failures.append("Issue Form does not apply the non-dispatching intake label")
    if contract.get("dispatch_label") in issue_form:
        failures.append("Issue Form must not auto-apply the dispatch label")
    if "不得包含密码、token、私钥或生产数据" not in issue_form:
        failures.append("Issue Form does not warn against secret submission")

    for boundary in ("只读观察", "不得自动合并", "kill switch"):
        if boundary not in runbook:
            failures.append(f"runbook is missing boundary: {boundary}")

    return failures


def main() -> int:
    failures = validate()
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        print(f"{len(failures)} agentic CI/CD contract check(s) failed.", file=sys.stderr)
        return 1

    print("PASS: agentic CI/CD contracts are consistent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
