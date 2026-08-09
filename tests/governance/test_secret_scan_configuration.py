from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
GITLEAKS_IGNORE = REPO_ROOT / ".gitleaksignore"
SECURITY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "security.yml"
AFTER_SALE_API = (
    REPO_ROOT
    / ".qoder"
    / "repowiki"
    / "en"
    / "content"
    / "API Reference"
    / "After-Sale Management API.md"
)
KNOWN_FALSE_POSITIVE = (
    "2d3475ab73a045b213dae4f47e824c0ace1d7481:"
    ".qoder/repowiki/en/content/API Documentation/After-Sale Processing API.md:"
    "generic-api-key:466"
)


class SecretScanConfigurationTest(unittest.TestCase):
    def test_only_the_reviewed_documentation_finding_is_ignored(self) -> None:
        entries = [
            line.strip()
            for line in GITLEAKS_IGNORE.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        ]

        self.assertEqual([KNOWN_FALSE_POSITIVE], entries)

    def test_idempotency_key_example_is_an_obvious_placeholder(self) -> None:
        documentation = AFTER_SALE_API.read_text(encoding="utf-8")

        self.assertIn('Idempotency-Key: "<merchant-approval-request-id>"', documentation)

    def test_security_workflow_explicitly_loads_the_ignore_file(self) -> None:
        workflow = SECURITY_WORKFLOW.read_text(encoding="utf-8")

        self.assertEqual(2, workflow.count("--gitleaks-ignore-path .gitleaksignore"))


if __name__ == "__main__":
    unittest.main()
