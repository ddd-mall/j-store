from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
TARGET_SCRIPT = (
    REPOSITORY_ROOT
    / "deploy"
    / "kubernetes"
    / "agentic-cicd"
    / "image"
    / "write-spotless-targets.sh"
)
KNOWN_EXAMPLE = REPOSITORY_ROOT / "docs" / "Spring-Modulith示例代码.kt"


class GateSpotlessTargetsTest(unittest.TestCase):
    def test_known_baseline_exception_is_hash_bound_and_changes_are_checked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            example = root / "docs" / KNOWN_EXAMPLE.name
            example.parent.mkdir()
            example.write_bytes(KNOWN_EXAMPLE.read_bytes())
            source = root / "module" / "Candidate.kt"
            source.parent.mkdir()
            source.write_text("package candidate\n", encoding="utf-8")
            targets = root / "targets.txt"
            repository_files = root / "repository-files.txt"
            repository_files.write_text(
                f"./docs/{KNOWN_EXAMPLE.name}\n./module/Candidate.kt\n",
                encoding="utf-8",
            )

            subprocess.run(
                ["bash", str(TARGET_SCRIPT), str(targets)],
                cwd=root,
                env={
                    "PATH": os.environ["PATH"],
                    "JSTORE_REPOSITORY_FILES_FILE": str(repository_files),
                },
                check=True,
            )
            selected = targets.read_text(encoding="utf-8").splitlines()
            self.assertIn("./module/Candidate.kt", selected)
            self.assertNotIn(f"./docs/{KNOWN_EXAMPLE.name}", selected)

            example.write_text("changed\n", encoding="utf-8")
            subprocess.run(
                ["bash", str(TARGET_SCRIPT), str(targets)],
                cwd=root,
                env={
                    "PATH": os.environ["PATH"],
                    "JSTORE_REPOSITORY_FILES_FILE": str(repository_files),
                },
                check=True,
            )
            self.assertIn(
                f"./docs/{KNOWN_EXAMPLE.name}",
                targets.read_text(encoding="utf-8").splitlines(),
            )


if __name__ == "__main__":
    unittest.main()
