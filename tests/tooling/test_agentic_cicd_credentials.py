from __future__ import annotations

import os
import pty
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts" / "agentic-cicd-github-token-secret.sh"


class AgenticCicdCredentialToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.log = self.root / "kubectl.log"
        kubectl = self.bin / "kubectl"
        kubectl.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$KUBECTL_LOG\"\n"
            "if [ -n \"${KUBECTL_FAIL_WITH:-}\" ] && echo \"$*\" | grep -q 'apply'; then\n"
            "  printf '%s\\n' \"$KUBECTL_FAIL_WITH\" >&2\n"
            "  exit 1\n"
            "fi\n"
            "case \"$*\" in\n"
            "  'config current-context') printf '%s\\n' \"${KUBECTL_CURRENT_CONTEXT:-kubernetes-admin@kubernetes}\" ;;\n"
            "  *'create secret generic'*) printf '%s\\n' 'apiVersion: v1' 'kind: Secret' ;;\n"
            "  *'apply'*) : ;;\n"
            "esac\n",
            encoding="utf-8",
        )
        kubectl.chmod(0o755)
        self.environment = os.environ.copy()
        self.environment["PATH"] = f"{self.bin}:{self.environment['PATH']}"
        self.environment["KUBECTL_LOG"] = str(self.log)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run_tool(self, *arguments: str, stdin: str | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(SCRIPT), *arguments],
            cwd=REPOSITORY_ROOT,
            env=self.environment,
            input=stdin,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_dry_run_reads_stdin_without_printing_or_forwarding_the_token(self) -> None:
        token = "ghs_fixture_token_not_a_secret"

        result = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--stdin",
            "--dry-run",
            stdin=token,
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn(token, result.stdout + result.stderr)
        calls = self.log.read_text(encoding="utf-8")
        self.assertNotIn(token, calls)
        self.assertIn("create secret generic symphony-github-token", calls)
        self.assertIn("apply --dry-run=server", calls)
        self.assertIn("DRY_RUN_OK", result.stdout)

    def test_apply_requires_an_explicit_mode_and_restricted_token_file(self) -> None:
        token_file = self.root / "token"
        token_file.write_text("ghs_fixture_token_not_a_secret", encoding="utf-8")
        token_file.chmod(0o600)

        missing_mode = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--token-file",
            str(token_file),
        )
        self.assertEqual(2, missing_mode.returncode)

        result = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--token-file",
            str(token_file),
            "--apply",
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("APPLIED", result.stdout)
        self.assertNotIn("--dry-run=server", self.log.read_text(encoding="utf-8"))

        conflicting_mode = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--token-file",
            str(token_file),
            "--dry-run",
            "--apply",
        )
        self.assertEqual(2, conflicting_mode.returncode)
        self.assertIn("exactly one", conflicting_mode.stderr)

    def test_rejects_world_readable_token_files_and_wrong_targets(self) -> None:
        token_file = self.root / "token"
        token_file.write_text("ghs_fixture_token_not_a_secret", encoding="utf-8")
        token_file.chmod(0o644)

        permissions = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--token-file",
            str(token_file),
            "--dry-run",
        )
        self.assertEqual(2, permissions.returncode)
        self.assertIn("0600", permissions.stderr)

        namespace = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--namespace",
            "default",
            "--stdin",
            "--dry-run",
            stdin="ghs_fixture_token_not_a_secret",
        )
        self.assertEqual(2, namespace.returncode)
        self.assertIn("agentic-cicd", namespace.stderr)

        self.environment["KUBECTL_CURRENT_CONTEXT"] = "other-development-context"
        context = self.run_tool(
            "--context",
            "other-development-context",
            "--stdin",
            "--dry-run",
            stdin="ghs_fixture_token_not_a_secret",
        )
        self.assertEqual(2, context.returncode)
        self.assertIn("kubernetes-admin@kubernetes", context.stderr)

    def test_rejects_a_token_file_with_a_trailing_newline(self) -> None:
        token_file = self.root / "token"
        token_file.write_text("ghs_fixture_token_not_a_secret\n", encoding="utf-8")
        token_file.chmod(0o600)

        result = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--token-file",
            str(token_file),
            "--dry-run",
        )

        self.assertEqual(2, result.returncode)
        self.assertIn("must not contain whitespace", result.stderr)
        self.assertNotIn("create secret generic", self.log.read_text(encoding="utf-8"))

    def test_rejects_tty_stdin_without_reading_or_echoing_a_token(self) -> None:
        token = "ghs_fixture_token_not_a_secret"
        master, slave = pty.openpty()
        try:
            process = subprocess.Popen(
                [
                    str(SCRIPT),
                    "--context",
                    "kubernetes-admin@kubernetes",
                    "--stdin",
                    "--dry-run",
                ],
                cwd=REPOSITORY_ROOT,
                env=self.environment,
                stdin=slave,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            os.close(slave)
            slave = -1
            stdout, stderr = process.communicate(timeout=5)
        finally:
            os.close(master)
            if slave >= 0:
                os.close(slave)

        self.assertEqual(2, process.returncode)
        self.assertNotIn(token, stdout + stderr)
        self.assertIn("non-interactive pipe", stderr)

    def test_unknown_argument_never_echoes_a_mistaken_token(self) -> None:
        token = "ghs_fixture_token_not_a_secret"

        result = self.run_tool(token)

        self.assertEqual(2, result.returncode)
        self.assertNotIn(token, result.stdout + result.stderr)
        self.assertIn("unsupported argument", result.stderr)

    def test_suppresses_kubectl_errors_that_might_echo_secret_data(self) -> None:
        token = "ghs_fixture_token_not_a_secret"
        self.environment["KUBECTL_FAIL_WITH"] = token

        result = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--stdin",
            "--dry-run",
            stdin=token,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(token, result.stdout + result.stderr)
        self.assertIn("Kubernetes rejected the Secret manifest", result.stderr)


if __name__ == "__main__":
    unittest.main()
