import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
HOOK_SCRIPT = REPOSITORY_ROOT / "scripts" / "git-hooks" / "pre-push"
PRE_COMMIT_HOOK_SCRIPT = REPOSITORY_ROOT / "scripts" / "git-hooks" / "pre-commit"


class SpotlessPrePushHookTest(unittest.TestCase):
    def setUp(self) -> None:
        if shutil.which("sh") is None:
            self.skipTest("A POSIX shell is required to exercise the Git hook")

        self.temp_dir = tempfile.TemporaryDirectory()
        self.repository = Path(self.temp_dir.name)
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Spotless Hook Test")
        self.git("config", "user.email", "spotless-hook@example.invalid")

        (self.repository / "src").mkdir()
        (self.repository / "src" / "App.kt").write_text("class App\n", encoding="utf-8")
        (self.repository / "README.md").write_text("initial\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "initial")
        self.base_sha = self.git("rev-parse", "HEAD").stdout.strip()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=self.repository,
            check=True,
            capture_output=True,
            text=True,
        )

    def run_hook(self, remote_sha: str, local_sha: str) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment["SPOTLESS_PRE_PUSH_DRY_RUN"] = "1"
        return subprocess.run(
            ["sh", HOOK_SCRIPT.as_posix()],
            cwd=self.repository,
            input=f"refs/heads/main {local_sha} refs/heads/main {remote_sha}\n",
            env=environment,
            capture_output=True,
            text=True,
        )

    def test_docs_only_push_skips_spotless(self) -> None:
        (self.repository / "README.md").write_text("documentation only\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-m", "docs")
        local_sha = self.git("rev-parse", "HEAD").stdout.strip()

        result = self.run_hook(self.base_sha, local_sha)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("No pushed Kotlin/Java files; skipping Spotless.", result.stdout)
        self.assertNotIn("src/App.kt", result.stdout)

    def test_source_push_selects_only_existing_format_targets(self) -> None:
        (self.repository / "src" / "App.kt").write_text("class UpdatedApp\n", encoding="utf-8")
        (self.repository / "build.gradle.kts").write_text("plugins {}\n", encoding="utf-8")
        (self.repository / "README.md").write_text("also changed\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "source")
        local_sha = self.git("rev-parse", "HEAD").stdout.strip()

        result = self.run_hook(self.base_sha, local_sha)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("build.gradle.kts", result.stdout)
        self.assertIn("src/App.kt", result.stdout)
        self.assertNotIn("README.md", result.stdout)


class SpotlessPreCommitHookTest(unittest.TestCase):
    def setUp(self) -> None:
        if shutil.which("sh") is None:
            self.skipTest("A POSIX shell is required to exercise the Git hook")

        self.temp_dir = tempfile.TemporaryDirectory()
        self.repository = Path(self.temp_dir.name) / "repository"
        self.repository.mkdir()
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Spotless Hook Test")
        self.git("config", "user.email", "spotless-hook@example.invalid")

        (self.repository / "src").mkdir()
        (self.repository / "src" / "App.kt").write_text("class App\n", encoding="utf-8")
        (self.repository / "README.md").write_text("initial\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "initial")

        self.gradle_log = Path(self.temp_dir.name) / "gradle.log"
        self.fake_gradle = Path(self.temp_dir.name) / "fake-gradle"
        self.fake_gradle.write_text(
            """#!/bin/sh
set -eu
printf '%s\\n' "$*" >> "$SPOTLESS_TEST_LOG"
if [ "$1" = "spotlessApply" ]; then
    printf 'class FormattedApp\\n' > "$SPOTLESS_TEST_REPOSITORY/src/App.kt"
fi
""",
            encoding="utf-8",
        )
        self.fake_gradle.chmod(0o755)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=self.repository,
            check=True,
            capture_output=True,
            text=True,
        )

    def run_hook(self) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment["SPOTLESS_PRE_COMMIT_GRADLE"] = self.fake_gradle.as_posix()
        environment["SPOTLESS_TEST_LOG"] = self.gradle_log.as_posix()
        environment["SPOTLESS_TEST_REPOSITORY"] = self.repository.as_posix()
        return subprocess.run(
            ["sh", PRE_COMMIT_HOOK_SCRIPT.as_posix()],
            cwd=self.repository,
            env=environment,
            capture_output=True,
            text=True,
        )

    def test_docs_only_commit_skips_spotless(self) -> None:
        (self.repository / "README.md").write_text("documentation only\n", encoding="utf-8")
        self.git("add", "README.md")

        result = self.run_hook()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("No staged Kotlin/Java files; skipping Spotless.", result.stdout)
        self.assertFalse(self.gradle_log.exists())

    def test_source_commit_formats_and_restages_target(self) -> None:
        (self.repository / "src" / "App.kt").write_text("class    App\n", encoding="utf-8")
        self.git("add", "src/App.kt")

        result = self.run_hook()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Spotless will format these staged files:", result.stdout)
        self.assertIn("spotlessApply -PspotlessFilesFile=", self.gradle_log.read_text())
        staged_content = self.git("show", ":src/App.kt").stdout
        self.assertEqual("class FormattedApp\n", staged_content)

    def test_partially_staged_source_aborts_without_formatting(self) -> None:
        app_file = self.repository / "src" / "App.kt"
        app_file.write_text("class StagedApp\n", encoding="utf-8")
        self.git("add", "src/App.kt")
        app_file.write_text("class UnstagedApp\n", encoding="utf-8")

        result = self.run_hook()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("also have unstaged changes", result.stderr)
        self.assertFalse(self.gradle_log.exists())
        self.assertEqual("class StagedApp\n", self.git("show", ":src/App.kt").stdout)
        self.assertEqual("class UnstagedApp\n", app_file.read_text(encoding="utf-8"))

    def test_staged_source_missing_from_worktree_aborts_without_formatting(self) -> None:
        app_file = self.repository / "src" / "App.kt"
        app_file.write_text("class StagedApp\n", encoding="utf-8")
        self.git("add", "src/App.kt")
        app_file.unlink()

        result = self.run_hook()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("also have unstaged changes", result.stderr)
        self.assertFalse(self.gradle_log.exists())
        self.assertEqual("class StagedApp\n", self.git("show", ":src/App.kt").stdout)
        self.assertFalse(app_file.exists())

    def test_source_commit_runs_from_linked_worktree(self) -> None:
        branch = "linked-worktree"
        worktree = Path(self.temp_dir.name) / "linked"
        self.git("branch", branch)
        self.git("worktree", "add", worktree.as_posix(), branch)
        app_file = worktree / "src" / "App.kt"
        app_file.write_text("class    App\n", encoding="utf-8")
        subprocess.run(
            ["git", "add", "src/App.kt"],
            cwd=worktree,
            check=True,
            capture_output=True,
            text=True,
        )
        environment = os.environ.copy()
        environment["SPOTLESS_PRE_COMMIT_GRADLE"] = self.fake_gradle.as_posix()
        environment["SPOTLESS_TEST_LOG"] = self.gradle_log.as_posix()
        environment["SPOTLESS_TEST_REPOSITORY"] = worktree.as_posix()

        result = subprocess.run(
            ["sh", PRE_COMMIT_HOOK_SCRIPT.as_posix()],
            cwd=worktree,
            env=environment,
            capture_output=True,
            text=True,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        staged_content = subprocess.run(
            ["git", "show", ":src/App.kt"],
            cwd=worktree,
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        self.assertEqual("class FormattedApp\n", staged_content)


if __name__ == "__main__":
    unittest.main()
