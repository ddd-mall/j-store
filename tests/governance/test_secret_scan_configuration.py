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
KNOWN_FALSE_POSITIVES = [
    (
        "2d3475ab73a045b213dae4f47e824c0ace1d7481:"
        ".qoder/repowiki/en/content/API Documentation/After-Sale Processing API.md:"
        "generic-api-key:466"
    ),
    (
        "549fa3eb4916f6edf1b4960f6df890221bbb9943:"
        "j-store-outbox-spring/src/test/kotlin/com/jstore/outbox/spring/messaging/"
        "OutboxIntegrationMessagePublisherTest.kt:generic-api-key:48"
    ),
    (
        "3631568275bb82bd2358788723b95aaea0e05754:"
        "j-store-outbox-spring/src/test/kotlin/com/jstore/outbox/spring/"
        "OutboxEventPublisherTest.kt:generic-api-key:135"
    ),
    (
        "386126f3aad184e2fcdd5ad37d6ea0d29c61c321:"
        "j-store-outbox-spring/src/test/kotlin/com/jstore/outbox/spring/messaging/"
        "OutboxIntegrationMessagePublisherTest.kt:generic-api-key:48"
    ),
    (
        "98daa4019bedc1ebfd42074acd66c950d73c3180:"
        "j-store-outbox-spring/src/test/kotlin/com/jstore/outbox/spring/"
        "OutboxEventPublisherTest.kt:generic-api-key:135"
    ),
]


class SecretScanConfigurationTest(unittest.TestCase):
    def test_only_reviewed_false_positive_fingerprints_are_ignored(self) -> None:
        entries = [
            line.strip()
            for line in GITLEAKS_IGNORE.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        ]

        self.assertEqual(KNOWN_FALSE_POSITIVES, entries)

    def test_idempotency_key_example_is_an_obvious_placeholder(self) -> None:
        documentation = AFTER_SALE_API.read_text(encoding="utf-8")

        self.assertIn('Idempotency-Key: "<merchant-approval-request-id>"', documentation)

    def test_security_workflow_explicitly_loads_the_ignore_file(self) -> None:
        workflow = SECURITY_WORKFLOW.read_text(encoding="utf-8")

        self.assertEqual(2, workflow.count("--gitleaks-ignore-path .gitleaksignore"))

    def test_static_analysis_pins_the_semgrep_ruleset(self) -> None:
        workflow = SECURITY_WORKFLOW.read_text(encoding="utf-8")

        self.assertNotIn("--config p/default", workflow)
        self.assertNotIn("https://semgrep.dev/c/p/default", workflow)
        self.assertIn("repository: semgrep/semgrep-rules", workflow)
        self.assertIn("ref: 40b8c63f75dc7c22c8a77482d73bfb864b146f7e", workflow)
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn("--config \"$rule_bundle\"", workflow)
        self.assertIn("! -path '*/audit/*'", workflow)


if __name__ == "__main__":
    unittest.main()
