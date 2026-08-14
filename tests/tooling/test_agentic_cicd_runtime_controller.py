from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.coordinator import SnapshotStore, TaskSnapshot
from scripts.agentic_cicd.runtime_controller import (
    GateReceiptStore,
    PhaseContextStore,
    ReviewProposalStore,
    SymphonyWorkspaceBootstrap,
    TaskStateInitializer,
    TurnStateController,
)


class SymphonyWorkspaceBootstrapTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.remote = self.root / "remote.git"
        self.seed = self.root / "seed"
        self.workspace = self.root / "workspaces" / "GH-123"
        self.git(self.root, "init", "--bare", self.remote.as_posix())
        self.git(self.root, "init", "-b", "master", self.seed.as_posix())
        self.git(self.seed, "config", "user.name", "Runtime Controller Test")
        self.git(self.seed, "config", "user.email", "controller@example.invalid")
        (self.seed / "baseline.txt").write_text("master\n", encoding="utf-8")
        self.git(self.seed, "add", "baseline.txt")
        self.git(self.seed, "commit", "-m", "master baseline")
        self.git(self.seed, "remote", "add", "origin", self.remote.as_posix())
        self.git(self.seed, "push", "-u", "origin", "master")

        self.git(self.seed, "checkout", "-b", "develop")
        (self.seed / "baseline.txt").write_text("develop\n", encoding="utf-8")
        self.git(self.seed, "add", "baseline.txt")
        self.git(self.seed, "commit", "-m", "develop baseline")
        self.develop_sha = self.git(self.seed, "rev-parse", "HEAD").stdout.strip()
        self.git(self.seed, "push", "-u", "origin", "develop")
        self.workspace.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    @staticmethod
    def git(cwd: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
        )

    def test_bootstrap_ignores_default_master_and_locks_fetched_develop(self) -> None:
        result = SymphonyWorkspaceBootstrap().bootstrap(
            repository_url=self.remote.as_posix(),
            workspace=self.workspace,
        )

        self.assertEqual(self.develop_sha, result.base_sha)
        self.assertEqual("codex/gh-123-task", result.branch)
        self.assertEqual(
            self.develop_sha,
            self.git(self.workspace, "rev-parse", "HEAD").stdout.strip(),
        )
        self.assertEqual(
            "develop\n",
            (self.workspace / "baseline.txt").read_text(encoding="utf-8"),
        )

    def test_bootstrap_writes_excluded_trusted_metadata(self) -> None:
        result = SymphonyWorkspaceBootstrap().bootstrap(
            repository_url=self.remote.as_posix(),
            workspace=self.workspace,
        )

        metadata_path = self.workspace / ".agentic-cicd" / "workspace.json"
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        self.assertEqual(result.base_sha, metadata["base_sha"])
        self.assertEqual(result.branch, metadata["branch"])
        self.assertEqual("GH-123", metadata["issue_identifier"])
        self.assertEqual("", self.git(self.workspace, "status", "--porcelain").stdout)

    def test_bootstrap_result_initializes_recoverable_host_snapshot(self) -> None:
        result = SymphonyWorkspaceBootstrap().bootstrap(
            repository_url=self.remote.as_posix(),
            workspace=self.workspace,
        )

        path = TaskStateInitializer(self.root / "state").initialize(
            result, self.workspace
        )
        snapshot = SnapshotStore(path).load()

        self.assertEqual("GH-123", snapshot.issue_identifier)
        self.assertEqual(result.base_sha, snapshot.base_sha)
        self.assertEqual(result.base_sha, snapshot.head_sha)
        self.assertEqual(result.branch, snapshot.branch)
        self.assertEqual(str(self.workspace.resolve()), snapshot.workspace)
        self.assertEqual("implement", snapshot.iteration_phase)

    def test_bootstrap_rejects_noncanonical_workspace_identity(self) -> None:
        unsafe_workspace = self.root / "workspaces" / "../../unsafe"

        with self.assertRaisesRegex(ValueError, "GH-<positive-number>"):
            SymphonyWorkspaceBootstrap().bootstrap(
                repository_url=self.remote.as_posix(),
                workspace=unsafe_workspace,
            )


class ReviewProposalStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.snapshot_path = self.root / "tasks" / "GH-123.json"
        self.snapshot = TaskSnapshot(
            issue_identifier="GH-123",
            state="queued",
            base_sha="a" * 40,
            head_sha="b" * 40,
            iteration_phase="review",
            implementer_session_id="implementer-session",
        )
        SnapshotStore(self.snapshot_path).save(self.snapshot)
        self.store = ReviewProposalStore(self.root)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    @staticmethod
    def proposal(head_sha: str = "b" * 40) -> dict:
        return {
            "verdict": "PASS",
            "head_sha": head_sha,
            "reviewer_role": "spec-evaluator",
            "findings": [],
        }

    def test_accepts_schema_valid_exact_head_proposal_only_in_review_phase(self) -> None:
        path = self.store.submit("GH-123", self.proposal())

        self.assertEqual((self.root / "proposals" / "GH-123.json").resolve(), path)
        self.assertEqual(self.proposal(), json.loads(path.read_text(encoding="utf-8")))

    def test_rejects_stale_head_and_model_supplied_runtime_identity(self) -> None:
        with self.assertRaisesRegex(ValueError, "candidate head"):
            self.store.submit("GH-123", self.proposal("c" * 40))

        payload = self.proposal()
        payload["reviewer_session_id"] = "forged"
        with self.assertRaisesRegex(ValueError, "contract"):
            self.store.submit("GH-123", payload)

    def test_rejects_proposal_outside_review_phase(self) -> None:
        self.snapshot.iteration_phase = "implement"
        SnapshotStore(self.snapshot_path).save(self.snapshot)

        with self.assertRaisesRegex(RuntimeError, "review phase"):
            self.store.submit("GH-123", self.proposal())


class RuntimePhaseControllerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.workspace = self.root / "GH-123"
        self.workspace.mkdir()
        subprocess.run(["git", "init", "-b", "task"], cwd=self.workspace, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Phase Test"], cwd=self.workspace, check=True)
        subprocess.run(["git", "config", "user.email", "phase@example.invalid"], cwd=self.workspace, check=True)
        (self.workspace / "candidate.txt").write_text("candidate\n", encoding="utf-8")
        subprocess.run(["git", "add", "candidate.txt"], cwd=self.workspace, check=True)
        subprocess.run(["git", "commit", "-m", "candidate"], cwd=self.workspace, check=True, capture_output=True)
        self.head = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.workspace,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        self.snapshot_path = self.root / "state" / "tasks" / "GH-123.json"
        self.snapshot = TaskSnapshot(
            issue_identifier="GH-123",
            state="queued",
            base_sha=self.head,
            head_sha=self.head,
            branch="codex/gh-123-task",
            workspace=str(self.workspace.resolve()),
        )
        SnapshotStore(self.snapshot_path).save(self.snapshot)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_level_zero_observes_read_only_while_future_implementer_is_workspace_write(self) -> None:
        observer = PhaseContextStore(
            self.root / "state", workspace_write_enabled=False
        ).load("GH-123", self.workspace)
        implementer = PhaseContextStore(
            self.root / "state", workspace_write_enabled=True
        ).load("GH-123", self.workspace)

        self.assertEqual("observer", observer.role)
        self.assertEqual("read-only", observer.thread_sandbox)
        self.assertTrue(observer.complete_turn)
        self.assertEqual("implementer", implementer.role)
        self.assertEqual("workspace-write", implementer.thread_sandbox)
        self.assertTrue(implementer.complete_turn)

    def test_review_is_read_only_and_validate_complete_do_not_start_model(self) -> None:
        self.snapshot.iteration_phase = "review"
        self.snapshot.implementer_session_id = "implementer-session"
        SnapshotStore(self.snapshot_path).save(self.snapshot)
        context = PhaseContextStore(
            self.root / "state", workspace_write_enabled=True
        ).load("GH-123", self.workspace)
        self.assertEqual("reviewer", context.role)
        self.assertEqual("read-only", context.thread_sandbox)

        for phase in ("validate", "complete"):
            self.snapshot.iteration_phase = phase
            SnapshotStore(self.snapshot_path).save(self.snapshot)
            context = PhaseContextStore(
                self.root / "state", workspace_write_enabled=True
            ).load("GH-123", self.workspace)
            self.assertFalse(context.run_model)

    def test_turn_completion_and_gate_receipt_advance_without_executing_workspace_code(self) -> None:
        controller = TurnStateController(
            self.root / "state", workspace_write_enabled=True
        )
        controller.complete_turn(
            "GH-123",
            self.workspace,
            session_id="implementer-session",
            thread_id="thread-1",
            turn_id="turn-1",
        )
        self.assertEqual("validate", SnapshotStore(self.snapshot_path).load().iteration_phase)

        GateReceiptStore(self.root / "state").record(
            "GH-123",
            {"gate_id": "gate-1", "verdict": "PASS", "head_sha": self.head, "findings": []},
        )
        self.assertEqual("review", SnapshotStore(self.snapshot_path).load().iteration_phase)

    def test_observation_turn_completes_without_forging_implementation_identity(self) -> None:
        controller = TurnStateController(
            self.root / "state", workspace_write_enabled=False
        )
        controller.complete_turn(
            "GH-123",
            self.workspace,
            session_id="session",
            thread_id="thread",
            turn_id="turn",
        )
        snapshot = SnapshotStore(self.snapshot_path).load()
        self.assertEqual("complete", snapshot.iteration_phase)
        self.assertIsNone(snapshot.implementer_session_id)
        self.assertEqual("observer", snapshot.last_turn_receipt["role"])


if __name__ == "__main__":
    unittest.main()
