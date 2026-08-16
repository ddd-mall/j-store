from __future__ import annotations

import hashlib
import io
import json
import os
import re
import shutil
import stat
import subprocess
import tarfile
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any


FULL_SHA = re.compile(r"[0-9a-f]{40}\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
SNAPSHOT_POLICY = {
    "archive": "canonical-ustar-v1",
    "content": "raw-worktree-bytes-in-tree-and-archive",
    "excluded_roots": [".git", ".agentic-cicd"],
    "git_index": "temporary",
    "special_files": "reject",
    "submodules": "reject",
    "symlinks": "relative-within-workspace",
    "version": 1,
}
SNAPSHOT_POLICY_SHA256 = hashlib.sha256(
    json.dumps(SNAPSHOT_POLICY, separators=(",", ":"), sort_keys=True).encode()
).hexdigest()


class CandidateSnapshotError(RuntimeError):
    """Raised when a workspace cannot be frozen or safely materialized."""


def _sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def _is_within(path: Path, root: Path) -> bool:
    try:
        return os.path.commonpath((str(path), str(root))) == str(root)
    except ValueError:
        return False


def _atomic_write(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


@dataclass(frozen=True)
class CandidateRevision:
    base_sha: str
    tree_sha: str
    artifact_sha256: str
    snapshot_policy_sha256: str
    candidate_revision: str

    def __post_init__(self) -> None:
        for field_name in ("base_sha", "tree_sha"):
            if not FULL_SHA.fullmatch(getattr(self, field_name)):
                raise ValueError(f"{field_name} must be a lowercase full Git SHA")
        for field_name in (
            "artifact_sha256",
            "snapshot_policy_sha256",
            "candidate_revision",
        ):
            if not SHA256.fullmatch(getattr(self, field_name)):
                raise ValueError(f"{field_name} must be a lowercase SHA-256")
        expected_revision = self.calculate_revision(
            self.base_sha,
            self.tree_sha,
            self.artifact_sha256,
            self.snapshot_policy_sha256,
        )
        if self.candidate_revision != expected_revision:
            raise ValueError("candidate_revision does not bind the manifest fields")

    @staticmethod
    def calculate_revision(
        base_sha: str,
        tree_sha: str,
        artifact_sha256: str,
        snapshot_policy_sha256: str,
    ) -> str:
        identity = {
            "artifact_sha256": artifact_sha256,
            "base_sha": base_sha,
            "snapshot_policy_sha256": snapshot_policy_sha256,
            "tree_sha": tree_sha,
        }
        return _sha256(
            json.dumps(identity, separators=(",", ":"), sort_keys=True).encode()
        )

    def to_json(self) -> dict[str, str]:
        return {
            "artifact_sha256": self.artifact_sha256,
            "base_sha": self.base_sha,
            "candidate_revision": self.candidate_revision,
            "snapshot_policy_sha256": self.snapshot_policy_sha256,
            "tree_sha": self.tree_sha,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "CandidateRevision":
        required = {
            "artifact_sha256",
            "base_sha",
            "candidate_revision",
            "snapshot_policy_sha256",
            "tree_sha",
        }
        if set(payload) != required or not all(
            isinstance(payload[name], str) for name in required
        ):
            raise ValueError("candidate revision fields do not match the contract")
        return cls(**{name: payload[name] for name in required})

    def archive_path(self, artifact_root: Path) -> Path:
        return artifact_root.resolve() / "archives" / f"{self.artifact_sha256}.tar"

    def manifest_path(self, artifact_root: Path) -> Path:
        return (
            artifact_root.resolve()
            / "manifests"
            / f"{self.candidate_revision}.json"
        )


class CandidateSnapshotter:
    def __init__(self, workspace: Path, artifact_root: Path):
        self.workspace = workspace.resolve()
        self.artifact_root = artifact_root.resolve()
        if not self.workspace.is_dir():
            raise CandidateSnapshotError("candidate workspace must be a directory")
        if _is_within(self.artifact_root, self.workspace):
            raise CandidateSnapshotError(
                "artifact root must be outside the candidate workspace"
            )
        git_top_level = Path(
            self._git("rev-parse", "--show-toplevel").decode().strip()
        ).resolve()
        if git_top_level != self.workspace:
            raise CandidateSnapshotError("candidate workspace must be the Git top level")

    def freeze(self, base_sha: str) -> CandidateRevision:
        if not FULL_SHA.fullmatch(base_sha):
            raise CandidateSnapshotError("base SHA must be a lowercase full Git SHA")
        actual_head = self._git("rev-parse", "HEAD").decode().strip()
        if actual_head != base_sha:
            raise CandidateSnapshotError("workspace HEAD does not match the trusted base")
        workspace_entries = self._validate_workspace_entries()
        base_entries = self._tree_entries("HEAD")
        ignored_untracked = self._ignored_untracked_paths(
            set(workspace_entries) - set(base_entries)
        )
        candidate_entries = {
            name: path
            for name, path in workspace_entries.items()
            if name in base_entries or name not in ignored_untracked
        }
        tree_sha = self._write_raw_tree(base_entries, candidate_entries)
        archive = self._canonical_archive(tree_sha, candidate_entries)
        artifact_sha256 = _sha256(archive)
        candidate_revision = CandidateRevision.calculate_revision(
            base_sha,
            tree_sha,
            artifact_sha256,
            SNAPSHOT_POLICY_SHA256,
        )
        revision = CandidateRevision(
            base_sha=base_sha,
            tree_sha=tree_sha,
            artifact_sha256=artifact_sha256,
            snapshot_policy_sha256=SNAPSHOT_POLICY_SHA256,
            candidate_revision=candidate_revision,
        )
        self._store_revision(revision, archive)
        return revision

    def materialize(self, revision: CandidateRevision, destination: Path) -> None:
        payload = self._load_archive(revision)

        if destination.is_symlink():
            raise CandidateSnapshotError("materialization destination must not be a symlink")
        if destination.exists():
            raise CandidateSnapshotError("materialization destination must not exist")
        parent = destination.parent.resolve()
        if not parent.is_dir():
            raise CandidateSnapshotError("materialization parent must be a directory")
        temporary = Path(tempfile.mkdtemp(prefix=".candidate-materialize-", dir=parent))
        try:
            self._extract_archive(payload, temporary)
            os.replace(temporary, parent / destination.name)
        finally:
            if temporary.exists():
                shutil.rmtree(temporary)

    def verify_materialized(
        self, revision: CandidateRevision, materialized_root: Path
    ) -> None:
        """Proves that a recovered reviewer workspace is the exact frozen archive."""
        if materialized_root.is_symlink() or not materialized_root.is_dir():
            raise CandidateSnapshotError("materialized candidate root is unsafe")
        payload = self._load_archive(revision)
        expected: dict[str, tarfile.TarInfo] = {}
        expected_directories: set[str] = set()
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
            members = archive.getmembers()
            seen: set[str] = set()
            for member in members:
                self._validate_archive_member(member, materialized_root, seen)
                expected[member.name] = member
                relative = self._safe_archive_path(member.name)
                for depth in range(1, len(relative.parts)):
                    expected_directories.add("/".join(relative.parts[:depth]))

            observed: set[str] = set()

            def verify_directory(directory: Path, relative_parent: PurePosixPath) -> None:
                try:
                    entries = list(os.scandir(directory))
                except OSError as error:
                    raise CandidateSnapshotError(
                        "materialized candidate cannot be enumerated"
                    ) from error
                for entry in entries:
                    relative = relative_parent / entry.name
                    name = relative.as_posix()
                    metadata = entry.stat(follow_symlinks=False)
                    if stat.S_ISDIR(metadata.st_mode):
                        if name not in expected_directories:
                            raise CandidateSnapshotError(
                                "materialized candidate has an extra directory"
                            )
                        verify_directory(Path(entry.path), relative)
                        continue
                    member = expected.get(name)
                    if member is None:
                        raise CandidateSnapshotError(
                            "materialized candidate has an extra entry"
                        )
                    observed.add(name)
                    if member.issym():
                        if not stat.S_ISLNK(metadata.st_mode) or os.readlink(entry.path) != member.linkname:
                            raise CandidateSnapshotError(
                                "materialized candidate symlink differs"
                            )
                        continue
                    if not member.isreg() or not stat.S_ISREG(metadata.st_mode):
                        raise CandidateSnapshotError(
                            "materialized candidate entry type differs"
                        )
                    archive_mode = member.mode & 0o777
                    read_only_mode = 0o555 if archive_mode & 0o111 else 0o444
                    if stat.S_IMODE(metadata.st_mode) != read_only_mode:
                        raise CandidateSnapshotError(
                            "materialized candidate file mode differs"
                        )
                    descriptor = os.open(
                        entry.path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
                    )
                    with os.fdopen(descriptor, "rb") as stream:
                        actual = stream.read()
                    source = archive.extractfile(member)
                    if source is None or actual != source.read():
                        raise CandidateSnapshotError(
                            "materialized candidate file content differs"
                        )

            verify_directory(materialized_root, PurePosixPath())
            if observed != set(expected):
                raise CandidateSnapshotError("materialized candidate is incomplete")

    def _load_archive(self, revision: CandidateRevision) -> bytes:
        archive_path = revision.archive_path(self.artifact_root)
        try:
            descriptor = os.open(
                archive_path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
            )
        except FileNotFoundError as error:
            raise CandidateSnapshotError("candidate artifact is missing") from error
        except OSError as error:
            raise CandidateSnapshotError("candidate artifact is unsafe") from error
        with os.fdopen(descriptor, "rb") as stream:
            payload = stream.read()
        if _sha256(payload) != revision.artifact_sha256:
            raise CandidateSnapshotError("candidate artifact digest does not match")
        return payload

    def _extract_archive(self, payload: bytes, target: Path) -> None:
        seen: set[str] = set()
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
            members = archive.getmembers()
            for member in members:
                self._validate_archive_member(member, target, seen)
            for member in members:
                relative = self._safe_archive_path(member.name)
                output = target.joinpath(*relative.parts)
                output.parent.mkdir(parents=True, exist_ok=True)
                if member.isreg():
                    source = archive.extractfile(member)
                    if source is None:
                        raise CandidateSnapshotError("candidate archive file is unreadable")
                    descriptor = os.open(
                        output,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL
                        | getattr(os, "O_NOFOLLOW", 0),
                        member.mode & 0o777,
                    )
                    with os.fdopen(descriptor, "wb") as stream:
                        stream.write(source.read())
                    output.chmod(member.mode & 0o777)
                else:
                    output.symlink_to(member.linkname)

    def _validate_archive_member(
        self, member: tarfile.TarInfo, target: Path, seen: set[str]
    ) -> None:
        relative = self._safe_archive_path(member.name)
        if member.name in seen:
            raise CandidateSnapshotError("candidate archive has duplicate paths")
        seen.add(member.name)
        output = target.joinpath(*relative.parts)
        if not _is_within(output.resolve(strict=False), target):
            raise CandidateSnapshotError("candidate archive path traversal")
        if member.isreg():
            return
        if not member.issym():
            raise CandidateSnapshotError("candidate archive has unsupported entries")
        link_target = Path(member.linkname)
        if link_target.is_absolute() or not _is_within(
            (output.parent / link_target).resolve(strict=False), target
        ):
            raise CandidateSnapshotError("candidate archive symlink escapes root")

    def _validate_workspace_entries(self) -> dict[str, Path]:
        entries: dict[str, Path] = {}
        for path in self.workspace.rglob("*"):
            relative = path.relative_to(self.workspace)
            if relative == Path(".git"):
                continue
            if relative.parts[0] == ".git":
                continue
            if relative.parts[0] == ".agentic-cicd":
                if relative in {
                    Path(".agentic-cicd"),
                    Path(".agentic-cicd/workspace.json"),
                }:
                    continue
                raise CandidateSnapshotError("unexpected runtime metadata is not allowed")
            if ".git" in relative.parts:
                raise CandidateSnapshotError("nested repository metadata is not allowed")
            metadata = path.lstat()
            if stat.S_ISLNK(metadata.st_mode):
                link_target = Path(os.readlink(path))
                if link_target.is_absolute() or not _is_within(
                    (path.parent / link_target).resolve(strict=False), self.workspace
                ):
                    raise CandidateSnapshotError("symlink points outside workspace")
            elif not (stat.S_ISREG(metadata.st_mode) or stat.S_ISDIR(metadata.st_mode)):
                raise CandidateSnapshotError("special file is not allowed")
            if stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
                name = relative.as_posix()
                try:
                    name.encode("utf-8")
                except UnicodeEncodeError as error:
                    raise CandidateSnapshotError(
                        "candidate paths must use UTF-8"
                    ) from error
                self._safe_archive_path(name)
                entries[name] = path
        return entries

    def _tree_entries(self, revision: str) -> dict[str, tuple[bytes, bytes]]:
        entries: dict[str, tuple[bytes, bytes]] = {}
        listing = self._git("ls-tree", "-r", "-z", revision)
        for record in listing.split(b"\0"):
            if not record:
                continue
            metadata, raw_name = record.split(b"\t", 1)
            mode, object_type, object_sha = metadata.split(b" ", 2)
            if object_type == b"commit" or mode == b"160000":
                raise CandidateSnapshotError("submodule entries are not allowed")
            if object_type != b"blob":
                raise CandidateSnapshotError("candidate tree contains unsupported objects")
            try:
                name = raw_name.decode("utf-8")
            except UnicodeDecodeError as error:
                raise CandidateSnapshotError("candidate paths must use UTF-8") from error
            self._safe_archive_path(name)
            entries[name] = (mode, object_sha)
        return entries

    def _ignored_untracked_paths(self, paths: set[str]) -> set[str]:
        if not paths:
            return set()
        result = subprocess.run(
            [
                "git",
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.hooksPath=/dev/null",
                "check-ignore",
                "--no-index",
                "-z",
                "--stdin",
            ],
            cwd=self.workspace,
            input=b"\0".join(name.encode("utf-8") for name in sorted(paths)) + b"\0",
            check=False,
            capture_output=True,
        )
        if result.returncode not in {0, 1}:
            message = result.stderr.decode("utf-8", errors="replace").strip()
            raise CandidateSnapshotError(f"git check-ignore failed: {message}")
        return {
            raw_name.decode("utf-8")
            for raw_name in result.stdout.split(b"\0")
            if raw_name
        }

    def _write_raw_tree(
        self,
        base_entries: dict[str, tuple[bytes, bytes]],
        candidate_entries: dict[str, Path],
    ) -> str:
        with tempfile.TemporaryDirectory(prefix="candidate-index-") as directory:
            environment = dict(os.environ)
            environment["GIT_INDEX_FILE"] = str(Path(directory) / "index")
            environment["GIT_OPTIONAL_LOCKS"] = "0"
            self._git("read-tree", "HEAD", environment=environment)
            for name in sorted(set(base_entries) - set(candidate_entries)):
                self._git(
                    "update-index",
                    "--force-remove",
                    "--",
                    name,
                    environment=environment,
                )
            for name, path in sorted(candidate_entries.items()):
                metadata = path.lstat()
                if stat.S_ISLNK(metadata.st_mode):
                    mode = "120000"
                    content = os.readlink(path).encode("utf-8")
                elif stat.S_ISREG(metadata.st_mode):
                    mode = "100755" if metadata.st_mode & 0o111 else "100644"
                    content = path.read_bytes()
                else:
                    raise CandidateSnapshotError(
                        "candidate changed while it was being frozen"
                    )
                object_sha = self._hash_raw_object(content, write=True)
                self._git(
                    "update-index",
                    "--add",
                    "--cacheinfo",
                    mode,
                    object_sha,
                    name,
                    environment=environment,
                )
            return self._git("write-tree", environment=environment).decode().strip()

    def _canonical_archive(
        self, tree_sha: str, candidate_entries: dict[str, Path]
    ) -> bytes:
        tree_entries = self._tree_entries(tree_sha)
        if set(tree_entries) != set(candidate_entries):
            raise CandidateSnapshotError("candidate tree and workspace paths differ")
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name, (mode, object_sha) in sorted(tree_entries.items()):
                source_path = candidate_entries[name]
                try:
                    source_metadata = source_path.lstat()
                except FileNotFoundError as error:
                    raise CandidateSnapshotError(
                        "candidate changed while it was being frozen"
                    ) from error
                info = tarfile.TarInfo(name)
                info.uid = 0
                info.gid = 0
                info.uname = ""
                info.gname = ""
                info.mtime = 0
                info.mode = int(mode, 8) & 0o777
                if mode == b"120000":
                    if not stat.S_ISLNK(source_metadata.st_mode):
                        raise CandidateSnapshotError(
                            "candidate changed while it was being frozen"
                        )
                    try:
                        link_target = os.readlink(source_path)
                        content = link_target.encode("utf-8")
                    except UnicodeEncodeError as error:
                        raise CandidateSnapshotError(
                            "symlink targets must use UTF-8"
                        ) from error
                    actual_object_sha = self._hash_raw_object(content, write=False)
                    info.type = tarfile.SYMTYPE
                    info.linkname = link_target
                    info.size = 0
                    archive.addfile(info)
                else:
                    if mode not in {b"100644", b"100755"} or not stat.S_ISREG(
                        source_metadata.st_mode
                    ):
                        raise CandidateSnapshotError(
                            "candidate tree contains unsupported file modes"
                        )
                    content = source_path.read_bytes()
                    actual_object_sha = self._hash_raw_object(content, write=False)
                    info.size = len(content)
                    archive.addfile(info, io.BytesIO(content))
                if actual_object_sha != object_sha.decode():
                    raise CandidateSnapshotError(
                        "candidate changed while it was being frozen"
                    )
        return buffer.getvalue()

    def _store_revision(self, revision: CandidateRevision, archive: bytes) -> None:
        archive_path = revision.archive_path(self.artifact_root)
        if archive_path.exists():
            if _sha256(archive_path.read_bytes()) != revision.artifact_sha256:
                raise CandidateSnapshotError("stored candidate artifact digest differs")
        else:
            _atomic_write(archive_path, archive)
        manifest = json.dumps(
            revision.to_json(), indent=2, sort_keys=True
        ).encode("utf-8") + b"\n"
        manifest_path = revision.manifest_path(self.artifact_root)
        if manifest_path.exists() and manifest_path.read_bytes() != manifest:
            raise CandidateSnapshotError("stored candidate manifest differs")
        if not manifest_path.exists():
            _atomic_write(manifest_path, manifest)

    @staticmethod
    def _safe_archive_path(name: str) -> PurePosixPath:
        relative = PurePosixPath(name)
        if (
            not name
            or "\n" in name
            or "\r" in name
            or relative.is_absolute()
            or ".." in relative.parts
            or "." in relative.parts
            or name.endswith("/")
        ):
            raise CandidateSnapshotError("candidate archive path traversal")
        return relative

    def _git(
        self, *arguments: str, environment: dict[str, str] | None = None
    ) -> bytes:
        result = subprocess.run(
            [
                "git",
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.hooksPath=/dev/null",
                *arguments,
            ],
            cwd=self.workspace,
            env=environment,
            check=False,
            capture_output=True,
        )
        if result.returncode != 0:
            message = result.stderr.decode("utf-8", errors="replace").strip()
            raise CandidateSnapshotError(f"git {' '.join(arguments)} failed: {message}")
        return result.stdout

    def _hash_raw_object(self, content: bytes, *, write: bool) -> str:
        arguments = ["git", "hash-object", "--no-filters"]
        if write:
            arguments.append("-w")
        arguments.append("--stdin")
        result = subprocess.run(
            arguments,
            cwd=self.workspace,
            input=content,
            check=False,
            capture_output=True,
        )
        if result.returncode != 0:
            message = result.stderr.decode("utf-8", errors="replace").strip()
            raise CandidateSnapshotError(f"git hash-object failed: {message}")
        return result.stdout.decode().strip()
