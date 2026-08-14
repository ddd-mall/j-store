from __future__ import annotations

import re
import tomllib
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = REPO_ROOT / "gradle" / "libs.versions.toml"
PLATFORM_MODULE = "j-store-dependencies-platform"


class DependencyManagementContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.catalog = tomllib.loads(CATALOG_PATH.read_text(encoding="utf-8"))
        self.build_scripts = sorted(REPO_ROOT.glob("**/build.gradle.kts"))
        self.active_build_text = "\n".join(
            line
            for path in self.build_scripts
            for line in path.read_text(encoding="utf-8").splitlines()
            if not line.lstrip().startswith("//")
        )

    def test_external_coordinates_are_declared_through_the_catalog(self) -> None:
        direct_coordinate = re.compile(
            r'["\']([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?::[^"\'$]+)?["\']'
        )
        offenders: list[str] = []

        for path in self.build_scripts:
            for line_number, line in enumerate(
                path.read_text(encoding="utf-8").splitlines(), start=1
            ):
                if direct_coordinate.search(line):
                    relative = path.relative_to(REPO_ROOT).as_posix()
                    offenders.append(f"{relative}:{line_number}")

        self.assertEqual([], offenders, f"direct external coordinates: {offenders}")

    def test_project_plugins_do_not_hardcode_versions(self) -> None:
        hardcoded_plugin = re.compile(r'id\("[^"]+"\)\s+version\s+"[^"]+"')
        offenders = [
            path.relative_to(REPO_ROOT).as_posix()
            for path in self.build_scripts
            if hardcoded_plugin.search(path.read_text(encoding="utf-8"))
        ]

        self.assertEqual([], offenders, f"hardcoded project plugin versions: {offenders}")

    def test_catalog_contains_only_referenced_library_and_plugin_aliases(self) -> None:
        unused: list[str] = []
        for section in ("libraries", "plugins"):
            prefix = "libs.plugins." if section == "plugins" else "libs."
            for alias in self.catalog.get(section, {}):
                accessor = prefix + alias.replace("-", ".").replace("_", ".")
                if accessor not in self.active_build_text:
                    unused.append(f"{section}:{alias}")

        self.assertEqual([], unused, f"unused catalog aliases: {unused}")

    def test_unified_platform_owns_version_alignment(self) -> None:
        settings = (REPO_ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        platform_path = REPO_ROOT / PLATFORM_MODULE / "build.gradle.kts"

        self.assertIn(f'include("{PLATFORM_MODULE}")', settings)
        self.assertTrue(platform_path.is_file())
        platform = platform_path.read_text(encoding="utf-8")
        self.assertIn("api(platform(libs.spring.boot.dependencies))", platform)
        self.assertIn("api(platform(libs.junit.bom))", platform)
        self.assertIn("api(platform(libs.open.telemetry.bom))", platform)
        self.assertIn("api(platform(libs.jackson.bom))", platform)
        self.assertIn("api(platform(libs.netty.bom))", platform)
        self.assertIn("api(platform(libs.log4j.bom))", platform)
        self.assertIn("api(libs.postgresql)", platform)
        self.assertIn("api(libs.commons.lang3)", platform)

        non_platform_text = "\n".join(
            path.read_text(encoding="utf-8")
            for path in self.build_scripts
            if path != platform_path
        )
        self.assertNotIn("platform(libs.spring.boot.dependencies)", non_platform_text)
        self.assertNotIn("platform(libs.jackson.bom)", non_platform_text)
        self.assertNotIn("platform(libs.netty.bom)", non_platform_text)
        self.assertNotIn("platform(libs.log4j.bom)", non_platform_text)
        self.assertNotIn("platform(libs.open.telemetry.bom)", non_platform_text)

        root_build = (REPO_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('pluginManager.withPlugin("java-test-fixtures")', root_build)
        self.assertIn('"testFixturesImplementation"', root_build)

    def test_approved_security_and_boot_managed_version_policy(self) -> None:
        versions = self.catalog["versions"]
        libraries = self.catalog["libraries"]

        self.assertEqual("1.62.0", versions["open-telemetry"])
        self.assertEqual("2.21.5", versions["jackson"])
        self.assertEqual("4.1.136.Final", versions["netty"])
        self.assertEqual("2.25.5", versions["log4j"])
        self.assertEqual("42.7.12", versions["postgresql"])
        self.assertEqual("3.19.0", versions["commons-lang3"])
        self.assertEqual("jackson", libraries["jackson-bom"]["version"]["ref"])
        self.assertEqual("netty", libraries["netty-bom"]["version"]["ref"])
        self.assertEqual("log4j", libraries["log4j-bom"]["version"]["ref"])
        self.assertEqual("postgresql", libraries["postgresql"]["version"]["ref"])
        self.assertEqual(
            "commons-lang3", libraries["commons-lang3"]["version"]["ref"]
        )
        self.assertNotIn("spring-security", versions)
        self.assertNotIn("version", libraries["spring-security-crypto"])
        self.assertNotIn("spirng-boot-boot", libraries)

    def test_dependency_management_rules_are_part_of_project_steering(self) -> None:
        guideline_path = (
            REPO_ROOT / "docs" / "steering" / "dependency-management-guidelines.md"
        )
        agents = (REPO_ROOT / "AGENTS.md").read_text(encoding="utf-8")

        self.assertIn(
            "docs/steering/dependency-management-guidelines.md",
            agents,
        )
        self.assertTrue(guideline_path.is_file())

        guideline = guideline_path.read_text(encoding="utf-8")
        required_contracts = (
            "gradle/libs.versions.toml",
            "j-store-dependencies-platform",
            "Spring Boot BOM",
            "安全下限",
            "dependencyInsight",
            "cyclonedxDirectBom",
            "OSV Scanner",
            "./scripts/quality-gate.sh",
        )
        for contract in required_contracts:
            self.assertIn(contract, guideline)

    def test_resolved_dependency_versions_are_verified_by_the_quality_gate(self) -> None:
        root_build = (REPO_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        quality_gate = (REPO_ROOT / "scripts" / "quality-gate.sh").read_text(
            encoding="utf-8"
        )

        self.assertIn('tasks.register("verifyDependencyResolution")', root_build)
        self.assertIn("verifyDependencyResolution", quality_gate)


if __name__ == "__main__":
    unittest.main()
