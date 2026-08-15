from __future__ import annotations

import hashlib
import io
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.agentic_cicd.gate_fetch import GateFetchError, fetch_candidate


def archive(name: str = "candidate.txt", payload: bytes = b"candidate\n") -> bytes:
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w", format=tarfile.USTAR_FORMAT) as stream:
        member = tarfile.TarInfo(name)
        member.mode = 0o644
        member.size = len(payload)
        stream.addfile(member, io.BytesIO(payload))
    return output.getvalue()


class GateFetchTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_fetches_exact_archive_into_new_workspace(self) -> None:
        payload = archive()
        source = self.root / "source.tar"
        source.write_bytes(payload)
        workspace = self.root / "workspace"
        metadata = self.root / "metadata"
        metadata.mkdir()
        repository_files = metadata / "repository-files"

        fetch_candidate(
            artifact_url=source.as_uri(),
            artifact_sha256=hashlib.sha256(payload).hexdigest(),
            token="a" * 32,
            workspace=workspace,
            repository_files_output=repository_files,
        )

        self.assertEqual(
            "candidate\n",
            (workspace / "candidate.txt").read_text(encoding="utf-8"),
        )
        self.assertFalse((self.root / "candidate.tar").exists())
        self.assertEqual("./candidate.txt\n", repository_files.read_text(encoding="utf-8"))
        self.assertEqual(0o440, repository_files.stat().st_mode & 0o777)

    def test_streams_a_large_member_without_materializing_it_as_one_buffer(self) -> None:
        payload = archive(payload=b"x" * (2 * 1024 * 1024))
        source = self.root / "large.tar"
        source.write_bytes(payload)
        fetch_candidate(
            artifact_url=source.as_uri(),
            artifact_sha256=hashlib.sha256(payload).hexdigest(),
            token="a" * 32,
            workspace=self.root / "large-workspace",
        )
        self.assertEqual(
            2 * 1024 * 1024,
            (self.root / "large-workspace" / "candidate.txt").stat().st_size,
        )

    @patch("scripts.agentic_cicd.gate_fetch.MAXIMUM_ARCHIVE_MEMBERS", 2)
    def test_rejects_many_small_files_before_materializing_workspace(self) -> None:
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w", format=tarfile.USTAR_FORMAT) as stream:
            for index in range(3):
                member = tarfile.TarInfo(f"file-{index}.txt")
                member.mode = 0o644
                member.size = 0
                stream.addfile(member, io.BytesIO())
        payload = output.getvalue()
        source = self.root / "many.tar"
        source.write_bytes(payload)
        workspace = self.root / "many-workspace"

        with self.assertRaisesRegex(GateFetchError, "member limit"):
            fetch_candidate(
                artifact_url=source.as_uri(),
                artifact_sha256=hashlib.sha256(payload).hexdigest(),
                token="a" * 32,
                workspace=workspace,
            )
        self.assertFalse(workspace.exists())

    def test_rejects_digest_mismatch_and_path_traversal_without_partial_workspace(self) -> None:
        source = self.root / "source.tar"
        source.write_bytes(archive())
        workspace = self.root / "digest-workspace"
        with self.assertRaisesRegex(GateFetchError, "digest"):
            fetch_candidate(
                artifact_url=source.as_uri(),
                artifact_sha256="f" * 64,
                token="a" * 32,
                workspace=workspace,
            )
        self.assertFalse(workspace.exists())

        malicious = archive("../outside.txt", b"outside\n")
        source.write_bytes(malicious)
        workspace = self.root / "traversal-workspace"
        with self.assertRaisesRegex(GateFetchError, "traversal"):
            fetch_candidate(
                artifact_url=source.as_uri(),
                artifact_sha256=hashlib.sha256(malicious).hexdigest(),
                token="a" * 32,
                workspace=workspace,
            )
        self.assertFalse(workspace.exists())
        self.assertFalse((self.root / "outside.txt").exists())


if __name__ == "__main__":
    unittest.main()
