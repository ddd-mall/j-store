from __future__ import annotations

import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import yaml

from scripts.agentic_cicd.level2_deployment import (
    prepare_level2_deployment_candidate,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


class LevelTwoDeploymentCandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.artifacts = self.root / "artifacts"
        self.artifacts.mkdir()
        self.repository = "ddd-mall/agentic-cicd-disposable"
        self.github_app_login = "jstore-agentic-cicd[bot]"
        self.reviewer = "jstore-maintainer"
        self.contract_digest = "1" * 64
        self.binding_digest = "2" * 64
        self.runtime_digest = "sha256:" + "3" * 64
        self.archive = self.artifacts / "controller.docker.tar"
        self.archive.write_bytes(b"reviewed controller archive")
        self.sbom = self.artifacts / "controller.spdx.json"
        self.provenance = self.artifacts / "controller.provenance.json"
        for path, predicate in (
            (self.sbom, "https://spdx.dev/Document"),
            (self.provenance, "https://slsa.dev/provenance/v1"),
        ):
            path.write_text(
                json.dumps(
                    {
                        "predicateType": predicate,
                        "subject": [
                            {"digest": {"sha256": self.runtime_digest.removeprefix("sha256:")}}
                        ],
                    }
                ),
                encoding="utf-8",
            )
        self.source_record = self.artifacts / "controller.source.json"
        self.record = {
            "schema_version": 1,
            "created_at": "2026-08-19T00:00:00+00:00",
            "image": (
                "docker.io/library/jstore-agentic-cicd:test"
                f"-level2-{self.contract_digest[:16]}"
                f"-binding-{self.binding_digest[:16]}"
            ),
            "runtime_manifest_digest": self.runtime_digest,
            "archive": self.archive.name,
            "archive_sha256": hashlib.sha256(self.archive.read_bytes()).hexdigest(),
            "symphony_revision": "4" * 40,
            "controller_revision": "5" * 40,
            "phase_bridge_patch_sha256": "6" * 64,
            "phase_routing_patch_sha256": "7" * 64,
            "dependency_lock_sha256": "8" * 64,
            "workflow_sha256": "9" * 64,
            "capability_level": 2,
            "target_repository": self.repository,
            "state_contract_sha256": self.contract_digest,
            "runtime_binding_sha256": self.binding_digest,
            "codex_version": "1.2.3",
            "elixir_image": "example/elixir@sha256:" + "a" * 64,
            "node_image": "example/node@sha256:" + "b" * 64,
            "buildx_version": "github.com/docker/buildx v1.0.0",
            "sbom": self.sbom.name,
            "provenance": self.provenance.name,
        }
        self._write_record()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def _write_record(self) -> str:
        self.source_record.write_text(
            json.dumps(self.record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return hashlib.sha256(self.source_record.read_bytes()).hexdigest()

    def _prepare(self, *, expected_sha256: str | None = None) -> Path:
        output = self.root / "prepared"
        prepare_level2_deployment_candidate(
            source_record_path=self.source_record,
            expected_source_record_sha256=expected_sha256 or self._write_record(),
            repository=self.repository,
            github_app_login=self.github_app_login,
            reviewer=self.reviewer,
            output_directory=output,
            repository_root=REPOSITORY_ROOT,
        )
        return output

    def test_prepares_digest_bound_credentialed_manifest_without_secret_values(self) -> None:
        output = self._prepare()
        documents = [
            document
            for document in yaml.safe_load_all(
                (output / "manifest.yaml").read_text(encoding="utf-8")
            )
            if document
        ]
        deployment = next(
            document
            for document in documents
            if document.get("kind") == "Deployment"
            and document.get("metadata", {}).get("name") == "symphony"
        )
        expected_image = (
            "docker.io/library/jstore-agentic-cicd@" + self.runtime_digest
        )
        pod = deployment["spec"]["template"]
        self.assertEqual(expected_image, pod["spec"]["containers"][0]["image"])
        self.assertEqual(expected_image, pod["spec"]["initContainers"][0]["image"])
        environment = {
            item["name"]: item for item in pod["spec"]["containers"][0]["env"]
        }
        self.assertEqual(self.repository, environment["JSTORE_SYMPHONY_REPOSITORY"]["value"])
        self.assertEqual(
            f"https://github.com/{self.repository}.git",
            environment["JSTORE_SYMPHONY_REPOSITORY_URL"]["value"],
        )
        self.assertEqual(
            self.github_app_login,
            environment["JSTORE_GITHUB_APP_LOGIN"]["value"],
        )
        self.assertEqual(
            self.reviewer,
            environment["JSTORE_GITHUB_REVIEWER"]["value"],
        )
        self.assertNotIn("value", environment["JSTORE_SYMPHONY_GITHUB_TOKEN"])
        self.assertEqual(
            "symphony-github-token",
            environment["JSTORE_SYMPHONY_GITHUB_TOKEN"]["valueFrom"]["secretKeyRef"]["name"],
        )
        annotations = pod["metadata"]["annotations"]
        self.assertEqual(self.binding_digest, annotations["agentic-cicd.jstore.io/runtime-binding-sha256"])
        self.assertEqual(self.repository, annotations["agentic-cicd.jstore.io/target-repository"])
        profile = json.loads(
            (output / "deployment-profile.json").read_text(encoding="utf-8")
        )
        self.assertEqual(expected_image, profile["image_ref"])
        self.assertEqual(self.repository, profile["repository"])
        self.assertEqual(self.github_app_login, profile["github_app_login"])
        self.assertEqual(self.reviewer, profile["reviewer"])
        self.assertEqual(0o444, (output / "manifest.yaml").stat().st_mode & 0o777)
        self.assertEqual(0o444, (output / "deployment-profile.json").stat().st_mode & 0o777)

    def test_rejects_source_record_digest_or_repository_drift_without_output(self) -> None:
        output = self.root / "prepared"
        with self.assertRaisesRegex(ValueError, "source record digest"):
            prepare_level2_deployment_candidate(
                source_record_path=self.source_record,
                expected_source_record_sha256="0" * 64,
                repository=self.repository,
                github_app_login=self.github_app_login,
                reviewer=self.reviewer,
                output_directory=output,
                repository_root=REPOSITORY_ROOT,
            )
        self.assertFalse(output.exists())

        expected = self._write_record()
        with self.assertRaisesRegex(ValueError, "target repository"):
            prepare_level2_deployment_candidate(
                source_record_path=self.source_record,
                expected_source_record_sha256=expected,
                repository="ddd-mall/other-disposable",
                github_app_login=self.github_app_login,
                reviewer=self.reviewer,
                output_directory=output,
                repository_root=REPOSITORY_ROOT,
            )
        self.assertFalse(output.exists())

    def test_rejects_production_alias_and_non_level_two_record(self) -> None:
        self.record["target_repository"] = "DDD-MALL/J-STORE"
        expected = self._write_record()
        with self.assertRaisesRegex(ValueError, "must not target"):
            prepare_level2_deployment_candidate(
                source_record_path=self.source_record,
                expected_source_record_sha256=expected,
                repository="DDD-MALL/J-STORE",
                github_app_login=self.github_app_login,
                reviewer=self.reviewer,
                output_directory=self.root / "prepared",
                repository_root=REPOSITORY_ROOT,
            )

        self.record["target_repository"] = self.repository
        self.record["capability_level"] = 0
        with self.assertRaisesRegex(ValueError, "capability level"):
            self._prepare()

    def test_rejects_tag_binding_archive_and_attestation_drift(self) -> None:
        cases = (
            ("image", "docker.io/library/jstore-agentic-cicd:unbound", "image tag"),
            ("runtime_binding_sha256", "f" * 64, "image tag"),
        )
        for field, value, message in cases:
            with self.subTest(field=field):
                original = self.record[field]
                self.record[field] = value
                with self.assertRaisesRegex(ValueError, message):
                    self._prepare()
                self.record[field] = original

        self.archive.write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "archive digest"):
            self._prepare()
        self.archive.write_bytes(b"reviewed controller archive")

        self.sbom.write_text("{}", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "SBOM attestation"):
            self._prepare()

    def test_rejects_existing_output_directory(self) -> None:
        output = self.root / "prepared"
        output.mkdir()
        with self.assertRaisesRegex(FileExistsError, "already exists"):
            prepare_level2_deployment_candidate(
                source_record_path=self.source_record,
                expected_source_record_sha256=self._write_record(),
                repository=self.repository,
                github_app_login=self.github_app_login,
                reviewer=self.reviewer,
                output_directory=output,
                repository_root=REPOSITORY_ROOT,
            )

    def test_renderer_subprocess_receives_only_trusted_environment(self) -> None:
        actual_run = subprocess.run
        observed_environments: list[dict[str, str]] = []

        def capture(*args: object, **kwargs: object) -> subprocess.CompletedProcess[str]:
            observed_environments.append(dict(kwargs["env"]))
            return actual_run(*args, **kwargs)

        with mock.patch.dict(
            os.environ,
            {
                "JSTORE_SYMPHONY_GITHUB_TOKEN": "must-not-be-inherited",
                "GITHUB_TOKEN": "must-not-be-inherited",
                "OPENAI_API_KEY": "must-not-be-inherited",
            },
        ), mock.patch(
            "scripts.agentic_cicd.level2_deployment.subprocess.run",
            side_effect=capture,
        ):
            self._prepare()

        self.assertEqual(1, len(observed_environments))
        for secret_name in (
            "JSTORE_SYMPHONY_GITHUB_TOKEN",
            "GITHUB_TOKEN",
            "OPENAI_API_KEY",
        ):
            self.assertNotIn(secret_name, observed_environments[0])

    def test_cli_exposes_prepare_only_contract(self) -> None:
        script = (
            REPOSITORY_ROOT
            / "scripts"
            / "agentic-cicd-level2-deployment-prepare.py"
        )
        result = subprocess.run(
            ["python3", str(script), "--help"],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        for option in (
            "--source-record",
            "--source-record-sha256",
            "--repository",
            "--github-app-login",
            "--reviewer",
            "--output-dir",
        ):
            self.assertIn(option, result.stdout)
        source = script.read_text(encoding="utf-8")
        self.assertNotIn("kubectl apply", source)
        self.assertNotIn("GitHubRestGraphqlAdapter", source)
        self.assertNotIn("urllib", source)

    def test_rejects_invalid_or_identical_handoff_logins(self) -> None:
        for app_login, reviewer in (
            ("not-a-bot", self.reviewer),
            (self.github_app_login, "reviewer[bot]"),
            (self.github_app_login, self.github_app_login),
        ):
            with self.subTest(app_login=app_login, reviewer=reviewer):
                with self.assertRaisesRegex(ValueError, "login"):
                    prepare_level2_deployment_candidate(
                        source_record_path=self.source_record,
                        expected_source_record_sha256=self._write_record(),
                        repository=self.repository,
                        github_app_login=app_login,
                        reviewer=reviewer,
                        output_directory=self.root / "prepared",
                        repository_root=REPOSITORY_ROOT,
                    )


if __name__ == "__main__":
    unittest.main()
