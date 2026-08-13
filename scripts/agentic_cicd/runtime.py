from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence


CommandOutput = Callable[[tuple[str, ...]], str]
ToolAvailable = Callable[[str], bool]


@dataclass(frozen=True)
class PreflightResult:
    checks: tuple[str, ...]
    failures: tuple[str, ...]

    @property
    def ok(self) -> bool:
        return not self.failures


class RuntimePreflight:
    """Validate pinned local runtimes without starting Symphony or a model turn."""

    def __init__(
        self,
        *,
        symphony_source: Path,
        symphony_lock: Path,
        codex_lock: Path,
        command_output: CommandOutput | None = None,
        tool_available: ToolAvailable | None = None,
    ):
        self.symphony_source = symphony_source.expanduser().resolve()
        self.symphony_lock = symphony_lock.resolve()
        self.codex_lock = codex_lock.resolve()
        self.command_output = command_output or self._command_output
        self.tool_available = tool_available or (lambda tool: shutil.which(tool) is not None)

    def check(self) -> PreflightResult:
        checks: list[str] = []
        failures: list[str] = []

        symphony = self._load_json(self.symphony_lock, "Symphony lock", failures)
        codex = self._load_json(self.codex_lock, "Codex lock", failures)
        if symphony is not None:
            self._check_symphony(symphony, checks, failures)
        if codex is not None:
            self._check_codex(codex, checks, failures)
        self._check_elixir_runtime(checks, failures)

        return PreflightResult(tuple(checks), tuple(failures))

    @staticmethod
    def _load_json(path: Path, label: str, failures: list[str]) -> dict | None:
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            failures.append(f"{label} cannot be read: {error}")
            return None
        if not isinstance(payload, dict):
            failures.append(f"{label} must contain a JSON object")
            return None
        return payload

    def _check_symphony(
        self, lock: dict, checks: list[str], failures: list[str]
    ) -> None:
        expected = lock.get("commit")
        ancestors = lock.get("required_ancestor_commits")
        implementation = lock.get("implementation")
        if not isinstance(expected, str) or not self._full_sha(expected):
            failures.append("Symphony lock contains an invalid commit")
            return
        if not isinstance(ancestors, list) or not all(
            isinstance(value, str) and self._full_sha(value) for value in ancestors
        ):
            failures.append("Symphony lock contains invalid required ancestors")
            return
        if implementation != "elixir":
            failures.append(f"Unsupported Symphony implementation: {implementation}")
            return
        if not (self.symphony_source / ".git").exists():
            failures.append(
                f"Symphony source is not a Git checkout: {self.symphony_source}"
            )
            return
        if not (self.symphony_source / "elixir" / "mix.exs").is_file():
            failures.append("Symphony Elixir implementation is missing elixir/mix.exs")
            return

        head = self._git_output(("rev-parse", "HEAD"), failures)
        if head is None:
            return
        if head != expected:
            failures.append(
                f"Symphony source HEAD {head} does not match locked commit {expected}"
            )
        else:
            checks.append(f"Symphony source HEAD matches {expected}")

        for ancestor in ancestors:
            result = subprocess.run(
                ["git", "merge-base", "--is-ancestor", ancestor, expected],
                cwd=self.symphony_source,
                check=False,
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                checks.append(f"Symphony contains required ancestor {ancestor}")
            else:
                failures.append(
                    f"Symphony required ancestor {ancestor} is absent from {expected}"
                )

        dirty = self._git_output(
            ("status", "--porcelain", "--untracked-files=no"), failures
        )
        if dirty is not None:
            if dirty:
                failures.append("Symphony source contains tracked working-tree changes")
            else:
                checks.append("Symphony tracked source is clean")

    def _check_codex(
        self, lock: dict, checks: list[str], failures: list[str]
    ) -> None:
        expected = lock.get("codex_cli_version")
        if not isinstance(expected, str) or not expected.strip():
            failures.append("Codex lock contains an invalid CLI version")
            return
        try:
            actual = self.command_output(("codex", "--version")).strip()
        except (OSError, subprocess.SubprocessError) as error:
            failures.append(f"Codex CLI cannot be executed: {error}")
            return
        expected_output = f"codex-cli {expected}"
        if actual == expected_output:
            checks.append(f"Codex CLI matches {expected}")
        else:
            failures.append(
                f"Codex CLI version is {actual or '<empty>'}; expected {expected_output}"
            )

    def _check_elixir_runtime(
        self, checks: list[str], failures: list[str]
    ) -> None:
        if self.tool_available("mise"):
            checks.append("Elixir runtime is available through mise")
        elif self.tool_available("elixir") and self.tool_available("mix"):
            checks.append("Native elixir and mix runtimes are available")
        else:
            failures.append(
                "Elixir runtime is unavailable; install mise or both elixir and mix"
            )

    def _git_output(
        self, arguments: Sequence[str], failures: list[str]
    ) -> str | None:
        try:
            result = subprocess.run(
                ["git", *arguments],
                cwd=self.symphony_source,
                check=True,
                capture_output=True,
                text=True,
            )
        except (OSError, subprocess.SubprocessError) as error:
            failures.append(f"Symphony Git check failed: {error}")
            return None
        return result.stdout.strip()

    @staticmethod
    def _command_output(command: tuple[str, ...]) -> str:
        return subprocess.run(
            list(command), check=True, capture_output=True, text=True
        ).stdout

    @staticmethod
    def _full_sha(value: str) -> bool:
        return len(value) == 40 and all(
            character in "0123456789abcdef" for character in value
        )
