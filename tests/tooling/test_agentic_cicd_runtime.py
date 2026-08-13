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
        self.git("add", "README.md", "elixir/mix.exs", "elixir/mise.toml")
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
                }
            ),
            encoding="utf-8",
        )
        self.codex_lock = self.root / "codex.lock.json"
        self.codex_lock.write_text(
            json.dumps({"codex_cli_version": "0.146.0"}), encoding="utf-8"
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
        self.assertIn("Codex CLI matches 0.146.0", result.checks)
        self.assertIn("Elixir runtime is available through mise", result.checks)

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

    def test_rejects_codex_version_drift(self) -> None:
        result = self.preflight(codex_version="codex-cli 0.147.0").check()

        self.assertFalse(result.ok)
        self.assertIn(
            "Codex CLI version is codex-cli 0.147.0; expected codex-cli 0.146.0",
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
