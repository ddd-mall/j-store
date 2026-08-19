from __future__ import annotations

import importlib.util
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "scripts"))

CONTROLLER_PATH = REPOSITORY_ROOT / "scripts" / "agentic-cicd-controller.py"
SPEC = importlib.util.spec_from_file_location("agentic_cicd_controller_cli", CONTROLLER_PATH)
assert SPEC is not None and SPEC.loader is not None
CONTROLLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONTROLLER)

from agentic_cicd.coordinator import SnapshotStore, TaskSnapshot  # noqa: E402
from agentic_cicd.github_lifecycle import CandidatePromoter  # noqa: E402


DISPOSABLE_CONTRACT = (
    REPOSITORY_ROOT
    / "config"
    / "agentic-cicd"
    / "state-contract.level2-disposable.example.json"
)


class GitHubRuntimePrerequisiteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.state_root = self.root / "state"
        self.snapshot_path = self.state_root / "tasks" / "GH-123.json"
        SnapshotStore(self.snapshot_path).save(
            TaskSnapshot(
                issue_identifier="GH-123",
                state="waiting_ci",
                repository="ddd-mall/agentic-cicd-disposable",
                iteration_phase="complete",
                candidate_revision={"candidate_revision": "candidate-123"},
            )
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_invalid_runtime_credentials_fail_before_any_lifecycle_side_effect(self) -> None:
        valid_environment = {
            "JSTORE_SYMPHONY_GITHUB_TOKEN": "fixture-installation-token",
            "JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS": str(time.time() + 600),
            "JSTORE_GITHUB_APP_LOGIN": "jstore-agentic-cicd[bot]",
            "JSTORE_GITHUB_REVIEWER": "jstore-maintainer",
        }
        cases = (
            (
                {
                    key: value
                    for key, value in valid_environment.items()
                    if key != "JSTORE_SYMPHONY_GITHUB_TOKEN"
                },
                "token_unavailable",
            ),
            (
                {
                    **valid_environment,
                    "JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS": str(
                        time.time() - 1
                    ),
                },
                "token_unavailable",
            ),
            (
                {
                    key: value
                    for key, value in valid_environment.items()
                    if key != "JSTORE_GITHUB_APP_LOGIN"
                },
                "App login",
            ),
            (
                {**valid_environment, "JSTORE_GITHUB_APP_LOGIN": "not-a-bot"},
                "App login",
            ),
            (
                {
                    key: value
                    for key, value in valid_environment.items()
                    if key != "JSTORE_GITHUB_REVIEWER"
                },
                "reviewer login",
            ),
            (
                {**valid_environment, "JSTORE_GITHUB_REVIEWER": "reviewer[bot]"},
                "reviewer login",
            ),
            (
                {
                    **valid_environment,
                    "JSTORE_GITHUB_REVIEWER": "jstore-agentic-cicd[bot]",
                },
                "different",
            ),
        )

        for environment, message in cases:
            with self.subTest(message=message):
                before = self.snapshot_path.read_bytes()
                with (
                    patch.dict(os.environ, environment, clear=True),
                    patch.object(CONTROLLER, "GitHubRestGraphqlAdapter") as adapter,
                    patch.object(CONTROLLER, "HostGitPusher") as pusher,
                    patch.object(CONTROLLER, "GitHubLifecycleController") as lifecycle,
                    patch.object(CandidatePromoter, "promote") as promote,
                    self.assertRaisesRegex((RuntimeError, ValueError), message),
                ):
                    CONTROLLER.reconcile_github_if_ready(
                        issue_identifier="GH-123",
                        state_root=self.state_root,
                        artifact_root=self.root / "artifacts",
                        contract_path=DISPOSABLE_CONTRACT,
                    )

                adapter.assert_not_called()
                pusher.assert_not_called()
                lifecycle.assert_not_called()
                promote.assert_not_called()
                self.assertEqual(before, self.snapshot_path.read_bytes())

    def test_valid_runtime_credentials_reach_the_single_lifecycle_entrypoint(self) -> None:
        environment = {
            "JSTORE_SYMPHONY_GITHUB_TOKEN": "fixture-installation-token",
            "JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS": str(time.time() + 600),
            "JSTORE_GITHUB_APP_LOGIN": "jstore-agentic-cicd[bot]",
            "JSTORE_GITHUB_REVIEWER": "jstore-maintainer",
        }
        before = self.snapshot_path.read_bytes()
        with (
            patch.dict(os.environ, environment, clear=True),
            patch.object(CONTROLLER, "GitHubRestGraphqlAdapter") as adapter,
            patch.object(CONTROLLER, "HostGitPusher") as pusher,
            patch.object(CONTROLLER, "GitHubLifecycleController") as lifecycle,
        ):
            CONTROLLER.reconcile_github_if_ready(
                issue_identifier="GH-123",
                state_root=self.state_root,
                artifact_root=self.root / "artifacts",
                contract_path=DISPOSABLE_CONTRACT,
            )

        adapter.assert_called_once()
        pusher.assert_called_once()
        lifecycle.assert_called_once()
        lifecycle.return_value.reconcile.assert_called_once()
        self.assertEqual(before, self.snapshot_path.read_bytes())


if __name__ == "__main__":
    unittest.main()
