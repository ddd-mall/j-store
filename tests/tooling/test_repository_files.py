from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "scripts"))

from repository_files import repository_files


class RepositoryFilesTest(unittest.TestCase):
    def test_reads_trusted_candidate_manifest_without_git_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "module").mkdir()
            (root / "module" / "Source.kt").write_text("source\n", encoding="utf-8")
            manifest = root / "manifest"
            manifest.write_text("./module/Source.kt\n", encoding="utf-8")

            with patch.dict(
                os.environ,
                {"JSTORE_REPOSITORY_FILES_FILE": str(manifest)},
            ):
                self.assertEqual(["module/Source.kt"], repository_files(root))

    def test_rejects_manifest_path_escape_and_duplicate_entries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "Source.kt"
            source.write_text("source\n", encoding="utf-8")
            manifest = root / "manifest"

            for content in ("../Source.kt\n", "Source.kt\n./Source.kt\n"):
                manifest.write_text(content, encoding="utf-8")
                with self.subTest(content=content), patch.dict(
                    os.environ,
                    {"JSTORE_REPOSITORY_FILES_FILE": str(manifest)},
                ):
                    with self.assertRaises(ValueError):
                        repository_files(root)


if __name__ == "__main__":
    unittest.main()
