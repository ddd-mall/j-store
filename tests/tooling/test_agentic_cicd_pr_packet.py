from __future__ import annotations

import sys
import unittest
from dataclasses import replace
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.pr_packet import (  # noqa: E402
    PullRequestPacket,
    TaskBrief,
    build_pull_request_packet,
)
from agentic_cicd.candidate import CandidateRevision  # noqa: E402
from agentic_cicd.coordinator import TaskSnapshot  # noqa: E402
from agentic_cicd.protocol import GateReceipt, GateRequest, ReviewDecision  # noqa: E402


ISSUE_BODY = """### 具体目标

生成与当前候选身份绑定的 PR 正文。

### 价值与动机

让人工审核者可以核对验收证据。

### 范围

PR packet 生成与 Ready 门禁。

### 非目标

不自动 merge、release 或 deploy。

### 验收标准

- [ ] AC-PACKET-01 | 完整 packet 可以往返解析 | Evidence: `./scripts/quality-gate.sh`
- [ ] AC-PACKET-02 | 过期 head 被拒绝 | Evidence: `./scripts/quality-gate.sh`

### 必需验证

- `./scripts/quality-gate.sh`

### 兼容性与迁移

仅增加 host-side 内部合同；无公开 API 或数据迁移。

### 恢复与回滚

停止 Supervisor 并保留 Snapshot，重启后按候选身份恢复。

### 所需人工审批

None

### 残余风险

真实 GitHub 行为仍待 disposable 仓库验收。

### 风险类型

低风险：文档、测试或可逆内部实现

### 确认

- [x] 我确认目标有界，且 Issue 中没有秘密或生产数据。
- [x] 我理解自动化只准备候选，不会自动合并、发布或执行生产操作。
"""


def accepted_snapshot() -> TaskSnapshot:
    candidate_revision = CandidateRevision.calculate_revision(
        "a" * 40, "c" * 40, "d" * 64, "e" * 64
    )
    candidate = CandidateRevision(
        base_sha="a" * 40,
        tree_sha="c" * 40,
        artifact_sha256="d" * 64,
        snapshot_policy_sha256="e" * 64,
        candidate_revision=candidate_revision,
    )
    commands = ("./scripts/quality-gate.sh",)
    policy = GateRequest.calculate_command_policy_sha256(commands)
    request = GateRequest(
        gate_id="gate-gh-50",
        issue_identifier="GH-50",
        candidate_revision=candidate,
        runner_image="registry.example/gate@sha256:" + "f" * 64,
        command_policy_sha256=policy,
        validation_commands=commands,
        timeout_seconds=600,
        requested_at="2026-08-19T00:00:00Z",
    )
    receipt = GateReceipt(
        gate_id=request.gate_id,
        issue_identifier=request.issue_identifier,
        candidate_revision=candidate,
        runner_image=request.runner_image,
        command_policy_sha256=policy,
        verdict="PASS",
        started_at="2026-08-19T00:00:01Z",
        finished_at="2026-08-19T00:00:02Z",
        exit_code=0,
        log_sha256="1" * 64,
        job_uid="job-gh-50",
        pod_uid="pod-gh-50",
        findings=(),
    )
    snapshot = TaskSnapshot(
        issue_identifier="GH-50",
        state="waiting_ci",
        base_sha="a" * 40,
        head_sha="9" * 40,
        branch="codex/gh-50-task",
        candidate_commit_sha="9" * 40,
        candidate_revision=candidate.to_json(),
        gate_request=request.to_json(),
        gate_receipt=receipt.to_json(),
        task_brief=TaskBrief.parse("GH-50", "[Agent Goal]: PR packet", ISSUE_BODY).to_json(),
        iteration_phase="complete",
    )
    snapshot.record_review_decision(
        ReviewDecision(
            verdict="PASS",
            head_sha="9" * 40,
            candidate_revision=candidate.candidate_revision,
            reviewer_role="spec-evaluator",
            reviewer_session_id="reviewer-session",
            implementer_session_id="implementer-session",
            findings=(),
        )
    )
    return snapshot


class TaskBriefTest(unittest.TestCase):
    def test_issue_form_is_parsed_into_stable_acceptance_and_commands(self) -> None:
        brief = TaskBrief.parse("GH-50", "[Agent Goal]: PR packet", ISSUE_BODY)

        self.assertEqual(("AC-PACKET-01", "AC-PACKET-02"), tuple(item.identifier for item in brief.acceptance))
        self.assertEqual(("./scripts/quality-gate.sh",), brief.validation_commands)
        self.assertEqual(brief, TaskBrief.from_json(brief.to_json()))

    def test_missing_duplicate_or_unbound_acceptance_is_rejected(self) -> None:
        cases = (
            ISSUE_BODY.replace("AC-PACKET-02", "AC-PACKET-01"),
            ISSUE_BODY.replace(" | Evidence: `./scripts/quality-gate.sh`", "", 1),
            ISSUE_BODY.replace("### 兼容性与迁移", "### 兼容性与迁移\n\nTBD\n\n### ignored"),
        )
        for body in cases:
            with self.subTest(body=body[:80]):
                with self.assertRaises(ValueError):
                    TaskBrief.parse("GH-50", "[Agent Goal]: PR packet", body)


class PullRequestPacketTest(unittest.TestCase):
    def test_complete_packet_round_trips_through_exact_rendered_body(self) -> None:
        snapshot = accepted_snapshot()

        packet = build_pull_request_packet(snapshot, target_branch="develop")
        body = packet.render()

        self.assertEqual(packet, PullRequestPacket.parse(body))
        self.assertEqual(body, PullRequestPacket.parse(body).render())
        self.assertNotIn("- [ ]", body)
        self.assertIn("AC-PACKET-01", body)
        self.assertIn("GateReceipt `gate-gh-50` PASS", body)

    def test_stale_head_candidate_or_gate_command_is_rejected(self) -> None:
        snapshot = accepted_snapshot()
        mutations = (
            lambda value: setattr(value, "head_sha", "8" * 40),
            lambda value: setattr(value, "candidate_commit_sha", "8" * 40),
            lambda value: value.gate_request.__setitem__("validation_commands", ["false"]),
        )
        for mutate in mutations:
            value = TaskSnapshot.from_json(snapshot.to_json())
            mutate(value)
            with self.subTest(snapshot=value.to_json()):
                with self.assertRaises((RuntimeError, ValueError)):
                    build_pull_request_packet(value, target_branch="develop")

    def test_tampered_body_and_unresolved_approval_are_rejected(self) -> None:
        snapshot = accepted_snapshot()
        packet = build_pull_request_packet(snapshot, target_branch="develop")
        with self.assertRaises(ValueError):
            PullRequestPacket.parse(packet.render().replace("No skipped checks.", "TBD"))

        brief = TaskBrief.from_json(snapshot.task_brief)
        snapshot.task_brief = replace(
            brief, required_human_approvals=("Security owner approval pending",)
        ).to_json()
        with self.assertRaisesRegex(RuntimeError, "human approval"):
            build_pull_request_packet(snapshot, target_branch="develop")


if __name__ == "__main__":
    unittest.main()
