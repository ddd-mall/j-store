import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
HOOK_SCRIPT = REPOSITORY_ROOT / "scripts" / "git-hooks" / "pre-push"


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


if __name__ == "__main__":
    unittest.main()
