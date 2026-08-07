from __future__ import annotations

import json
import subprocess
import tomllib
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SPDX_COPYRIGHT = "SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)"
SPDX_LICENSE = "SPDX-License-Identifier: Apache-2.0"


class LicenseGovernanceContractTest(unittest.TestCase):
    def test_all_java_and_kotlin_files_have_spdx_ownership(self) -> None:
        offenders: list[str] = []
        for suffix in ("*.java", "*.kt"):
            for path in REPO_ROOT.rglob(suffix):
                if "build" in path.parts or ".git" in path.parts:
                    continue
                content = path.read_text(encoding="utf-8")
                if SPDX_COPYRIGHT not in content or SPDX_LICENSE not in content:
                    offenders.append(path.relative_to(REPO_ROOT).as_posix())

        self.assertEqual([], sorted(offenders), f"missing SPDX ownership: {offenders}")

    def test_license_delivery_and_dependency_audit_are_enforced(self) -> None:
        build_script = (REPO_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        quality_gate = (REPO_ROOT / "scripts/quality-gate.sh").read_text(encoding="utf-8")
        security_workflow = (REPO_ROOT / ".github/workflows/security.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn('id("app.cash.licensee") version "1.14.1"', build_script)
        self.assertIn('into("META-INF")', build_script)
        self.assertIn('tasks.register("verifyLicenseArtifacts")', build_script)
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
            ["python3", "scripts/check-file-ownership.py"],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_release_evidence_and_master_ruleset_contracts_exist(self) -> None:
        evidence_script = REPO_ROOT / "scripts/create-release-evidence.sh"
        workflow = REPO_ROOT / ".github/workflows/release-evidence.yml"
        ruleset_path = REPO_ROOT / ".github/rulesets/master.json"

        self.assertTrue(evidence_script.is_file())
        self.assertTrue(workflow.is_file())
        ruleset = json.loads(ruleset_path.read_text(encoding="utf-8"))

        self.assertEqual("branch", ruleset["target"])
        self.assertEqual("active", ruleset["enforcement"])
        rule_types = {rule["type"] for rule in ruleset["rules"]}
        self.assertTrue(
            {"deletion", "non_fast_forward", "pull_request", "required_status_checks"}
            <= rule_types
        )
        status_rule = next(
            rule for rule in ruleset["rules"] if rule["type"] == "required_status_checks"
        )
        contexts = {
            item["context"]
            for item in status_rule["parameters"]["required_status_checks"]
        }
        self.assertEqual(
            {
                "quality",
                "static-analysis",
                "dependency-vulnerability-scan",
                "dependency-license-audit",
                "secret-scan",
            },
            contexts,
        )

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
