from __future__ import annotations

import json
import re
import subprocess
import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.capabilities import (  # noqa: E402
    REMOTE_WRITE_CAPABILITIES,
    validate_capabilities,
    validate_disposable_github_e2e,
)
STATE_CONTRACT = REPO_ROOT / "config" / "agentic-cicd" / "state-contract.json"
DISPOSABLE_CONTRACT = (
    REPO_ROOT
    / "config"
    / "agentic-cicd"
    / "state-contract.level2-disposable.example.json"
)
SYMPHONY_LOCK = REPO_ROOT / "config" / "agentic-cicd" / "symphony.lock.json"
WORKFLOW = REPO_ROOT / "WORKFLOW.md"
ISSUE_FORM = REPO_ROOT / ".github" / "ISSUE_TEMPLATE" / "agent-goal.yml"
RUNBOOK = REPO_ROOT / "docs" / "operations" / "agentic-cicd-runbook.md"
MAIN_REQUIREMENT = REPO_ROOT / "docs" / "spec" / "agentic-cicd" / "requirement.md"
MAIN_TASKS = REPO_ROOT / "docs" / "spec" / "agentic-cicd" / "tasks.md"
LEVEL_ONE_REQUIREMENT = (
    REPO_ROOT
    / "docs"
    / "spec"
    / "changes"
    / "agentic-cicd-local-candidate-loop"
    / "requirement.md"
)
REVIEW_PROPOSAL = (
    REPO_ROOT / "config" / "agentic-cicd" / "review-proposal.schema.json"
)


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

        self.assertEqual(3, contract["version"])
        self.assertEqual(0, contract["capability_level"])
        labels = contract["states"]
        self.assertEqual(len(labels), len(set(labels.values())))
        self.assertEqual("agent:queued", contract["dispatch_label"])
        self.assertEqual(["queued"], contract["claimable_states"])
        self.assertEqual("agent:human-review", contract["human_terminal_state"])
        self.assertEqual(2, contract["limits"]["semantic_fixes_per_root_cause"])
        self.assertEqual(1, contract["limits"]["max_concurrent_agents"])
        self.assertFalse(contract["capabilities"]["auto_merge"])
        self.assertFalse(contract["capabilities"]["auto_release"])
        self.assertFalse(contract["capabilities"]["local_workspace_write"])
        self.assertTrue(all(value > 0 for value in contract["limits"].values()))

        capabilities = contract["capabilities"]
        self.assertTrue(capabilities["bootstrap_local_workspace"])
        self.assertFalse(capabilities["freeze_local_candidate"])
        self.assertFalse(capabilities["run_isolated_gate"])
        self.assertFalse(capabilities["create_remote_branch"])
        self.assertFalse(capabilities["write_issue_comment"])
        self.assertFalse(capabilities["update_issue_labels"])
        self.assertFalse(capabilities["request_pull_request_review"])
        self.assertNotIn("send_email", capabilities)
        self.assertNotIn("create_branch", capabilities)

        known_states = set(labels)
        for source, targets in contract["transitions"].items():
            self.assertIn(source, known_states)
            self.assertLessEqual(set(targets), known_states)

    def test_capability_contract_rejects_ambiguous_or_remote_level_one_writes(self) -> None:
        contract = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))
        capabilities = dict(contract["capabilities"])
        capabilities["create_branch"] = False
        self.assertTrue(validate_capabilities(0, capabilities))

        capabilities = dict(contract["capabilities"])
        capabilities["local_workspace_write"] = True
        self.assertTrue(validate_capabilities(0, capabilities))

        capabilities = dict(contract["capabilities"])
        capabilities.update(
            local_workspace_write=True,
            freeze_local_candidate=True,
            run_isolated_gate=True,
            create_remote_branch=True,
        )
        failures = validate_capabilities(1, capabilities)
        self.assertTrue(any("remote" in failure for failure in failures))

        for remote_capability in (
            "write_issue_comment",
            "update_issue_labels",
            "request_pull_request_review",
        ):
            capabilities = dict(contract["capabilities"])
            capabilities.update(
                local_workspace_write=True,
                freeze_local_candidate=True,
                run_isolated_gate=True,
            )
            capabilities[remote_capability] = True
            failures = validate_capabilities(1, capabilities)
            self.assertTrue(any("remote" in failure for failure in failures))

    def test_disposable_level_two_profile_is_complete_but_not_authoritative(self) -> None:
        authoritative = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))
        candidate = json.loads(DISPOSABLE_CONTRACT.read_text(encoding="utf-8"))

        failures = validate_disposable_github_e2e(
            repository="ddd-mall/agentic-cicd-disposable",
            repository_url="https://github.com/ddd-mall/agentic-cicd-disposable.git",
            contract=candidate,
            authoritative_contract=authoritative,
        )

        self.assertEqual([], failures)
        self.assertEqual(0, authoritative["capability_level"])
        self.assertEqual(2, candidate["capability_level"])
        self.assertTrue(
            all(candidate["capabilities"][name] for name in REMOTE_WRITE_CAPABILITIES)
        )
        for name in ("auto_approve", "auto_merge", "auto_release", "production_write"):
            self.assertFalse(candidate["capabilities"][name])

    def test_disposable_preflight_rejects_identity_profile_and_gate_drift(self) -> None:
        authoritative = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))
        candidate = json.loads(DISPOSABLE_CONTRACT.read_text(encoding="utf-8"))

        cases = (
            (
                "ddd-mall/j-store",
                "https://github.com/ddd-mall/j-store.git",
                candidate,
                authoritative,
                "must not target",
            ),
            (
                "DDD-MALL/J-STORE",
                "https://github.com/DDD-MALL/J-STORE.git",
                candidate,
                authoritative,
                "must not target",
            ),
            (
                "not-canonical",
                "https://github.com/not-canonical.git",
                candidate,
                authoritative,
                "canonical owner/name",
            ),
            (
                "ddd-mall/disposable",
                "https://github.com/ddd-mall/other.git",
                candidate,
                authoritative,
                "exactly match",
            ),
            (
                "ddd-mall/disposable",
                "https://github.com/ddd-mall/disposable.git",
                authoritative,
                authoritative,
                "requires a Level 2",
            ),
            (
                "ddd-mall/disposable",
                "https://github.com/ddd-mall/disposable.git",
                {**candidate, "required_checks": ["quality"]},
                authoritative,
                "differ from",
            ),
        )
        for repository, url, profile, authoritative_profile, expected in cases:
            with self.subTest(expected=expected):
                failures = validate_disposable_github_e2e(
                    repository=repository,
                    repository_url=url,
                    contract=profile,
                    authoritative_contract=authoritative_profile,
                )
                self.assertTrue(any(expected in failure for failure in failures), failures)

        for capability in (
            "run_isolated_gate",
            "write_issue_comment",
            "request_pull_request_review",
        ):
            profile = json.loads(DISPOSABLE_CONTRACT.read_text(encoding="utf-8"))
            profile["capabilities"][capability] = False
            failures = validate_disposable_github_e2e(
                repository="ddd-mall/disposable",
                repository_url="https://github.com/ddd-mall/disposable.git",
                contract=profile,
                authoritative_contract=authoritative,
            )
            self.assertTrue(any("requires all" in failure for failure in failures), failures)

        profile = json.loads(DISPOSABLE_CONTRACT.read_text(encoding="utf-8"))
        profile["capabilities"]["auto_merge"] = True
        failures = validate_disposable_github_e2e(
            repository="ddd-mall/disposable",
            repository_url="https://github.com/ddd-mall/disposable.git",
            contract=profile,
            authoritative_contract=authoritative,
        )
        self.assertTrue(any("terminal capabilities" in failure for failure in failures))

        failures = validate_disposable_github_e2e(
            repository="ddd-mall/disposable",
            repository_url="https://github.com/ddd-mall/disposable.git",
            contract=candidate,
            authoritative_contract=candidate,
        )
        self.assertTrue(any("must remain at Level 0" in failure for failure in failures))

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

    def test_runbook_preserves_the_real_level_two_e2e_contract(self) -> None:
        runbook = RUNBOOK.read_text(encoding="utf-8")

        for scenario in (
            "GH15-01",
            "GH15-02",
            "GH15-03",
            "GH15-04",
            "GH15-05",
            "GH15-06",
            "GH15-07",
            "GH16-01",
        ):
            self.assertIn(scenario, runbook)
        self.assertIn("不构成外部写授权", runbook)
        self.assertIn("单元测试、fake transport、bundle结果", runbook)
        self.assertIn("停止host Supervisor service", runbook)

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
            "repo: $JSTORE_SYMPHONY_REPOSITORY",
            "token: $JSTORE_SYMPHONY_GITHUB_TOKEN",
            "- agent:queued",
            "max_concurrent_agents: 1",
            "max_turns: 1",
            "thread_sandbox: read-only",
            "granular:",
            "sandbox_approval: false",
            "rules: false",
            "mcp_elicitations: false",
            "request_permissions: false",
            "skill_approval: false",
            "origin/develop",
            "docs/steering/agent-governance.md",
            "Draft PR",
            "不得自动合并",
        )
        for fragment in expected_fragments:
            self.assertIn(fragment, workflow)

        self.assertNotIn("approval_policy: never", workflow)
        self.assertNotIn("reject:", workflow)
        self.assertNotRegex(workflow, r"(?m)^\s*thread_sandbox:\s*danger-full-access\s*$")

    def test_agentic_cicd_runbook_is_indexed_by_repository_guidance(self) -> None:
        agents = (REPO_ROOT / "AGENTS.md").read_text(encoding="utf-8")
        automation_runbook = (
            REPO_ROOT / "docs" / "operations" / "agent-automation-runbook.md"
        ).read_text(encoding="utf-8")

        self.assertIn("docs/operations/agentic-cicd-runbook.md", agents)
        self.assertIn("agentic-cicd-runbook.md", automation_runbook)

    def test_runtime_preflight_is_required_before_starting_symphony(self) -> None:
        governance = (REPO_ROOT / "scripts" / "check-agent-governance.sh").read_text(
            encoding="utf-8"
        )
        runbook = RUNBOOK.read_text(encoding="utf-8")

        self.assertIn("scripts/check-agentic-cicd-runtime.py", governance)
        self.assertIn("check-agentic-cicd-runtime.py", runbook)
        self.assertIn("JSTORE_SYMPHONY_SOURCE", runbook)

    def test_model_review_output_cannot_claim_trusted_runtime_identity(self) -> None:
        proposal = json.loads(REVIEW_PROPOSAL.read_text(encoding="utf-8"))

        self.assertEqual(
            {
                "verdict",
                "head_sha",
                "candidate_revision",
                "reviewer_role",
                "findings",
            },
            set(proposal["required"]),
        )
        self.assertNotIn("reviewer_session_id", proposal["properties"])
        self.assertNotIn("implementer_session_id", proposal["properties"])

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

    def test_specs_lock_runtime_write_and_level_one_boundaries(self) -> None:
        requirement = MAIN_REQUIREMENT.read_text(encoding="utf-8")
        level_one = LEVEL_ONE_REQUIREMENT.read_text(encoding="utf-8")

        self.assertIn("运行环境默认只读", requirement)
        self.assertIn("仅 Implementer 阶段按能力合同获得当前 workspace 写权限", requirement)
        for capability in (
            "`local_workspace_write`",
            "`freeze_local_candidate`",
            "`run_isolated_gate`",
        ):
            self.assertIn(capability, level_one)
        self.assertIn("只把", level_one)
        self.assertIn("全部GitHub写入", level_one)

    def test_tasks_preserve_issue_control_plane_and_rollout_order(self) -> None:
        tasks = MAIN_TASKS.read_text(encoding="utf-8")

        for behavior in (
            "唯一Workpad评论的compare-and-reconcile",
            "互斥`agent:*`标签迁移",
            "准入缺失说明",
            "`agent:human-review`",
            "review request",
        ):
            self.assertIn(behavior, tasks)
        self.assertIn("在迭代5完成后", tasks)
        self.assertIn("重新切换为只读profile", tasks)
        self.assertIn("连续观察两周", tasks)

    def test_email_is_not_a_product_capability_or_handoff_requirement(self) -> None:
        contract = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))
        requirement = MAIN_REQUIREMENT.read_text(encoding="utf-8")
        workflow = WORKFLOW.read_text(encoding="utf-8")

        self.assertNotIn("send_email", contract["capabilities"])
        self.assertIn("不建设独立邮件通知链路", requirement)
        self.assertIn("Issue 状态、Workpad 或 PR review request", requirement)
        self.assertNotIn("发送邮件", workflow)
        self.assertIn("不得执行未获能力合同授权的外部通知或控制面写入", workflow)

    def test_handoff_requires_one_native_signal_and_retries_enhancements(self) -> None:
        requirement = MAIN_REQUIREMENT.read_text(encoding="utf-8")
        tasks = MAIN_TASKS.read_text(encoding="utf-8")

        self.assertIn("至少一种信号成功才算完成人工交接", requirement)
        self.assertIn("全部信号都失败", requirement)
        self.assertIn("至少一种信号成功才完成handoff", tasks)
        self.assertIn("全部失败时handoff保持pending", tasks)

    def test_contract_does_not_embed_model_pricing(self) -> None:
        contract = json.loads(STATE_CONTRACT.read_text(encoding="utf-8"))
        level_one_design = (
            REPO_ROOT
            / "docs"
            / "spec"
            / "changes"
            / "agentic-cicd-local-candidate-loop"
            / "design.md"
        ).read_text(encoding="utf-8")

        self.assertGreater(contract["limits"]["max_cost_microusd"], 0)
        self.assertNotIn("estimated_input_microusd_per_million_tokens", contract["limits"])
        self.assertNotIn("estimated_output_microusd_per_million_tokens", contract["limits"])
        self.assertIn("不维护模型费率表", level_one_design)
        self.assertIn("不是当前能力升级的完成条件", level_one_design)


if __name__ == "__main__":
    unittest.main()
