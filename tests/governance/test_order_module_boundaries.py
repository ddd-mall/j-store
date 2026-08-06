import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class OrderModuleBoundaryTests(unittest.TestCase):
    def test_order_context_uses_four_layer_modules(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        for module in (
            "j-store-order-domain",
            "j-store-order-application",
            "j-store-order-infrastructure",
            "j-store-order-boot",
        ):
            self.assertIn(f'include("{module}")', settings)
        self.assertNotIn('include("j-store-order")', settings)

    def test_order_layer_dependency_direction(self):
        expected = {
            "j-store-order-domain": {"j-store-common-core"},
            "j-store-order-application": {
                "j-store-order-domain",
                "j-store-common-core",
                "j-store-integration-contracts",
            },
            "j-store-order-infrastructure": {
                "j-store-order-domain",
                "j-store-goods-api",
                "j-store-shop-api",
                "j-store-user-api",
            },
        }
        for module, allowed in expected.items():
            build = (ROOT / module / "build.gradle.kts").read_text(encoding="utf-8")
            dependencies = set(re.findall(r'project\(":([^"]+)"\)', build))
            self.assertTrue(
                dependencies <= allowed,
                f"{module} has forbidden project dependencies: {dependencies - allowed}",
            )

        boot = (ROOT / "j-store-order-boot" / "build.gradle.kts").read_text(encoding="utf-8")
        for dependency in (
            "j-store-order-domain",
            "j-store-order-application",
            "j-store-order-infrastructure",
        ):
            self.assertIn(f'project(":{dependency}")', boot)

    def test_domain_and_application_are_framework_free(self):
        forbidden = (
            "org.springframework",
            "jakarta.persistence",
            "jakarta.transaction",
            "org.hibernate",
        )
        for module in ("j-store-order-domain", "j-store-order-application"):
            source_root = ROOT / module / "src" / "main"
            self.assertTrue(source_root.is_dir(), f"missing source root: {source_root}")
            for source in source_root.rglob("*.kt"):
                text = source.read_text(encoding="utf-8")
                for package in forbidden:
                    self.assertNotIn(package, text, f"{source} imports {package}")


if __name__ == "__main__":
    unittest.main()
