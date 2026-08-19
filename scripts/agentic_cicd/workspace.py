from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path

from .process_environment import trusted_process_environment


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


@dataclass(frozen=True)
class BaseSyncResult:
    status: str
    previous_base_sha: str
    base_sha: str
    previous_head_sha: str
    head_sha: str

    def __post_init__(self) -> None:
        if self.status not in {"UNCHANGED", "UPDATED", "CONFLICT"}:
            raise ValueError("base sync status is invalid")
        for field_name in (
            "previous_base_sha",
            "base_sha",
            "previous_head_sha",
            "head_sha",
        ):
            if not re.fullmatch(r"[0-9a-f]{40}", getattr(self, field_name)):
                raise ValueError(f"{field_name} must be a lowercase full Git SHA")
        if self.status == "UNCHANGED" and self.base_sha != self.previous_base_sha:
            raise ValueError("unchanged base sync cannot change the base SHA")
        if self.status == "UNCHANGED" and self.head_sha != self.previous_head_sha:
            raise ValueError("unchanged base sync cannot change the head SHA")
        if self.status != "UNCHANGED" and self.base_sha == self.previous_base_sha:
            raise ValueError("base sync result does not contain an advanced base")
        if self.status == "CONFLICT" and self.head_sha != self.previous_head_sha:
            raise ValueError("conflicting base sync cannot change the head SHA")

    def to_json(self) -> dict[str, str]:
        return {
            "status": self.status,
            "previous_base_sha": self.previous_base_sha,
            "base_sha": self.base_sha,
            "previous_head_sha": self.previous_head_sha,
            "head_sha": self.head_sha,
        }


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
        self._write_metadata(metadata_path, workspace)
        return workspace

    def sync_base(
        self, workspace: Workspace, *, expected_head_sha: str
    ) -> BaseSyncResult:
        """Merge an advanced origin/develop without rewriting candidate history."""
        if not re.fullmatch(r"[0-9a-f]{40}", expected_head_sha):
            raise ValueError("expected_head_sha must be a lowercase full Git SHA")
        metadata_path = workspace.path / METADATA_DIRECTORY / METADATA_FILE
        recovered = self._recover(
            metadata_path, workspace.path, workspace.issue_identifier
        )
        if recovered != workspace:
            return self._recover_completed_base_sync(
                workspace, recovered, expected_head_sha=expected_head_sha
            )
        actual_head = self._git(workspace.path, "rev-parse", "HEAD").stdout.strip()
        if actual_head != expected_head_sha:
            raise RuntimeError("workspace HEAD differs from trusted task head")
        self._require_clean_runtime_workspace(workspace.path)
        if not self._is_ancestor(workspace.path, workspace.base_sha, actual_head):
            raise RuntimeError("trusted base is not an ancestor of the task head")

        advanced_base = self._fetch_origin_develop()
        if advanced_base == workspace.base_sha:
            return BaseSyncResult(
                "UNCHANGED",
                workspace.base_sha,
                advanced_base,
                actual_head,
                actual_head,
            )
        if not self._is_ancestor(
            workspace.path, workspace.base_sha, advanced_base
        ):
            raise RuntimeError("origin/develop did not advance from the trusted base")

        if not self._is_ancestor(workspace.path, advanced_base, actual_head):
            merge = subprocess.run(
                [
                    "git",
                    "-c",
                    "core.hooksPath=/dev/null",
                    "-c",
                    "commit.gpgSign=false",
                    "-c",
                    "user.name=j-store Agentic CI/CD",
                    "-c",
                    "user.email=agentic-cicd@localhost.invalid",
                    "merge",
                    "--no-edit",
                    "--no-stat",
                    advanced_base,
                ],
                cwd=workspace.path,
                check=False,
                capture_output=True,
                text=True,
                env=trusted_process_environment(
                    {"GIT_MERGE_AUTOEDIT": "no", "GIT_TERMINAL_PROMPT": "0"}
                ),
            )
            if merge.returncode != 0:
                conflicts = self._git(
                    workspace.path, "diff", "--name-only", "--diff-filter=U"
                ).stdout.splitlines()
                self._abort_merge(workspace.path)
                self._require_restored_head(workspace.path, actual_head)
                if conflicts:
                    return BaseSyncResult(
                        "CONFLICT",
                        workspace.base_sha,
                        advanced_base,
                        actual_head,
                        actual_head,
                    )
                raise RuntimeError("base merge failed without a merge conflict")

        new_head = self._git(workspace.path, "rev-parse", "HEAD").stdout.strip()
        if not self._is_ancestor(workspace.path, actual_head, new_head):
            raise RuntimeError("base sync rewrote the previous task head")
        if not self._is_ancestor(workspace.path, advanced_base, new_head):
            raise RuntimeError("base sync head does not contain the advanced base")
        updated = Workspace(
            workspace.issue_identifier,
            advanced_base,
            workspace.branch,
            workspace.path,
        )
        result = BaseSyncResult(
            "UPDATED",
            workspace.base_sha,
            advanced_base,
            actual_head,
            new_head,
        )
        self._write_metadata(metadata_path, updated, base_sync=result)
        return result

    def _recover_completed_base_sync(
        self,
        previous: Workspace,
        recovered: Workspace,
        *,
        expected_head_sha: str,
    ) -> BaseSyncResult:
        if (
            recovered.issue_identifier != previous.issue_identifier
            or recovered.branch != previous.branch
            or recovered.path != previous.path
        ):
            raise RuntimeError("base sync request differs from trusted workspace metadata")
        actual_head = self._git(previous.path, "rev-parse", "HEAD").stdout.strip()
        self._require_clean_runtime_workspace(previous.path)
        metadata_payload = json.loads(
            (previous.path / METADATA_DIRECTORY / METADATA_FILE).read_text(
                encoding="utf-8"
            )
        )
        try:
            recorded = BaseSyncResult(**metadata_payload["base_sync"])
        except (KeyError, TypeError, ValueError):
            raise RuntimeError(
                "base sync request differs from trusted workspace metadata"
            ) from None
        if (
            recovered.base_sha == previous.base_sha
            or recorded
            != BaseSyncResult(
                "UPDATED",
                previous.base_sha,
                recovered.base_sha,
                expected_head_sha,
                actual_head,
            )
            or not self._is_ancestor(
                previous.path, previous.base_sha, recovered.base_sha
            )
            or not self._is_ancestor(previous.path, expected_head_sha, actual_head)
            or not self._is_ancestor(previous.path, recovered.base_sha, actual_head)
        ):
            raise RuntimeError("base sync request differs from trusted workspace metadata")
        return BaseSyncResult(
            "UPDATED",
            previous.base_sha,
            recovered.base_sha,
            expected_head_sha,
            actual_head,
        )

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
            env=trusted_process_environment(),
        )
        return result.returncode == 0

    def _require_clean_runtime_workspace(self, path: Path) -> None:
        status_lines = [
            line
            for line in self._git(
                path, "status", "--porcelain=v1", "--untracked-files=all"
            ).stdout.splitlines()
            if line.strip()
        ]
        allowed = f"?? {METADATA_DIRECTORY}/{METADATA_FILE}"
        if any(line != allowed for line in status_lines):
            raise RuntimeError("workspace must be clean before base synchronization")

    def _abort_merge(self, path: Path) -> None:
        merge_head = subprocess.run(
            ["git", "rev-parse", "-q", "--verify", "MERGE_HEAD"],
            cwd=path,
            check=False,
            capture_output=True,
            text=True,
            env=trusted_process_environment(),
        )
        if merge_head.returncode == 0:
            self._git(path, "merge", "--abort")

    def _require_restored_head(self, path: Path, expected_head: str) -> None:
        actual_head = self._git(path, "rev-parse", "HEAD").stdout.strip()
        if actual_head != expected_head:
            raise RuntimeError("failed base sync did not restore the previous head")
        self._require_clean_runtime_workspace(path)

    @staticmethod
    def _is_ancestor(path: Path, ancestor: str, descendant: str) -> bool:
        result = subprocess.run(
            ["git", "merge-base", "--is-ancestor", ancestor, descendant],
            cwd=path,
            check=False,
            capture_output=True,
            text=True,
            env=trusted_process_environment(),
        )
        if result.returncode not in {0, 1}:
            raise RuntimeError("git merge-base failed during base synchronization")
        return result.returncode == 0

    @staticmethod
    def _write_metadata(
        path: Path,
        workspace: Workspace,
        *,
        base_sync: BaseSyncResult | None = None,
    ) -> None:
        document: dict[str, object] = {
            "issue_identifier": workspace.issue_identifier,
            "base_sha": workspace.base_sha,
            "branch": workspace.branch,
        }
        if base_sync is not None:
            document["base_sync"] = base_sync.to_json()
        payload = (
            json.dumps(
                document,
                indent=2,
                sort_keys=True,
            )
            + "\n"
        )
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
        )
        temporary_path = Path(temporary_name)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_path, path)
        finally:
            temporary_path.unlink(missing_ok=True)

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
            env=trusted_process_environment(),
        )
