from __future__ import annotations

import json
import re
import subprocess
import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
STATE_CONTRACT = REPO_ROOT / "config" / "agentic-cicd" / "state-contract.json"
SYMPHONY_LOCK = REPO_ROOT / "config" / "agentic-cicd" / "symphony.lock.json"
WORKFLOW = REPO_ROOT / "WORKFLOW.md"
ISSUE_FORM = REPO_ROOT / ".github" / "ISSUE_TEMPLATE" / "agent-goal.yml"
RUNBOOK = REPO_ROOT / "docs" / "operations" / "agentic-cicd-runbook.md"


class AgenticCicdContractTest(unittest.TestCase):
    def test_contract_checker_accepts_repository_configuration(self) -> None:
        result = subprocess.run(
            [sys.executable, "scripts/check-agentic-cicd.py"],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS: agentic CI/CD contracts are consistent", result.stdout)

    def test_state_contract_has_single_human_terminal_and_bounded_rework(self) -> None:
        contract = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))

        labels = contract["states"]
        self.assertEqual(len(labels), len(set(labels.values())))
        self.assertEqual("agent:queued", contract["dispatch_label"])
        self.assertEqual(["queued"], contract["claimable_states"])
        self.assertEqual("agent:human-review", contract["human_terminal_state"])
        self.assertEqual(2, contract["limits"]["semantic_fixes_per_root_cause"])
        self.assertEqual(1, contract["limits"]["max_concurrent_agents"])
        self.assertFalse(contract["capabilities"]["auto_merge"])
        self.assertFalse(contract["capabilities"]["auto_release"])
        self.assertTrue(all(value > 0 for value in contract["limits"].values()))

        known_states = set(labels)
        for source, targets in contract["transitions"].items():
            self.assertIn(source, known_states)
            self.assertLessEqual(set(targets), known_states)

    def test_required_checks_match_develop_ruleset(self) -> None:
        contract = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))
        ruleset = json.loads(
            (REPO_ROOT / ".github" / "rulesets" / "develop.json").read_text(
                encoding="utf-8"
            )
        )
        status_rule = next(
            rule for rule in ruleset["rules"] if rule["type"] == "required_status_checks"
        )
        ruleset_checks = {
            item["context"]
            for item in status_rule["parameters"]["required_status_checks"]
        }

        self.assertEqual(ruleset_checks, set(contract["required_checks"]))

    def test_symphony_runtime_is_pinned_to_reviewed_full_commit(self) -> None:
        lock = json.loads(SYMPHONY_LOCK.read_text(encoding="utf-8"))

        self.assertEqual("openai/symphony", lock["repository"])
        self.assertEqual("elixir", lock["implementation"])
        self.assertRegex(lock["commit"], r"^[0-9a-f]{40}$")
        self.assertIn(
            "8001b52e3062495a16e520e4ceaf8f9de868c4d0",
            lock["required_ancestor_commits"],
        )
        self.assertTrue(lock["source_url"].endswith(lock["commit"]))

    def test_workflow_uses_github_issue_gating_and_safe_codex_defaults(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")

        expected_fragments = (
            "kind: github",
            'repo: "ddd-mall/j-store"',
            "token: $JSTORE_SYMPHONY_GITHUB_TOKEN",
            "- agent:queued",
            "max_concurrent_agents: 1",
            "max_turns: 12",
            "thread_sandbox: read-only",
            "sandbox_approval: true",
            "rules: true",
            "mcp_elicitations: true",
            "origin/develop",
            "docs/steering/agent-governance.md",
            "Draft PR",
            "不得自动合并",
        )
        for fragment in expected_fragments:
            self.assertIn(fragment, workflow)

        self.assertNotIn("approval_policy: never", workflow)
        self.assertNotRegex(workflow, r"(?m)^\s*thread_sandbox:\s*danger-full-access\s*$")

    def test_agentic_cicd_runbook_is_indexed_by_repository_guidance(self) -> None:
        agents = (REPO_ROOT / "AGENTS.md").read_text(encoding="utf-8")
        automation_runbook = (
            REPO_ROOT / "docs" / "operations" / "agent-automation-runbook.md"
        ).read_text(encoding="utf-8")

        self.assertIn("docs/operations/agentic-cicd-runbook.md", agents)
        self.assertIn("agentic-cicd-runbook.md", automation_runbook)

    def test_branch_examples_follow_existing_lowercase_branch_policy(self) -> None:
        artifacts = [
            WORKFLOW,
            REPO_ROOT / "docs" / "spec" / "agentic-cicd" / "requirement.md",
            REPO_ROOT / "docs" / "spec" / "agentic-cicd" / "design.md",
            REPO_ROOT / "docs" / "spec" / "agentic-cicd" / "tasks.md",
        ]

        for artifact in artifacts:
            content = artifact.read_text(encoding="utf-8")
            self.assertNotIn("codex/GH-", content, artifact)
            self.assertIn("codex/gh-", content, artifact)

    def test_issue_form_collects_bounded_and_verifiable_goal(self) -> None:
        issue_form = ISSUE_FORM.read_text(encoding="utf-8")

        self.assertIn("agent:candidate", issue_form)
        self.assertNotIn("agent:queued", issue_form)
        for field_id in (
            "goal",
            "value",
            "scope",
            "out_of_scope",
            "acceptance",
            "validation",
            "risk",
        ):
            self.assertRegex(issue_form, rf"(?m)^\s*id:\s*{re.escape(field_id)}\s*$")
        self.assertGreaterEqual(issue_form.count("required: true"), 7)
        self.assertIn("不得包含密码、token、私钥或生产数据", issue_form)

    def test_runbook_keeps_external_writes_behind_explicit_approval(self) -> None:
        runbook = RUNBOOK.read_text(encoding="utf-8")

        for boundary in (
            "只读观察",
            "不得自动合并",
            "develop ruleset",
            "secret-scan",
            "GitHub App",
            "kill switch",
        ):
            self.assertIn(boundary, runbook)


if __name__ == "__main__":
    unittest.main()
