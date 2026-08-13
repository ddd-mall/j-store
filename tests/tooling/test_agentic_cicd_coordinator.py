from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.coordinator import (  # noqa: E402
    BudgetExceeded,
    ClaimConflict,
    Coordinator,
    InvalidTransition,
    KillSwitch,
    SnapshotStore,
    TaskSnapshot,
)
from agentic_cicd.workspace import PathSafetyError, WorkspaceManager  # noqa: E402


CONTRACT_PATH = REPO_ROOT / "config" / "agentic-cicd" / "state-contract.json"
BRANCH_POLICY = REPO_ROOT / "scripts" / "check-branch-policy.py"


class CoordinatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
        self.coordinator = Coordinator.from_contract(self.contract)
        self.snapshot = TaskSnapshot(issue_identifier="GH-123", state="queued")

    def test_claim_is_idempotent_for_same_run_and_rejects_competing_run(self) -> None:
        self.coordinator.claim(self.snapshot, "run-a")
        self.coordinator.claim(self.snapshot, "run-a")

        with self.assertRaises(ClaimConflict):
            self.coordinator.claim(self.snapshot, "run-b")

        self.assertEqual("run-a", self.snapshot.claim_id)

    def test_claim_rejects_non_dispatchable_state(self) -> None:
        self.snapshot.state = "blocked"

        with self.assertRaisesRegex(InvalidTransition, "not claimable"):
            self.coordinator.claim(self.snapshot, "run-a")

    def test_transition_rejects_path_not_declared_by_contract(self) -> None:
        self.coordinator.transition(self.snapshot, "waiting_ci")
        self.coordinator.transition(self.snapshot, "human_review")

        with self.assertRaises(InvalidTransition):
            self.coordinator.transition(self.snapshot, "blocked")

    def test_third_materially_different_fix_for_same_root_cause_fuses_task(self) -> None:
        first = self.coordinator.record_failed_semantic_fix(
            self.snapshot, "tests:order-total", "strategy-a"
        )
        duplicate = self.coordinator.record_failed_semantic_fix(
            self.snapshot, "tests:order-total", "strategy-a"
        )
        second = self.coordinator.record_failed_semantic_fix(
            self.snapshot, "tests:order-total", "strategy-b"
        )
        fused = self.coordinator.record_failed_semantic_fix(
            self.snapshot, "tests:order-total", "strategy-c"
        )

        self.assertEqual("retry", first)
        self.assertEqual("duplicate", duplicate)
        self.assertEqual("retry", second)
        self.assertEqual("fused", fused)
        self.assertEqual("fused", self.snapshot.state)
        self.assertEqual(
            ["strategy-a", "strategy-b"],
            self.snapshot.semantic_fix_strategies["tests:order-total"],
        )

    def test_infrastructure_retries_do_not_consume_semantic_fix_budget(self) -> None:
        for _ in range(self.contract["limits"]["infrastructure_retries"]):
            self.assertEqual("retry", self.coordinator.record_infrastructure_failure(self.snapshot))

        self.assertEqual("blocked", self.coordinator.record_infrastructure_failure(self.snapshot))
        self.assertEqual("blocked", self.snapshot.state)
        self.assertEqual({}, self.snapshot.semantic_fix_strategies)

    def test_budget_exhaustion_blocks_task(self) -> None:
        with self.assertRaises(BudgetExceeded):
            self.coordinator.consume_budget(
                self.snapshot,
                turns=self.contract["limits"]["max_turns_per_task"] + 1,
            )

        self.assertEqual("blocked", self.snapshot.state)
        self.assertEqual("budget:turns", self.snapshot.blocked_reason)

    def test_cancel_releases_claim_and_is_terminal_for_automation(self) -> None:
        self.coordinator.claim(self.snapshot, "run-a")

        self.coordinator.cancel(self.snapshot, "issue-closed")

        self.assertEqual("cancelled", self.snapshot.state)
        self.assertIsNone(self.snapshot.claim_id)
        self.assertEqual("issue-closed", self.snapshot.blocked_reason)

    def test_snapshot_store_round_trips_and_tracks_idempotency_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            store = SnapshotStore(Path(directory) / "task-state.json")
            self.snapshot.base_sha = "a" * 40
            self.snapshot.branch = "codex/gh-123-order-total"
            self.snapshot.consume_idempotency_key("draft-pr:GH-123")
            store.save(self.snapshot)

            restored = store.load()

        self.assertEqual(self.snapshot, restored)
        self.assertFalse(restored.consume_idempotency_key("draft-pr:GH-123"))
        self.assertTrue(restored.consume_idempotency_key("ready:GH-123:" + "b" * 40))

    def test_kill_switch_prevents_new_claim(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            switch = KillSwitch(Path(directory) / "STOP")
            switch.activate("operator-request")

            with self.assertRaisesRegex(RuntimeError, "operator-request"):
                self.coordinator.claim(self.snapshot, "run-a", kill_switch=switch)


class WorkspaceManagerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.remote = self.root / "remote.git"
        self.seed = self.root / "seed"
        self.checkout = self.root / "checkout"
        self.workspaces = self.root / "workspaces"

        self.git(self.root, "init", "--bare", self.remote.as_posix())
        self.git(self.root, "init", "-b", "develop", self.seed.as_posix())
        self.git(self.seed, "config", "user.name", "Agentic CI Test")
        self.git(self.seed, "config", "user.email", "agentic-ci@example.invalid")
        (self.seed / "value.txt").write_text("base-1\n", encoding="utf-8")
        self.git(self.seed, "add", "value.txt")
        self.git(self.seed, "commit", "-m", "base one")
        self.git(self.seed, "remote", "add", "origin", self.remote.as_posix())
        self.git(self.seed, "push", "-u", "origin", "develop")
        self.git(self.root, "clone", "--branch", "develop", self.remote.as_posix(), self.checkout.as_posix())
        self.stale_sha = self.git(self.checkout, "rev-parse", "HEAD").stdout.strip()

        (self.seed / "value.txt").write_text("base-2\n", encoding="utf-8")
        self.git(self.seed, "add", "value.txt")
        self.git(self.seed, "commit", "-m", "base two")
        self.git(self.seed, "push", "origin", "develop")
        self.latest_sha = self.git(self.seed, "rev-parse", "HEAD").stdout.strip()

        self.manager = WorkspaceManager(self.checkout, self.workspaces)

    def tearDown(self) -> None:
        self.temp.cleanup()

    @staticmethod
    def git(cwd: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
        )

    def test_workspace_is_created_from_fetched_origin_develop_not_stale_local_branch(self) -> None:
        workspace = self.manager.create_or_recover("GH-123", "Order Total")

        self.assertNotEqual(self.stale_sha, workspace.base_sha)
        self.assertEqual(self.latest_sha, workspace.base_sha)
        self.assertEqual("codex/gh-123-order-total", workspace.branch)
        self.assertEqual(
            self.latest_sha,
            self.git(workspace.path, "rev-parse", "HEAD").stdout.strip(),
        )
        policy = subprocess.run(
            [
                sys.executable,
                str(BRANCH_POLICY),
                "--base",
                "develop",
                "--head",
                workspace.branch,
                "--title",
                "ci(agent): exercise coordinator",
            ],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, policy.returncode, policy.stderr)

    def test_repeated_creation_recovers_same_workspace_without_new_branch(self) -> None:
        first = self.manager.create_or_recover("GH-123", "Order Total")
        second = self.manager.create_or_recover("GH-123", "Different ignored title")

        self.assertEqual(first, second)
        branches = self.git(self.checkout, "branch", "--list", "codex/gh-123-*").stdout
        self.assertEqual(1, len([line for line in branches.splitlines() if line.strip()]))

    def test_recovery_rejects_metadata_for_another_issue(self) -> None:
        workspace = self.manager.create_or_recover("GH-123", "Order Total")
        metadata = workspace.path / ".agentic-cicd" / "workspace.json"
        payload = json.loads(metadata.read_text(encoding="utf-8"))
        payload["issue_identifier"] = "GH-999"
        metadata.write_text(json.dumps(payload), encoding="utf-8")

        with self.assertRaisesRegex(RuntimeError, "issue mismatch"):
            self.manager.create_or_recover("GH-123", "Order Total")

    def test_unsafe_issue_identifier_is_rejected_before_filesystem_access(self) -> None:
        with self.assertRaises(PathSafetyError):
            self.manager.create_or_recover("GH-../../outside", "Unsafe")

    def test_remove_deletes_only_exact_clean_issue_workspace_and_preserves_branch(self) -> None:
        workspace = self.manager.create_or_recover("GH-123", "Order Total")
        outside = self.root / "outside.txt"
        outside.write_text("keep\n", encoding="utf-8")

        self.manager.remove(workspace, "GH-123")

        self.assertFalse(workspace.path.exists())
        self.assertTrue(outside.is_file())
        branches = self.git(self.checkout, "branch", "--list", workspace.branch).stdout
        self.assertIn(workspace.branch, branches)

    def test_remove_refuses_workspace_with_uncommitted_user_change(self) -> None:
        workspace = self.manager.create_or_recover("GH-123", "Order Total")
        (workspace.path / "value.txt").write_text("uncommitted\n", encoding="utf-8")

        with self.assertRaisesRegex(RuntimeError, "uncommitted changes"):
            self.manager.remove(workspace, "GH-123")

        self.assertTrue(workspace.path.is_dir())


if __name__ == "__main__":
    unittest.main()
