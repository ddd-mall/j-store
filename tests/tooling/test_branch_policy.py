import subprocess
import sys
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
POLICY_SCRIPT = REPOSITORY_ROOT / "scripts" / "check-branch-policy.py"


class BranchPolicyTest(unittest.TestCase):
    def run_policy(
        self, base: str, head: str, title: str
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(POLICY_SCRIPT),
                "--base",
                base,
                "--head",
                head,
                "--title",
                title,
            ],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )

    def assert_allowed(self, base: str, head: str, title: str) -> None:
        result = self.run_policy(base, head, title)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("PASS", result.stdout)

    def assert_rejected(self, base: str, head: str, title: str) -> None:
        result = self.run_policy(base, head, title)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("FAIL", result.stderr)

    def test_short_lived_branch_targets_develop(self) -> None:
        self.assert_allowed(
            "develop", "feature/batch-domain-events", "feat(event): support batch publish"
        )

    def test_codex_branch_targets_develop(self) -> None:
        self.assert_allowed(
            "develop", "codex/outbox-transport-modules", "refactor(outbox): split transports"
        )

    def test_dependabot_branch_targets_develop(self) -> None:
        self.assert_allowed(
            "develop", "dependabot/gradle/kotlin-2.3.0", "deps: update Kotlin to 2.3.0"
        )

    def test_master_can_sync_back_to_develop(self) -> None:
        self.assert_allowed(
            "develop", "master", "chore(sync): merge master into develop"
        )

    def test_release_branch_targets_master(self) -> None:
        self.assert_allowed(
            "master", "release/v1.2.3-rc.1", "chore(release): prepare v1.2.3"
        )

    def test_hotfix_branch_targets_master(self) -> None:
        self.assert_allowed(
            "master", "hotfix/v1.2.4", "fix(release): restore payment callback"
        )

    def test_feature_branch_cannot_target_master(self) -> None:
        self.assert_rejected(
            "master", "feature/batch-domain-events", "feat(event): support batch publish"
        )

    def test_release_branch_cannot_target_develop(self) -> None:
        self.assert_rejected(
            "develop", "release/v1.2.3", "chore(release): prepare v1.2.3"
        )

    def test_uppercase_branch_name_is_rejected(self) -> None:
        self.assert_rejected(
            "develop", "feature/Batch-Events", "feat(event): support batch publish"
        )

    def test_malformed_semver_release_branch_is_rejected(self) -> None:
        self.assert_rejected(
            "master", "release/v1.2.3-rc..1", "chore(release): prepare v1.2.3"
        )

    def test_non_conventional_pull_request_title_is_rejected(self) -> None:
        self.assert_rejected(
            "develop", "feature/batch-domain-events", "Support batch publish"
        )

    def test_unmanaged_base_branch_is_rejected(self) -> None:
        self.assert_rejected(
            "release/v1.2.3", "fix/release-notes", "docs(release): fix release notes"
        )


if __name__ == "__main__":
    unittest.main()
