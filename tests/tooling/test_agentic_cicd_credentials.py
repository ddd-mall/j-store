from __future__ import annotations

import os
import pty
import subprocess
import tempfile
import time
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts" / "agentic-cicd-github-token-secret.sh"
CODEX_AUTH_SCRIPT = (
    REPOSITORY_ROOT / "scripts" / "agentic-cicd-codex-auth-secret.sh"
)


class AgenticCicdCredentialToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.log = self.root / "kubectl.log"
        self.codex_config_capture = self.root / "captured-config.toml"
        self.codex_config = self.root / "config.toml"
        self.codex_config.write_text(
            'model = "gpt-fixture"\n'
            'model_provider = "fixture"\n'
            'model_reasoning_effort = "medium"\n'
            'approval_policy = "never"\n'
            '\n'
            '[model_providers.fixture]\n'
            'name = "Fixture provider"\n'
            'base_url = "https://fixture.example/v1"\n'
            'wire_api = "responses"\n'
            'requires_openai_auth = true\n'
            '\n'
            '[model_providers.unused]\n'
            'name = "Must not propagate"\n'
            'base_url = "https://unused.example/v1"\n'
            'env_key = "UNUSED_SECRET"\n'
            '\n'
            '[mcp_servers.untrusted]\n'
            'command = "/tmp/untrusted"\n',
            encoding="utf-8",
        )
        self.codex_config.chmod(0o600)
        kubectl = self.bin / "kubectl"
        kubectl.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$KUBECTL_LOG\"\n"
            "for argument in \"$@\"; do\n"
            "  case \"$argument\" in\n"
            "    --from-file=config.toml=*) "
            "cp \"${argument#--from-file=config.toml=}\" \"$CODEX_CONFIG_CAPTURE\" ;;\n"
            "  esac\n"
            "done\n"
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
        self.environment["CODEX_CONFIG_CAPTURE"] = str(self.codex_config_capture)
        self.token_expiry = str(int(time.time()) + 3600)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run_tool(
        self,
        *arguments: str,
        stdin: str | None = None,
        include_expiry: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        command = [str(SCRIPT), *arguments]
        if include_expiry:
            command.extend(
                ["--expires-at-epoch-seconds", self.token_expiry]
            )
        return subprocess.run(
            command,
            cwd=REPOSITORY_ROOT,
            env=self.environment,
            input=stdin,
            capture_output=True,
            text=True,
            check=False,
        )

    def run_codex_auth_tool(
        self, *arguments: str, stdin: str | None = None
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(CODEX_AUTH_SCRIPT), *arguments],
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
        self.assertIn(
            f"--from-literal=expires-at-epoch-seconds={self.token_expiry}", calls
        )
        self.assertIn("apply --dry-run=server", calls)
        self.assertIn("DRY_RUN_OK", result.stdout)

    def test_requires_a_bounded_future_expiration_before_reading_token(self) -> None:
        missing = self.run_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--stdin",
            "--dry-run",
            stdin="ghs_fixture_token_not_a_secret",
            include_expiry=False,
        )
        self.assertEqual(2, missing.returncode)
        self.assertIn("expires-at-epoch-seconds", missing.stderr)

        for expiry in (str(int(time.time()) + 60), str(int(time.time()) + 10800)):
            result = self.run_tool(
                "--context",
                "kubernetes-admin@kubernetes",
                "--stdin",
                "--dry-run",
                "--expires-at-epoch-seconds",
                expiry,
                stdin="ghs_fixture_token_not_a_secret",
                include_expiry=False,
            )
            self.assertEqual(2, result.returncode)
            self.assertIn("5 minutes and 2 hours", result.stderr)
        calls = self.log.read_text(encoding="utf-8")
        self.assertNotIn("create secret generic", calls)

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
                    "--expires-at-epoch-seconds",
                    self.token_expiry,
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

    def test_codex_auth_dry_run_does_not_print_or_forward_the_api_key(self) -> None:
        api_key = "sk-fixture-codex-key-not-a-secret"
        auth = f'{{"OPENAI_API_KEY":"{api_key}"}}'

        result = self.run_codex_auth_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--stdin",
            "--config-file",
            str(self.codex_config),
            "--dry-run",
            stdin=auth,
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn(api_key, result.stdout + result.stderr)
        calls = self.log.read_text(encoding="utf-8")
        self.assertNotIn(api_key, calls)
        self.assertIn("create secret generic symphony-codex-auth", calls)
        self.assertIn("--from-file=auth.json=", calls)
        self.assertIn("--from-file=config.toml=", calls)
        reduced_config = self.codex_config_capture.read_text(encoding="utf-8")
        self.assertIn('model = "gpt-fixture"', reduced_config)
        self.assertIn('base_url = "https://fixture.example/v1"', reduced_config)
        self.assertIn(
            "[features]\nuse_legacy_landlock = true",
            reduced_config,
        )
        for excluded in (
            "approval_policy",
            "model_providers.unused",
            "UNUSED_SECRET",
            "mcp_servers",
            "/tmp/untrusted",
        ):
            self.assertNotIn(excluded, reduced_config)
        self.assertIn("apply --dry-run=server", calls)
        self.assertIn("DRY_RUN_OK", result.stdout)

    def test_codex_auth_apply_requires_restricted_file_and_explicit_mode(self) -> None:
        auth_file = self.root / "auth.json"
        auth_file.write_text(
            '{"OPENAI_API_KEY":"sk-fixture-codex-key-not-a-secret"}',
            encoding="utf-8",
        )
        auth_file.chmod(0o600)

        missing_mode = self.run_codex_auth_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--auth-file",
            str(auth_file),
            "--config-file",
            str(self.codex_config),
        )
        self.assertEqual(2, missing_mode.returncode)

        result = self.run_codex_auth_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--auth-file",
            str(auth_file),
            "--config-file",
            str(self.codex_config),
            "--apply",
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("APPLIED", result.stdout)

        auth_file.chmod(0o644)
        permissions = self.run_codex_auth_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--auth-file",
            str(auth_file),
            "--config-file",
            str(self.codex_config),
            "--dry-run",
        )
        self.assertEqual(2, permissions.returncode)
        self.assertIn("0600", permissions.stderr)

    def test_codex_auth_rejects_extra_fields_and_whitespace_in_key(self) -> None:
        invalid_inputs = (
            '{"OPENAI_API_KEY":"sk-fixture-codex-key-not-a-secret","tokens":{}}',
            '{"OPENAI_API_KEY":"sk-fixture codex key not a secret"}',
            '{"access_token":"fixture-access-token-not-a-secret"}',
        )

        for auth in invalid_inputs:
            with self.subTest(auth=auth):
                result = self.run_codex_auth_tool(
                    "--context",
                    "kubernetes-admin@kubernetes",
                    "--stdin",
                    "--config-file",
                    str(self.codex_config),
                    "--dry-run",
                    stdin=auth,
                )
                self.assertEqual(2, result.returncode)
                self.assertIn("only one nonblank OPENAI_API_KEY", result.stderr)

        self.assertNotIn(
            "create secret generic symphony-codex-auth",
            self.log.read_text(encoding="utf-8"),
        )

    def test_codex_auth_rejects_unsafe_or_unsupported_provider_config(self) -> None:
        invalid_configs = (
            self.codex_config.read_text(encoding="utf-8").replace(
                "https://fixture.example/v1", "http://fixture.example/v1"
            ),
            self.codex_config.read_text(encoding="utf-8").replace(
                'wire_api = "responses"', 'wire_api = "chat"'
            ),
            self.codex_config.read_text(encoding="utf-8").replace(
                "requires_openai_auth = true", "requires_openai_auth = false", 1
            ),
            self.codex_config.read_text(encoding="utf-8").replace(
                'requires_openai_auth = true',
                'requires_openai_auth = true\nenv_key = "LEAKED_KEY"',
                1,
            ),
        )
        auth = '{"OPENAI_API_KEY":"sk-fixture-codex-key-not-a-secret"}'

        for index, content in enumerate(invalid_configs):
            with self.subTest(index=index):
                config = self.root / f"invalid-{index}.toml"
                config.write_text(content, encoding="utf-8")
                config.chmod(0o600)
                result = self.run_codex_auth_tool(
                    "--context",
                    "kubernetes-admin@kubernetes",
                    "--stdin",
                    "--config-file",
                    str(config),
                    "--dry-run",
                    stdin=auth,
                )
                self.assertEqual(2, result.returncode)
                self.assertIn("credential-free HTTPS Responses provider", result.stderr)

    def test_codex_auth_does_not_echo_malformed_provider_config(self) -> None:
        marker = "provider-config-must-not-be-echoed"
        config = self.root / "malformed.toml"
        config.write_text(f'model = "{marker}"\ninvalid = [', encoding="utf-8")
        config.chmod(0o600)

        result = self.run_codex_auth_tool(
            "--context",
            "kubernetes-admin@kubernetes",
            "--stdin",
            "--config-file",
            str(config),
            "--dry-run",
            stdin='{"OPENAI_API_KEY":"sk-fixture-codex-key-not-a-secret"}',
        )

        self.assertEqual(2, result.returncode)
        self.assertNotIn(marker, result.stdout + result.stderr)
        self.assertIn("credential-free HTTPS Responses provider", result.stderr)


if __name__ == "__main__":
    unittest.main()
