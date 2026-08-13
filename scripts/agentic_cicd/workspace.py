from __future__ import annotations

import json
import re
import subprocess
import unicodedata
from dataclasses import dataclass
from pathlib import Path


ISSUE_IDENTIFIER = re.compile(r"^GH-([1-9][0-9]*)$")
SAFE_SLUG = re.compile(r"[^a-z0-9._-]+")
METADATA_DIRECTORY = ".agentic-cicd"
METADATA_FILE = "workspace.json"


class PathSafetyError(RuntimeError):
    """Raised before a workspace path can escape its configured root."""


@dataclass(frozen=True)
class Workspace:
    issue_identifier: str
    base_sha: str
    branch: str
    path: Path


class WorkspaceManager:
    def __init__(self, repository: Path, workspace_root: Path):
        self.repository = repository.resolve()
        self.workspace_root = workspace_root.resolve()
        if self.repository == self.workspace_root:
            raise PathSafetyError("workspace root must differ from the source repository")

    def create_or_recover(self, issue_identifier: str, title: str) -> Workspace:
        issue_number = self._issue_number(issue_identifier)
        workspace_path = (self.workspace_root / f"gh-{issue_number}").resolve()
        self._assert_beneath_root(workspace_path)
        metadata_path = workspace_path / METADATA_DIRECTORY / METADATA_FILE
        if metadata_path.is_file():
            return self._recover(metadata_path, workspace_path, issue_identifier)
        if workspace_path.exists():
            raise PathSafetyError(
                f"workspace exists without trusted metadata: {workspace_path}"
            )

        base_sha = self._fetch_origin_develop()
        branch = f"codex/gh-{issue_number}-{self._slug(title)}"
        if self._branch_exists(branch):
            raise RuntimeError(f"branch exists without recoverable workspace: {branch}")

        self.workspace_root.mkdir(parents=True, exist_ok=True)
        self._git(
            self.repository,
            "worktree",
            "add",
            "-b",
            branch,
            workspace_path.as_posix(),
            base_sha,
        )
        workspace = Workspace(issue_identifier, base_sha, branch, workspace_path)
        metadata_path.parent.mkdir(parents=True, exist_ok=True)
        metadata_path.write_text(
            json.dumps(
                {
                    "issue_identifier": workspace.issue_identifier,
                    "base_sha": workspace.base_sha,
                    "branch": workspace.branch,
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        return workspace

    def remove(self, workspace: Workspace, issue_identifier: str) -> None:
        """Remove one verified worktree while retaining its local branch for audit."""
        issue_number = self._issue_number(issue_identifier)
        expected_path = (self.workspace_root / f"gh-{issue_number}").resolve()
        actual_path = workspace.path.resolve()
        self._assert_beneath_root(actual_path)
        if actual_path != expected_path:
            raise PathSafetyError(
                f"workspace path does not match issue {issue_identifier}: {actual_path}"
            )
        metadata_path = actual_path / METADATA_DIRECTORY / METADATA_FILE
        if not metadata_path.is_file():
            raise PathSafetyError("workspace cannot be removed without trusted metadata")
        recovered = self._recover(metadata_path, actual_path, issue_identifier)
        if recovered != workspace:
            raise RuntimeError("workspace removal request differs from trusted metadata")

        status_lines = [
            line
            for line in self._git(
                actual_path,
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ).stdout.splitlines()
            if line.strip()
        ]
        allowed_runtime_state = f"?? {METADATA_DIRECTORY}/{METADATA_FILE}"
        unexpected_changes = [
            line for line in status_lines if line != allowed_runtime_state
        ]
        if unexpected_changes:
            raise RuntimeError(
                "workspace has uncommitted changes and will not be removed: "
                + ", ".join(unexpected_changes)
            )

        self._git(
            self.repository,
            "worktree",
            "remove",
            "--force",
            actual_path.as_posix(),
        )

    def _recover(
        self,
        metadata_path: Path,
        workspace_path: Path,
        expected_issue_identifier: str,
    ) -> Workspace:
        payload = json.loads(metadata_path.read_text(encoding="utf-8"))
        workspace = Workspace(
            issue_identifier=str(payload["issue_identifier"]),
            base_sha=str(payload["base_sha"]),
            branch=str(payload["branch"]),
            path=workspace_path,
        )
        if workspace.issue_identifier != expected_issue_identifier:
            raise RuntimeError(
                "workspace issue mismatch: "
                f"expected {expected_issue_identifier}, got {workspace.issue_identifier}"
            )
        if not re.fullmatch(r"[0-9a-f]{40}", workspace.base_sha):
            raise RuntimeError("workspace metadata contains an invalid base SHA")
        expected_branch_prefix = (
            f"codex/gh-{self._issue_number(expected_issue_identifier)}-"
        )
        if not workspace.branch.startswith(expected_branch_prefix):
            raise RuntimeError("workspace metadata contains an invalid branch")
        current_branch = self._git(
            workspace_path, "branch", "--show-current"
        ).stdout.strip()
        if current_branch != workspace.branch:
            raise RuntimeError(
                f"workspace branch mismatch: expected {workspace.branch}, got {current_branch}"
            )
        return workspace

    def _fetch_origin_develop(self) -> str:
        self._git(self.repository, "fetch", "origin", "develop")
        sha = self._git(
            self.repository, "rev-parse", "refs/remotes/origin/develop"
        ).stdout.strip()
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise RuntimeError("origin/develop did not resolve to a full commit SHA")
        return sha

    def _branch_exists(self, branch: str) -> bool:
        result = subprocess.run(
            ["git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}"],
            cwd=self.repository,
            check=False,
        )
        return result.returncode == 0

    @staticmethod
    def _issue_number(issue_identifier: str) -> str:
        match = ISSUE_IDENTIFIER.fullmatch(issue_identifier)
        if match is None:
            raise PathSafetyError(
                "issue identifier must use canonical GH-<positive-number> form"
            )
        return match.group(1)

    @staticmethod
    def _slug(title: str) -> str:
        ascii_title = (
            unicodedata.normalize("NFKD", title)
            .encode("ascii", "ignore")
            .decode("ascii")
            .lower()
        )
        slug = SAFE_SLUG.sub("-", ascii_title).strip("-._")
        return slug[:48].rstrip("-._") or "task"

    def _assert_beneath_root(self, path: Path) -> None:
        try:
            path.relative_to(self.workspace_root)
        except ValueError as error:
            raise PathSafetyError(
                f"workspace path escapes configured root: {path}"
            ) from error

    @staticmethod
    def _git(cwd: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
        )
