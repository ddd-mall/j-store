from __future__ import annotations

import unittest
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]


class QodanaConfigurationContractTest(unittest.TestCase):
    def test_bootstrap_relinks_the_gradle_model_left_by_pr_baseline_analysis(self) -> None:
        configuration = yaml.safe_load((REPO_ROOT / "qodana.yaml").read_text())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "backend").mkdir()
            (root / "backend/settings.gradle.kts").touch()
            (root / ".idea").mkdir()
            model = root / ".idea/gradle.xml"
            model.write_text('<project><option name="externalProjectPath" value="$PROJECT_DIR$" />'
                             '<option name="gradleJvm" value="25" /></project>')
            bootstrap = configuration["bootstrap"].replace("scripts/prepare-qodana.sh", str(REPO_ROOT / "scripts/prepare-qodana.sh"))
            for _ in range(2):
                subprocess.run(bootstrap, cwd=root, shell=True, check=True)
                options = {e.get("name"): e.get("value") for e in ET.parse(model).iter("option")}
                self.assertEqual("$PROJECT_DIR$/backend", options["externalProjectPath"])
                self.assertEqual("25", options["gradleJvm"])

    def test_qodana_imports_the_backend_gradle_build_from_the_vcs_root(self) -> None:
        configuration = yaml.safe_load((REPO_ROOT / "qodana.yaml").read_text())
        self.assertEqual(["./backend"], configuration["rootJavaProjects"])
        for project in configuration["rootJavaProjects"]:
            self.assertTrue((REPO_ROOT / project / "settings.gradle.kts").is_file())

    def test_pull_requests_only_analyze_changed_files(self) -> None:
        workflow = (
            REPO_ROOT / ".github/workflows/qodana_code_quality.yml"
        ).read_text(encoding="utf-8")

        self.assertIn("pr-mode: true", workflow)
        self.assertNotIn("pr-mode: false", workflow)

    def test_layout_transition_does_not_restore_a_head_model_into_the_old_baseline(self) -> None:
        workflow = yaml.safe_load((REPO_ROOT / ".github/workflows/qodana_code_quality.yml").read_text())
        scan = next(step for step in workflow["jobs"]["qodana"]["steps"] if step.get("name") == "Qodana Scan")
        self.assertIs(False, scan["with"]["use-caches"])

    def test_qodana_uses_jdk_25_and_rejects_any_problem(self) -> None:
        configuration = (REPO_ROOT / "qodana.yaml").read_text(encoding="utf-8")

        self.assertIn('projectJDK: "25"', configuration)
        self.assertNotIn("\njdk:", configuration)
        self.assertIn("jetbrains/qodana-jvm-community@sha256:", configuration)
        self.assertIn(
            "failureConditions:\n  severityThresholds:\n    any: 0",
            configuration,
        )

    def test_qodana_excludes_optional_multi_dollar_migration_hint(self) -> None:
        configuration = (REPO_ROOT / "qodana.yaml").read_text(encoding="utf-8")

        self.assertIn(
            "exclude:\n  - name: CanConvertToMultiDollarString",
            configuration,
        )

    def test_qodana_is_a_protected_branch_required_check(self) -> None:
        for branch in ("develop", "master"):
            ruleset = (REPO_ROOT / f".github/rulesets/{branch}.json").read_text(
                encoding="utf-8"
            )
            self.assertIn('{"context": "qodana"}', ruleset)


if __name__ == "__main__":
    unittest.main()
