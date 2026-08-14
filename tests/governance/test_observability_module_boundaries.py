import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ObservabilityModuleBoundaryTests(unittest.TestCase):
    def test_common_observability_module_has_no_project_or_web_runtime_dependency(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        build = (
            ROOT / "j-store-observability-spring" / "build.gradle.kts"
        ).read_text(encoding="utf-8")

        self.assertIn('include("j-store-observability-spring")', settings)
        self.assertEqual(set(), set(re.findall(r'project\(":([^"]+)"\)', build)))
        self.assertIn("compileOnly(libs.spring.boot.starter.web)", build)
        self.assertNotIn("implementation(libs.spring.boot.starter.web)", build)
        self.assertNotIn("runtimeOnly(libs.spring.boot.starter.web)", build)

    def test_boot_consumes_the_common_module_instead_of_redeclaring_runtime(self):
        build = (ROOT / "j-store-boot" / "build.gradle.kts").read_text(
            encoding="utf-8"
        )

        self.assertIn('project(":j-store-observability-spring")', build)
        self.assertNotIn("libs.spring.boot.starter.actuator", build)
        self.assertNotIn("libs.micrometer.tracing.bridge.otel", build)
        self.assertNotIn("libs.micrometer.registry.prometheus", build)

    def test_component_health_and_configuration_remain_component_owned(self):
        common_profile = (
            ROOT
            / "j-store-boot"
            / "src"
            / "main"
            / "resources"
            / "application-observability.properties"
        ).read_text(encoding="utf-8")
        outbox_profile = (
            ROOT
            / "j-store-boot"
            / "src"
            / "main"
            / "resources"
            / "application-outbox-observability.properties"
        ).read_text(encoding="utf-8")
        outbox_imports = (
            ROOT
            / "j-store-outbox-spring"
            / "src"
            / "main"
            / "resources"
            / "META-INF"
            / "spring"
            / "org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        ).read_text(encoding="utf-8")

        self.assertNotIn("group.operations.include=outbox", common_profile)
        self.assertIn("group.operations.include=outbox", outbox_profile)
        self.assertIn("OutboxHealthAutoConfiguration", outbox_imports)
        self.assertFalse(
            (
                ROOT
                / "j-store-boot"
                / "src"
                / "main"
                / "kotlin"
                / "com"
                / "jstore"
                / "observability"
                / "OutboxHealthIndicator.kt"
            ).exists()
        )


if __name__ == "__main__":
    unittest.main()
