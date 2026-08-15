from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import stat
import tarfile
import urllib.request
from pathlib import Path, PurePosixPath


MAXIMUM_ARCHIVE_BYTES = 512 * 1024 * 1024
MAXIMUM_ARCHIVE_MEMBERS = 10_000


class GateFetchError(RuntimeError):
    """Raised when an exact candidate cannot be fetched and materialized."""


def fetch_candidate(
    *,
    artifact_url: str,
    artifact_sha256: str,
    token: str,
    workspace: Path,
    repository_files_output: Path | None = None,
) -> None:
    if len(artifact_sha256) != 64 or any(
        value not in "0123456789abcdef" for value in artifact_sha256
    ):
        raise GateFetchError("artifact digest must be a lowercase SHA-256")
    if not token or any(value.isspace() for value in token):
        raise GateFetchError("artifact capability is missing or malformed")
    if workspace.exists() or workspace.is_symlink():
        raise GateFetchError("gate workspace must not exist before fetch")
    workspace.parent.mkdir(parents=True, exist_ok=True)
    archive_path = workspace.parent / "candidate.tar"
    descriptor = os.open(
        archive_path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o400,
    )
    digest = hashlib.sha256()
    size = 0
    request = urllib.request.Request(
        artifact_url, headers={"Authorization": f"Bearer {token}"}
    )
    try:
        with os.fdopen(descriptor, "wb") as output:
            with urllib.request.urlopen(request, timeout=30) as response:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > MAXIMUM_ARCHIVE_BYTES:
                        raise GateFetchError("candidate archive exceeds the size limit")
                    digest.update(chunk)
                    output.write(chunk)
        if digest.hexdigest() != artifact_sha256:
            raise GateFetchError("candidate archive digest does not match")
        repository_files = _extract_verified_archive(archive_path, workspace)
        if repository_files_output is not None:
            _write_repository_manifest(repository_files_output, repository_files)
    finally:
        archive_path.unlink(missing_ok=True)


def _extract_verified_archive(archive_path: Path, workspace: Path) -> list[str]:
    workspace.mkdir(mode=0o770)
    try:
        repository_files: list[str] = []
        with tarfile.open(archive_path, mode="r:") as archive:
            seen: set[str] = set()
            for member in _bounded_members(archive):
                relative = _safe_path(member.name)
                if member.name in seen:
                    raise GateFetchError("candidate archive contains duplicate paths")
                seen.add(member.name)
                output = workspace.joinpath(*relative.parts)
                if member.isreg():
                    repository_files.append(f"./{member.name}")
                    continue
                if not member.issym():
                    raise GateFetchError("candidate archive contains unsupported entries")
                target = Path(member.linkname)
                if target.is_absolute() or not _is_within(
                    (output.parent / target).resolve(strict=False), workspace
                ):
                    raise GateFetchError("candidate archive symlink escapes workspace")

        with tarfile.open(archive_path, mode="r:") as archive:
            for member in _bounded_members(archive):
                relative = _safe_path(member.name)
                output = workspace.joinpath(*relative.parts)
                _create_safe_parents(workspace, output.parent)
                if member.isreg():
                    source = archive.extractfile(member)
                    if source is None:
                        raise GateFetchError("candidate archive file is unreadable")
                    file_descriptor = os.open(
                        output,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL
                        | getattr(os, "O_NOFOLLOW", 0),
                        member.mode & 0o777,
                    )
                    with os.fdopen(file_descriptor, "wb") as stream:
                        shutil.copyfileobj(source, stream, length=1024 * 1024)
                else:
                    output.symlink_to(member.linkname)
        return sorted(repository_files)
    except Exception:
        _remove_partial_workspace(workspace)
        raise


def _bounded_members(archive: tarfile.TarFile):
    count = 0
    while True:
        member = archive.next()
        if member is None:
            return
        archive.members.clear()
        count += 1
        if count > MAXIMUM_ARCHIVE_MEMBERS:
            raise GateFetchError("candidate archive exceeds the member limit")
        yield member


def _create_safe_parents(root: Path, parent: Path) -> None:
    relative = parent.relative_to(root)
    current = root
    for part in relative.parts:
        current /= part
        try:
            current.mkdir(mode=0o770)
        except FileExistsError:
            metadata = current.lstat()
            if not stat.S_ISDIR(metadata.st_mode):
                raise GateFetchError("candidate parent path is not a directory")


def _remove_partial_workspace(root: Path) -> None:
    if not root.exists() or root.is_symlink():
        root.unlink(missing_ok=True)
        return
    for directory, child_directories, files in os.walk(root, topdown=False):
        for name in files:
            (Path(directory) / name).unlink(missing_ok=True)
        for name in child_directories:
            child = Path(directory) / name
            if child.is_symlink():
                child.unlink(missing_ok=True)
            else:
                child.rmdir()
    root.rmdir()


def _safe_path(name: str) -> PurePosixPath:
    relative = PurePosixPath(name)
    if (
        not name
        or "\n" in name
        or "\r" in name
        or relative.is_absolute()
        or "." in relative.parts
        or ".." in relative.parts
        or name.endswith("/")
    ):
        raise GateFetchError("candidate archive path traversal")
    return relative


def _write_repository_manifest(path: Path, repository_files: list[str]) -> None:
    if path.exists() or path.is_symlink() or not path.parent.is_dir():
        raise GateFetchError("repository manifest destination is unsafe")
    descriptor = os.open(
        path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o440,
    )
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        for relative in repository_files:
            stream.write(relative + "\n")
        stream.flush()
        os.fsync(stream.fileno())


def _is_within(path: Path, root: Path) -> bool:
    try:
        return os.path.commonpath((str(path), str(root))) == str(root)
    except ValueError:
        return False


def main() -> None:
    parser = argparse.ArgumentParser(description="Fetch one exact gate candidate")
    parser.add_argument("--artifact-url", required=True)
    parser.add_argument("--artifact-sha256", required=True)
    parser.add_argument("--workspace", type=Path, default=Path("/workspace/source"))
    parser.add_argument(
        "--repository-files-output",
        type=Path,
        default=Path("/gate-metadata/repository-files"),
    )
    arguments = parser.parse_args()
    fetch_candidate(
        artifact_url=arguments.artifact_url,
        artifact_sha256=arguments.artifact_sha256,
        token=os.environ.pop("ARTIFACT_TOKEN", ""),
        workspace=arguments.workspace,
        repository_files_output=arguments.repository_files_output,
    )


if __name__ == "__main__":
    main()
