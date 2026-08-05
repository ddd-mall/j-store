import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTEXTS = ("goods", "payment", "fulfillment", "user", "accounting")
FORBIDDEN_IMPORT = re.compile(r"^import (org\.springframework|jakarta\.|org\.hibernate)", re.MULTILINE)


class CommerceContextModuleBoundaryTest(unittest.TestCase):
    def test_each_active_context_has_four_physical_modules_and_no_legacy_module(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        for context in CONTEXTS:
            for layer in ("domain", "application", "infrastructure", "boot"):
                self.assertIn(f'include("j-store-{context}-{layer}")', settings)
            self.assertNotIn(f'include("j-store-{context}")', settings)

    def test_domain_and_application_are_framework_free(self):
        for context in CONTEXTS:
            for layer in ("domain", "application"):
                source = ROOT / f"j-store-{context}-{layer}" / "src" / "main"
                self.assertTrue(source.is_dir(), source)
                for path in source.rglob("*.kt"):
                    self.assertIsNone(FORBIDDEN_IMPORT.search(path.read_text(encoding="utf-8")), path)

    def test_application_does_not_depend_on_infrastructure_or_boot(self):
        for context in CONTEXTS:
            build = (ROOT / f"j-store-{context}-application" / "build.gradle.kts").read_text(encoding="utf-8")
            self.assertNotIn(f'project(":j-store-{context}-infrastructure")', build)
            self.assertNotIn(f'project(":j-store-{context}-boot")', build)


if __name__ == "__main__":
    unittest.main()
