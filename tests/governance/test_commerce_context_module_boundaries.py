import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTEXTS = (
    "goods",
    "order",
    "cart",
    "trade",
    "payment",
    "fulfillment",
    "user",
    "accounting",
    "shop",
    "inventory",
    "warehouse",
)
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

    def test_mutating_repository_adapters_require_an_existing_transaction(self):
        bare_write_transaction = re.compile(
            r"@Transactional\s*\n\s*override fun (?:save|add|create|createWithOwner)\b"
        )
        for context in CONTEXTS:
            source = ROOT / f"j-store-{context}-infrastructure" / "src" / "main"
            self.assertTrue(source.is_dir(), source)
            for path in source.rglob("*RepositoryImpl.kt"):
                text = path.read_text(encoding="utf-8")
                self.assertIsNone(
                    bare_write_transaction.search(text),
                    f"{path} opens a write transaction instead of requiring Propagation.MANDATORY",
                )

    def test_domain_models_do_not_expose_public_mutable_state(self):
        public_var = re.compile(
            r"^\s+(?!(?:private|internal)\s)(?:override\s+)?var\s+[A-Za-z_]\w*\s*:",
            re.MULTILINE,
        )
        for context in CONTEXTS:
            source = ROOT / f"j-store-{context}-domain" / "src" / "main"
            self.assertTrue(source.is_dir(), source)
            for path in source.rglob("*.kt"):
                self.assertIsNone(
                    public_var.search(path.read_text(encoding="utf-8")),
                    f"{path} exposes mutable domain state outside behavior methods",
                )

    def test_domain_commands_are_data_carriers(self):
        behavior = re.compile(r"^\s+fun\s+[A-Za-z_]\w*\s*\(", re.MULTILINE)
        for context in CONTEXTS:
            source = ROOT / f"j-store-{context}-domain" / "src" / "main"
            for path in source.rglob("*.kt"):
                relative_parts = path.relative_to(source).parts
                is_command = "command" in relative_parts or "comand" in relative_parts
                if is_command:
                    self.assertIsNone(
                        behavior.search(path.read_text(encoding="utf-8")),
                        f"{path} contains command behavior instead of data only",
                    )

    def test_cross_context_boot_dependencies_use_published_contracts(self):
        allowed_suffixes = ("-api", "-client-spring")
        project_dependency = re.compile(r'project\(":(j-store-([a-z]+)-[^"\)]+)"\)')
        for context in CONTEXTS:
            build = ROOT / f"j-store-{context}-boot" / "build.gradle.kts"
            for dependency, target_context in project_dependency.findall(
                build.read_text(encoding="utf-8")
            ):
                if target_context in CONTEXTS and target_context != context:
                    self.assertTrue(
                        dependency.endswith(allowed_suffixes),
                        f"{build} bypasses a published cross-context contract via {dependency}",
                    )


if __name__ == "__main__":
    unittest.main()
