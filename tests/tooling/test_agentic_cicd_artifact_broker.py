from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.artifact_broker import ArtifactLeaseStore, ArtifactStore


TOKEN = "a-secure-single-use-token-with-more-than-32-characters"


class ArtifactLeaseStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.store = ArtifactLeaseStore(self.root / "leases", maximum_ttl_seconds=60)
        self.artifact = "a" * 64

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_lease_is_exact_short_lived_and_single_use(self) -> None:
        lease = self.store.issue(
            self.artifact, ttl_seconds=30, now=100, token=TOKEN
        )

        self.assertEqual(130, lease.expires_at)
        self.assertIsNotNone(self.store.consume(TOKEN, self.artifact, now=130))
        self.assertIsNone(self.store.consume(TOKEN, self.artifact, now=130))

    def test_wrong_artifact_or_expired_lease_is_consumed_without_access(self) -> None:
        self.store.issue(self.artifact, ttl_seconds=10, now=100, token=TOKEN)
        self.assertIsNone(self.store.consume(TOKEN, "b" * 64, now=101))
        self.assertIsNone(self.store.consume(TOKEN, self.artifact, now=101))

        self.store.issue(self.artifact, ttl_seconds=10, now=100, token=TOKEN)
        self.assertIsNone(self.store.consume(TOKEN, self.artifact, now=111))

    def test_rejects_excessive_ttl_and_duplicate_token(self) -> None:
        with self.assertRaisesRegex(ValueError, "TTL"):
            self.store.issue(self.artifact, ttl_seconds=61, token=TOKEN)
        self.store.issue(self.artifact, ttl_seconds=10, token=TOKEN)
        with self.assertRaises(FileExistsError):
            self.store.issue(self.artifact, ttl_seconds=10, token=TOKEN)


class ArtifactStoreTest(unittest.TestCase):
    def test_opens_only_digest_matching_regular_archive(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archives = root / "archives"
            archives.mkdir()
            payload = b"immutable candidate archive"
            digest = hashlib.sha256(payload).hexdigest()
            (archives / f"{digest}.tar").write_bytes(payload)

            opened = ArtifactStore(root).open_verified(digest)
            self.assertIsNotNone(opened)
            stream, size = opened  # type: ignore[misc]
            with stream:
                self.assertEqual(payload, stream.read())
            self.assertEqual(len(payload), size)

            (archives / f"{'b' * 64}.tar").write_bytes(payload)
            self.assertIsNone(ArtifactStore(root).open_verified("b" * 64))


if __name__ == "__main__":
    unittest.main()
