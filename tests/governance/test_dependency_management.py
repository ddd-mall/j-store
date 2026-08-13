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

        non_platform_text = "\n".join(
            path.read_text(encoding="utf-8")
            for path in self.build_scripts
            if path != platform_path
        )
        self.assertNotIn("platform(libs.spring.boot.dependencies)", non_platform_text)
        self.assertNotIn("platform(libs.jackson.bom)", non_platform_text)
        self.assertNotIn("platform(libs.netty.bom)", non_platform_text)
        self.assertNotIn("platform(libs.open.telemetry.bom)", non_platform_text)

        root_build = (REPO_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('pluginManager.withPlugin("java-test-fixtures")', root_build)
        self.assertIn('"testFixturesImplementation"', root_build)

    def test_approved_and_boot_managed_version_policy(self) -> None:
        versions = self.catalog["versions"]
        libraries = self.catalog["libraries"]

        self.assertEqual("1.62.0", versions["open-telemetry"])
        self.assertNotIn("jackson-bom", libraries)
        self.assertNotIn("netty-bom", libraries)
        self.assertNotIn("spring-security", versions)
        self.assertNotIn("version", libraries["spring-security-crypto"])
        self.assertNotIn("version.ref", libraries["spring-security-crypto"])
        self.assertNotIn("spirng-boot-boot", libraries)


if __name__ == "__main__":
    unittest.main()
