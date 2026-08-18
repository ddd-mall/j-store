from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.runtime import RuntimePreflight


class AgenticCicdRuntimePreflightTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.symphony = self.root / "symphony"
        self.symphony.mkdir()
        self.git("init")
        self.git("config", "user.name", "Runtime Test")
        self.git("config", "user.email", "runtime@example.invalid")
        (self.symphony / "README.md").write_text("fixture\n", encoding="utf-8")
        (self.symphony / "elixir").mkdir()
        (self.symphony / "elixir" / "mix.exs").write_text(
            "defmodule Fixture.MixProject do\nend\n", encoding="utf-8"
        )
        (self.symphony / "elixir" / "mise.toml").write_text(
            '[tools]\nerlang = "28"\nelixir = "1.19.5-otp-28"\n',
            encoding="utf-8",
        )
        github = self.symphony / "elixir" / "lib" / "symphony_elixir" / "github"
        github.mkdir(parents=True)
        (github / "client.ex").write_text(
            '''def secret_environment_names(tracker_settings) do
  provider = provider_settings(tracker_settings)
  ["GITHUB_TOKEN", "GH_TOKEN", "GITHUB_ENTERPRISE_TOKEN", "GH_ENTERPRISE_TOKEN" |
    env_reference_names([provider["token"]])]
end
''',
            encoding="utf-8",
        )
        codex = self.symphony / "elixir" / "lib" / "symphony_elixir" / "codex"
        codex.mkdir(parents=True)
        (codex / "app_server.ex").write_text(
            '''defp open_port(dynamic_tool_binding), do: tracker_secret_port_env(dynamic_tool_binding)
defp local_launch_command(dynamic_tool_binding), do: [tracker_secret_unset_command(dynamic_tool_binding)]
defp remote_launch_command(workspace, dynamic_tool_binding), do: [tracker_secret_unset_command(dynamic_tool_binding)]
defp tracker_secret_port_env(dynamic_tool_binding), do: dynamic_tool_binding.secret_environment_names
defp tracker_secret_unset_command(dynamic_tool_binding), do: dynamic_tool_binding.secret_environment_names
''',
            encoding="utf-8",
        )
        self.git("add", "README.md", "elixir")
        self.git("commit", "-m", "fixture")
        self.commit = self.git("rev-parse", "HEAD").stdout.strip()

        self.symphony_lock = self.root / "symphony.lock.json"
        self.symphony_lock.write_text(
            json.dumps(
                {
                    "repository": "openai/symphony",
                    "implementation": "elixir",
                    "commit": self.commit,
                    "required_ancestor_commits": [self.commit],
                    "github_secret_environment_names": [
                        "GITHUB_TOKEN",
                        "GH_TOKEN",
                        "GITHUB_ENTERPRISE_TOKEN",
                        "GH_ENTERPRISE_TOKEN",
                        "JSTORE_SYMPHONY_GITHUB_TOKEN",
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.codex_lock = self.root / "codex.lock.json"
        self.codex_lock.write_text(
            json.dumps({"version_policy": "installed-stable"}), encoding="utf-8"
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=self.symphony,
            check=True,
            capture_output=True,
            text=True,
        )

    def preflight(
        self,
        *,
        codex_version: str = "codex-cli 0.146.0",
        available_tools: set[str] | None = None,
    ) -> RuntimePreflight:
        return RuntimePreflight(
            symphony_source=self.symphony,
            symphony_lock=self.symphony_lock,
            codex_lock=self.codex_lock,
            command_output=lambda command: (
                codex_version if command == ("codex", "--version") else ""
            ),
            tool_available=lambda tool: tool
            in (available_tools if available_tools is not None else {"mise"}),
        )

    def test_accepts_exact_source_commit_ancestor_and_managed_runtime(self) -> None:
        result = self.preflight().check()

        self.assertTrue(result.ok)
        self.assertEqual((), result.failures)
        self.assertIn(f"Symphony source HEAD matches {self.commit}", result.checks)
        self.assertIn("Codex CLI reports stable version 0.146.0", result.checks)
        self.assertIn("Elixir runtime is available through mise", result.checks)

    def test_source_only_accepts_pinned_source_without_host_runtime(self) -> None:
        result = self.preflight(
            codex_version="codex-cli 0.147.0", available_tools=set()
        ).check_symphony_source()

        self.assertTrue(result.ok)
        self.assertIn(f"Symphony source HEAD matches {self.commit}", result.checks)
        self.assertFalse(any("Codex CLI" in check for check in result.checks))
        self.assertFalse(any("Elixir runtime" in check for check in result.checks))

    def test_source_only_still_rejects_missing_secret_scrubbing(self) -> None:
        client = (
            self.symphony
            / "elixir"
            / "lib"
            / "symphony_elixir"
            / "github"
            / "client.ex"
        )
        client.write_text(
            client.read_text(encoding="utf-8").replace(
                'env_reference_names([provider["token"]])', "[]"
            ),
            encoding="utf-8",
        )
        self.git("add", "elixir/lib/symphony_elixir/github/client.ex")
        self.git("commit", "-m", "remove source-only scrub")
        payload = json.loads(self.symphony_lock.read_text(encoding="utf-8"))
        payload["commit"] = self.git("rev-parse", "HEAD").stdout.strip()
        payload["required_ancestor_commits"] = [self.commit]
        self.symphony_lock.write_text(json.dumps(payload), encoding="utf-8")

        result = self.preflight().check_symphony_source()

        self.assertFalse(result.ok)
        self.assertTrue(
            any("configured GitHub token environment" in failure for failure in result.failures)
        )

    def test_rejects_source_checked_out_at_a_different_commit(self) -> None:
        (self.symphony / "README.md").write_text("changed\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-m", "second")

        result = self.preflight().check()

        self.assertFalse(result.ok)
        self.assertTrue(
            any("does not match locked commit" in failure for failure in result.failures)
        )

    def test_rejects_missing_required_ancestor(self) -> None:
        payload = json.loads(self.symphony_lock.read_text(encoding="utf-8"))
        payload["required_ancestor_commits"] = ["f" * 40]
        self.symphony_lock.write_text(json.dumps(payload), encoding="utf-8")

        result = self.preflight().check()

        self.assertFalse(result.ok)
        self.assertTrue(
            any("required ancestor" in failure for failure in result.failures)
        )

    def test_rejects_symphony_without_dynamic_github_token_scrubbing(self) -> None:
        client = (
            self.symphony
            / "elixir"
            / "lib"
            / "symphony_elixir"
            / "github"
            / "client.ex"
        )
        client.write_text(
            client.read_text(encoding="utf-8").replace(
                'env_reference_names([provider["token"]])', "[]"
            ),
            encoding="utf-8",
        )
        self.git("add", "elixir/lib/symphony_elixir/github/client.ex")
        self.git("commit", "-m", "remove dynamic scrub")
        payload = json.loads(self.symphony_lock.read_text(encoding="utf-8"))
        payload["commit"] = self.git("rev-parse", "HEAD").stdout.strip()
        payload["required_ancestor_commits"] = [self.commit]
        self.symphony_lock.write_text(json.dumps(payload), encoding="utf-8")

        result = self.preflight().check()

        self.assertFalse(result.ok)
        self.assertTrue(
            any("configured GitHub token environment" in failure for failure in result.failures)
        )

    def test_rejects_symphony_without_remote_shell_token_scrubbing(self) -> None:
        app_server = (
            self.symphony
            / "elixir"
            / "lib"
            / "symphony_elixir"
            / "codex"
            / "app_server.ex"
        )
        app_server.write_text(
            app_server.read_text(encoding="utf-8").replace(
                "defp remote_launch_command(workspace, dynamic_tool_binding), do: [tracker_secret_unset_command(dynamic_tool_binding)]",
                "defp remote_launch_command(workspace, dynamic_tool_binding), do: [workspace]",
            ),
            encoding="utf-8",
        )
        self.git("add", "elixir/lib/symphony_elixir/codex/app_server.ex")
        self.git("commit", "-m", "remove remote scrub")
        payload = json.loads(self.symphony_lock.read_text(encoding="utf-8"))
        payload["commit"] = self.git("rev-parse", "HEAD").stdout.strip()
        payload["required_ancestor_commits"] = [self.commit]
        self.symphony_lock.write_text(json.dumps(payload), encoding="utf-8")

        result = self.preflight().check()

        self.assertFalse(result.ok)
        self.assertTrue(
            any("every boundary" in failure for failure in result.failures)
        )

    def test_accepts_a_different_stable_codex_version(self) -> None:
        result = self.preflight(codex_version="codex-cli 0.147.0").check()

        self.assertTrue(result.ok)
        self.assertIn(
            "Codex CLI reports stable version 0.147.0",
            result.checks,
        )

    def test_rejects_a_prerelease_codex_version(self) -> None:
        result = self.preflight(codex_version="codex-cli 0.148.0-beta.1").check()

        self.assertFalse(result.ok)
        self.assertIn(
            "Codex CLI version output is not a stable release: codex-cli 0.148.0-beta.1",
            result.failures,
        )

    def test_rejects_host_without_mise_or_native_elixir_tools(self) -> None:
        result = self.preflight(available_tools={"git", "codex"}).check()

        self.assertFalse(result.ok)
        self.assertIn(
            "Elixir runtime is unavailable; install mise or both elixir and mix",
            result.failures,
        )

    def test_accepts_native_elixir_and_mix_without_mise(self) -> None:
        result = self.preflight(available_tools={"elixir", "mix"}).check()

        self.assertTrue(result.ok)
        self.assertIn("Native elixir and mix runtimes are available", result.checks)


if __name__ == "__main__":
    unittest.main()
