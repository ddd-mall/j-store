from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


class QodanaConfigurationContractTest(unittest.TestCase):
    def test_pull_requests_only_analyze_changed_files(self) -> None:
        workflow = (
            REPO_ROOT / ".github/workflows/qodana_code_quality.yml"
        ).read_text(encoding="utf-8")

        self.assertIn("pr-mode: true", workflow)
        self.assertNotIn("pr-mode: false", workflow)

    def test_qodana_uses_jdk_25_and_rejects_any_problem(self) -> None:
        configuration = (REPO_ROOT / "qodana.yaml").read_text(encoding="utf-8")

        self.assertIn('projectJDK: "25"', configuration)
        self.assertNotIn("\njdk:", configuration)
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


if __name__ == "__main__":
    unittest.main()
