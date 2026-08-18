from __future__ import annotations

import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from jsonschema import Draft202012Validator


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from agentic_cicd.candidate import (  # noqa: E402
    CandidateRevision,
    CandidateSnapshotError,
    CandidateSnapshotter,
    SNAPSHOT_POLICY_SHA256,
)


CANDIDATE_SCHEMA = REPO_ROOT / "config" / "agentic-cicd" / "candidate-revision.schema.json"


class CandidateSnapshotterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.workspace = self.root / "workspace"
        self.artifacts = self.root / "artifacts"
        self.workspace.mkdir()
        self._git("init", "-q")
        self._git("config", "user.name", "Candidate Test")
        self._git("config", "user.email", "candidate@example.invalid")
        (self.workspace / "tracked.txt").write_text("before\n", encoding="utf-8")
        (self.workspace / "deleted.txt").write_text("delete me\n", encoding="utf-8")
        (self.workspace / "script.sh").write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        self._git("add", "--", "tracked.txt", "deleted.txt", "script.sh")
        self._git("commit", "-qm", "base")
        self.base_sha = self._git("rev-parse", "HEAD").stdout.strip()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def _git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=self.workspace,
            check=True,
            capture_output=True,
            text=True,
        )

    def _snapshotter(self) -> CandidateSnapshotter:
        return CandidateSnapshotter(self.workspace, self.artifacts)

    def test_freeze_captures_worktree_without_modifying_index_and_is_stable(self) -> None:
        (self.workspace / "tracked.txt").write_text("after\n", encoding="utf-8")
        (self.workspace / "deleted.txt").unlink()
        (self.workspace / "untracked.txt").write_text("new\n", encoding="utf-8")
        (self.workspace / "script.sh").chmod(0o755)
        index = self.workspace / ".git" / "index"
        index_sha = hashlib.sha256(index.read_bytes()).hexdigest()

        first = self._snapshotter().freeze(self.base_sha)
        second = self._snapshotter().freeze(self.base_sha)

        self.assertEqual(first, second)
        self.assertEqual(index_sha, hashlib.sha256(index.read_bytes()).hexdigest())
        self.assertRegex(first.candidate_revision, r"^[0-9a-f]{64}$")
        self.assertRegex(first.tree_sha, r"^[0-9a-f]{40}$")
        self.assertTrue(first.archive_path(self.artifacts).is_file())
        self.assertTrue(first.manifest_path(self.artifacts).is_file())

        with tarfile.open(first.archive_path(self.artifacts), "r") as archive:
            members = {member.name: member for member in archive.getmembers()}
            self.assertEqual(
                {"script.sh", "tracked.txt", "untracked.txt"}, set(members)
            )
            self.assertEqual(0o755, members["script.sh"].mode)
            tracked = archive.extractfile(members["tracked.txt"])
            self.assertIsNotNone(tracked)
            self.assertEqual(b"after\n", tracked.read())

    def test_content_or_executable_mode_change_creates_a_new_revision(self) -> None:
        first = self._snapshotter().freeze(self.base_sha)
        (self.workspace / "tracked.txt").write_text("changed\n", encoding="utf-8")
        content_changed = self._snapshotter().freeze(self.base_sha)
        self.assertNotEqual(first.candidate_revision, content_changed.candidate_revision)

        (self.workspace / "tracked.txt").write_text("before\n", encoding="utf-8")
        (self.workspace / "script.sh").chmod(0o755)
        mode_changed = self._snapshotter().freeze(self.base_sha)
        self.assertNotEqual(first.candidate_revision, mode_changed.candidate_revision)

    def test_worktree_byte_change_bypasses_git_content_normalization(self) -> None:
        (self.workspace / ".gitattributes").write_text(
            "normalized.txt text eol=lf\n", encoding="utf-8"
        )
        (self.workspace / "normalized.txt").write_bytes(b"line\n")
        self._git("add", "--", ".gitattributes", "normalized.txt")
        self._git("commit", "-qm", "add normalized file")
        self.base_sha = self._git("rev-parse", "HEAD").stdout.strip()
        first = self._snapshotter().freeze(self.base_sha)

        (self.workspace / "normalized.txt").write_bytes(b"line\r\n")
        normalized_change = self._snapshotter().freeze(self.base_sha)

        self.assertNotEqual(first.tree_sha, normalized_change.tree_sha)
        self.assertNotEqual(first.artifact_sha256, normalized_change.artifact_sha256)
        self.assertNotEqual(
            first.candidate_revision, normalized_change.candidate_revision
        )

    def test_freeze_rejects_unsafe_workspace_entries(self) -> None:
        cases = {
            "nested repository": lambda: self._write("nested/.git/config", "x"),
            "outside workspace": self._outside_symlink,
        }
        if hasattr(os, "mkfifo"):
            cases["special file"] = lambda: os.mkfifo(self.workspace / "candidate.fifo")

        for expected, arrange in cases.items():
            with self.subTest(expected=expected):
                self._git("reset", "--hard", "-q", self.base_sha)
                for child in self.workspace.iterdir():
                    if child.name == ".git" or child.name in {
                        "tracked.txt",
                        "deleted.txt",
                        "script.sh",
                    }:
                        continue
                    if child.is_dir() and not child.is_symlink():
                        shutil.rmtree(child)
                    else:
                        child.unlink(missing_ok=True)
                arrange()
                with self.assertRaisesRegex(CandidateSnapshotError, expected):
                    self._snapshotter().freeze(self.base_sha)

    def test_freeze_excludes_host_owned_runtime_metadata(self) -> None:
        self._write(".agentic-cicd/workspace.json", "host-owned")

        revision = self._snapshotter().freeze(self.base_sha)

        with tarfile.open(revision.archive_path(self.artifacts), "r") as archive:
            self.assertNotIn(".agentic-cicd/workspace.json", archive.getnames())

        self._write(".agentic-cicd/untrusted.json", "candidate-controlled")
        with self.assertRaisesRegex(CandidateSnapshotError, "runtime metadata"):
            self._snapshotter().freeze(self.base_sha)

    def test_materialize_rejects_a_tampered_archive(self) -> None:
        revision = self._snapshotter().freeze(self.base_sha)
        archive = revision.archive_path(self.artifacts)
        archive.write_bytes(archive.read_bytes() + b"tampered")

        with self.assertRaisesRegex(CandidateSnapshotError, "artifact digest"):
            self._snapshotter().materialize(revision, self.root / "materialized")

    def test_materialize_recreates_exact_files_modes_and_internal_symlinks(self) -> None:
        (self.workspace / "script.sh").chmod(0o755)
        (self.workspace / "tracked-link").symlink_to("tracked.txt")
        revision = self._snapshotter().freeze(self.base_sha)
        destination = self.root / "materialized"

        self._snapshotter().materialize(revision, destination)

        self.assertEqual("before\n", (destination / "tracked.txt").read_text())
        self.assertEqual(0o755, (destination / "script.sh").stat().st_mode & 0o777)
        self.assertTrue((destination / "tracked-link").is_symlink())
        self.assertEqual("tracked.txt", os.readlink(destination / "tracked-link"))

    def test_materialize_rejects_archive_path_traversal(self) -> None:
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode="w") as archive:
            member = tarfile.TarInfo("../escape.txt")
            member.size = 1
            archive.addfile(member, io.BytesIO(b"x"))
        payload = buffer.getvalue()
        artifact_sha = hashlib.sha256(payload).hexdigest()
        revision = CandidateRevision(
            base_sha="a" * 40,
            tree_sha="b" * 40,
            artifact_sha256=artifact_sha,
            snapshot_policy_sha256=SNAPSHOT_POLICY_SHA256,
            candidate_revision=CandidateRevision.calculate_revision(
                "a" * 40,
                "b" * 40,
                artifact_sha,
                SNAPSHOT_POLICY_SHA256,
            ),
        )
        archive_path = revision.archive_path(self.artifacts)
        archive_path.parent.mkdir(parents=True)
        archive_path.write_bytes(payload)

        with self.assertRaisesRegex(CandidateSnapshotError, "path traversal"):
            self._snapshotter().materialize(revision, self.root / "materialized")

    def test_freeze_rejects_a_tracked_submodule_entry(self) -> None:
        self._git(
            "update-index",
            "--add",
            "--cacheinfo",
            f"160000,{self.base_sha},vendor/dependency",
        )
        self._git("commit", "-qm", "add gitlink")
        self.base_sha = self._git("rev-parse", "HEAD").stdout.strip()

        with self.assertRaisesRegex(CandidateSnapshotError, "submodule"):
            self._snapshotter().freeze(self.base_sha)

    def test_freeze_does_not_execute_external_git_clean_filters(self) -> None:
        marker = self.root / "filter-executed"
        filter_script = self.workspace / "danger-filter.sh"
        filter_script.write_text(
            f"#!/bin/sh\ntouch {marker}\ncat\n", encoding="utf-8"
        )
        filter_script.chmod(0o755)
        (self.workspace / ".gitattributes").write_text(
            "*.txt filter=danger\n", encoding="utf-8"
        )
        self._git("config", "filter.danger.clean", filter_script.as_posix())
        self._git("config", "filter.danger.required", "true")

        revision = self._snapshotter().freeze(self.base_sha)

        self.assertRegex(revision.tree_sha, r"^[0-9a-f]{40}$")
        self.assertFalse(marker.exists())

    def test_freeze_git_subprocesses_do_not_inherit_controller_secrets(self) -> None:
        real_git = shutil.which("git")
        self.assertIsNotNone(real_git)
        wrapper_directory = self.root / "bin"
        wrapper_directory.mkdir()
        observed_names = self.root / "git-environment-names"
        wrapper = wrapper_directory / "git"
        wrapper.write_text(
            "#!/usr/bin/python3\n"
            "import os\n"
            "import sys\n"
            "from pathlib import Path\n"
            f"with Path({str(observed_names)!r}).open('a', encoding='utf-8') as stream:\n"
            "    stream.write(' '.join(sorted(os.environ)) + '\\n')\n"
            f"os.execv({real_git!r}, [{real_git!r}, *sys.argv[1:]])\n",
            encoding="utf-8",
        )
        wrapper.chmod(0o755)
        inherited = {
            "PATH": f"{wrapper_directory}:{os.environ['PATH']}",
            "JSTORE_SYMPHONY_GITHUB_TOKEN": "github-secret",
            "OPENAI_API_KEY": "model-secret",
            "GIT_ASKPASS": "/tmp/untrusted-askpass",
            "GIT_HTTP_LOW_SPEED_LIMIT": "1",
            "GIT_HTTP_LOW_SPEED_TIME": "3600",
            "GIT_PROXY_COMMAND": "/tmp/untrusted-proxy",
            "GIT_SSL_NO_VERIFY": "1",
            "GIT_CONFIG_PARAMETERS": "'http.sslVerify=false'",
        }

        with patch.dict(os.environ, inherited, clear=False):
            revision = self._snapshotter().freeze(self.base_sha)

        self.assertRegex(revision.tree_sha, r"^[0-9a-f]{40}$")
        observed = {
            name
            for line in observed_names.read_text(encoding="utf-8").splitlines()
            for name in line.split()
        }
        forbidden = set(inherited) - {"PATH"}
        leaked = sorted(forbidden & observed)
        self.assertFalse(leaked, f"candidate Git inherited variables: {leaked}")

    def test_freeze_rejects_line_breaks_in_candidate_paths(self) -> None:
        (self.workspace / "ambiguous\npath.txt").write_text("payload\n", encoding="utf-8")

        with self.assertRaisesRegex(CandidateSnapshotError, "path traversal"):
            self._snapshotter().freeze(self.base_sha)

    def test_materialize_rejects_destination_symlink_without_outside_write(self) -> None:
        revision = self._snapshotter().freeze(self.base_sha)
        outside = self.root / "outside"
        outside.mkdir()
        destination = self.root / "destination"
        destination.symlink_to(outside, target_is_directory=True)

        with self.assertRaisesRegex(CandidateSnapshotError, "destination.*symlink"):
            self._snapshotter().materialize(revision, destination)

        self.assertEqual([], list(outside.iterdir()))

    def test_artifact_root_cannot_be_inside_candidate_workspace(self) -> None:
        with self.assertRaisesRegex(CandidateSnapshotError, "artifact root"):
            CandidateSnapshotter(self.workspace, self.workspace / "artifacts")

    def test_workspace_must_be_the_git_top_level(self) -> None:
        nested = self.workspace / "nested"
        nested.mkdir()

        with self.assertRaisesRegex(CandidateSnapshotError, "top level"):
            CandidateSnapshotter(nested, self.artifacts)

    def test_candidate_manifest_round_trips_the_machine_schema(self) -> None:
        revision = self._snapshotter().freeze(self.base_sha)
        schema = json.loads(CANDIDATE_SCHEMA.read_text(encoding="utf-8"))

        Draft202012Validator(schema).validate(revision.to_json())
        self.assertEqual(revision, CandidateRevision.from_json(revision.to_json()))

        payload = revision.to_json()
        payload["head_sha"] = self.base_sha
        with self.assertRaisesRegex(ValueError, "contract"):
            CandidateRevision.from_json(payload)

    def _write(self, relative_path: str, content: str) -> None:
        destination = self.workspace / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")

    def _outside_symlink(self) -> None:
        outside = self.root / "outside.txt"
        outside.write_text("outside\n", encoding="utf-8")
        (self.workspace / "outside-link").symlink_to(outside)


if __name__ == "__main__":
    unittest.main()
