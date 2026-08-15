from __future__ import annotations

import json
import subprocess
import sys
import tomllib
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from repository_files import repository_files
SPDX_COPYRIGHT = "SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)"
SPDX_LICENSE = "SPDX-License-Identifier: Apache-2.0"


class LicenseGovernanceContractTest(unittest.TestCase):
    def test_all_java_and_kotlin_files_have_spdx_ownership(self) -> None:
        offenders: list[str] = []
        tracked_sources = repository_files(REPO_ROOT)
        for relative_path in tracked_sources:
            if not relative_path.endswith((".java", ".kt")):
                continue
            path = REPO_ROOT / relative_path
            if not path.is_file():
                continue
            content = path.read_text(encoding="utf-8")
            if SPDX_COPYRIGHT not in content or SPDX_LICENSE not in content:
                offenders.append(relative_path)

        self.assertEqual([], sorted(offenders), f"missing SPDX ownership: {offenders}")

    def test_license_delivery_and_dependency_audit_are_enforced(self) -> None:
        build_script = (REPO_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        quality_gate = (REPO_ROOT / "scripts/quality-gate.sh").read_text(encoding="utf-8")
        security_workflow = (REPO_ROOT / ".github/workflows/security.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn("alias(libs.plugins.licensee) apply false", build_script)
        self.assertIn('into("META-INF")', build_script)
        self.assertIn('tasks.register("verifyLicenseArtifacts")', build_script)
        self.assertIn('python "$tool_root/check-file-ownership.py"', quality_gate)
        self.assertIn("./gradlew licensee", quality_gate)
        self.assertIn("verifyLicenseArtifacts", quality_gate)
        self.assertIn("dependency-license-audit:", security_workflow)
        self.assertIn("./gradlew licensee", security_workflow)

    def test_file_ownership_manifest_distinguishes_gradle_wrapper(self) -> None:
        manifest_path = REPO_ROOT / "config/licenses/file-ownership.toml"
        manifest = tomllib.loads(manifest_path.read_text(encoding="utf-8"))

        self.assertEqual("original", manifest["default"]["classification"])
        self.assertEqual("潘少峰 (Peter Pan)", manifest["default"]["copyright"])
        wrapper = manifest["overrides"]["gradle_wrapper"]
        self.assertEqual("third-party", wrapper["classification"])
        self.assertEqual("Apache-2.0", wrapper["license"])
        self.assertIn("gradle/wrapper/**", wrapper["paths"])

        result = subprocess.run(
            [sys.executable, "scripts/check-file-ownership.py"],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_release_evidence_and_protected_branch_ruleset_contracts_exist(self) -> None:
        evidence_script = REPO_ROOT / "scripts/create-release-evidence.sh"
        workflow = REPO_ROOT / ".github/workflows/release-evidence.yml"

        self.assertTrue(evidence_script.is_file())
        self.assertTrue(workflow.is_file())

        expected_contexts = {
            "branch-policy",
            "quality",
            "static-analysis",
            "dependency-vulnerability-scan",
            "dependency-license-audit",
            "secret-scan",
        }
        for branch in ("master", "develop"):
            ruleset_path = REPO_ROOT / f".github/rulesets/{branch}.json"
            ruleset = json.loads(ruleset_path.read_text(encoding="utf-8"))

            self.assertEqual("branch", ruleset["target"])
            self.assertEqual("active", ruleset["enforcement"])
            self.assertEqual(
                [f"refs/heads/{branch}"],
                ruleset["conditions"]["ref_name"]["include"],
            )
            rule_types = {rule["type"] for rule in ruleset["rules"]}
            self.assertLessEqual(
                {
                    "deletion",
                    "non_fast_forward",
                    "pull_request",
                    "required_status_checks",
                },
                rule_types,
            )
            status_rule = next(
                rule
                for rule in ruleset["rules"]
                if rule["type"] == "required_status_checks"
            )
            contexts = {
                item["context"]
                for item in status_rule["parameters"]["required_status_checks"]
            }
            self.assertEqual(expected_contexts, contexts)

    def test_quality_workflow_fetches_spotless_ratchet_reference(self) -> None:
        build_script = (REPO_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        quality_workflow = (REPO_ROOT / ".github/workflows/quality.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn('ratchetFrom("origin/master")', build_script)
        self.assertIn(
            "fetch-depth: 0",
            quality_workflow,
            "Quality Gate must fetch origin/master for the Spotless ratchet baseline",
        )


if __name__ == "__main__":
    unittest.main()
