from pathlib import Path
import json
import re
import unittest

REPO_ROOT = Path(__file__).resolve().parents[2]


class RepositoryLayoutTest(unittest.TestCase):
    def test_backend_is_a_self_contained_gradle_build(self):
        backend = REPO_ROOT / "backend"
        for name in ("gradlew", "gradlew.bat", "settings.gradle.kts", "build.gradle.kts", "gradle/libs.versions.toml"):
            self.assertTrue((backend / name).is_file(), name)
        modules = {p.name for p in backend.glob("j-store-*") if p.is_dir()}
        registered = set(re.findall(r'include\("([^"\n]+)"\)', (backend / "settings.gradle.kts").read_text()))
        self.assertEqual(modules, registered)
        self.assertIn("j-store-user-boot", registered)
        self.assertIn("j-store-dependencies-platform", registered)
        for name in ("gradlew", "build.gradle.kts", "settings.gradle.kts"):
            self.assertFalse((REPO_ROOT / name).exists(), name)

    def test_frontend_owns_its_workspace_and_existing_admin_application(self):
        frontend = REPO_ROOT / "frontend"
        for name in ("package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "apps/admin-web/package.json"):
            self.assertTrue((frontend / name).is_file(), name)
        self.assertEqual("@j-store/admin-web", json.loads((frontend / "apps/admin-web/package.json").read_text())["name"])
        self.assertFalse((REPO_ROOT / "package.json").exists())
        self.assertFalse((REPO_ROOT / "apps").exists())

    def test_shared_quality_and_ci_use_the_backend_entrypoint(self):
        self.assertTrue((REPO_ROOT / "scripts/backend-gradle.sh").is_file())
        self.assertIn("backend-gradle.sh", (REPO_ROOT / "scripts/quality-gate.sh").read_text())
        self.assertIn("backend/j-store-boot/build/reports", (REPO_ROOT / ".github/workflows/security.yml").read_text())
