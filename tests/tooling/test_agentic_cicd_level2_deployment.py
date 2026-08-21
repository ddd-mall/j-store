from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.level2_deployment import (
    LevelTwoSourceRecord,
    prepare_level2_deployment_candidate,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


class RetiredLevelTwoKubernetesDeploymentTest(unittest.TestCase):
    def test_prepare_fails_closed_without_creating_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "prepared"
            with self.assertRaisesRegex(RuntimeError, "host-native execution bundle"):
                prepare_level2_deployment_candidate(
                    source_record_path=root / "unused.json",
                    expected_source_record_sha256="0" * 64,
                    repository="ddd-mall/j-store-agentic-cicd-disposable",
                    github_app_login="j-store-agentic-cicd[bot]",
                    reviewer="jstore-maintainer",
                    output_directory=output,
                    repository_root=REPOSITORY_ROOT,
                )
            self.assertFalse(output.exists())

    def test_verified_source_record_still_rejects_artifact_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "controller.docker.tar"
            archive.write_bytes(b"reviewed")
            digest = "3" * 64
            for name, predicate in (
                ("controller.spdx.json", "https://spdx.dev/Document"),
                ("controller.provenance.json", "https://slsa.dev/provenance/v1"),
            ):
                (root / name).write_text(
                    json.dumps(
                        {
                            "predicateType": predicate,
                            "subject": [{"digest": {"sha256": digest}}],
                        }
                    ),
                    encoding="utf-8",
                )
            contract = "1" * 64
            binding = "2" * 64
            repository = "ddd-mall/j-store-agentic-cicd-disposable"
            record = {
                "schema_version": 1,
                "created_at": "2026-08-20T00:00:00+08:00",
                "image": (
                    "docker.io/library/jstore-agentic-cicd:test"
                    f"-level2-{contract[:16]}-binding-{binding[:16]}"
                ),
                "runtime_manifest_digest": f"sha256:{digest}",
                "archive": archive.name,
                "archive_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
                "sbom": "controller.spdx.json",
                "provenance": "controller.provenance.json",
                "controller_revision": "4" * 40,
                "symphony_revision": "5" * 40,
                "phase_bridge_patch_sha256": "6" * 64,
                "phase_routing_patch_sha256": "7" * 64,
                "dependency_lock_sha256": "8" * 64,
                "workflow_sha256": "9" * 64,
                "capability_level": 2,
                "target_repository": repository,
                "state_contract_sha256": contract,
                "runtime_binding_sha256": binding,
                "codex_version": "0.148.0",
                "elixir_image": "example/elixir@sha256:" + "a" * 64,
                "node_image": "example/node@sha256:" + "b" * 64,
                "buildx_version": "github.com/docker/buildx v1.0.0",
            }
            record_path = root / "source.json"
            record_path.write_text(json.dumps(record), encoding="utf-8")
            record_sha = hashlib.sha256(record_path.read_bytes()).hexdigest()
            loaded = LevelTwoSourceRecord.load_verified(
                path=record_path,
                expected_sha256=record_sha,
                repository=repository,
            )
            self.assertEqual(f"sha256:{digest}", loaded.runtime_manifest_digest)

            archive.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "archive digest"):
                LevelTwoSourceRecord.load_verified(
                    path=record_path,
                    expected_sha256=record_sha,
                    repository=repository,
                )


if __name__ == "__main__":
    unittest.main()
