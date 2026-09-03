from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MESSAGE_SCOPE_FILES = [
    REPO_ROOT
    / "j-store-messaging-core/src/main/kotlin/com/jstore/messaging/IntegrationMessage.kt",
    REPO_ROOT
    / "j-store-messaging-core/src/main/kotlin/com/jstore/messaging/IntegrationMessageTransport.kt",
    REPO_ROOT / "j-store-outbox-core/src/main/kotlin/com/jstore/outbox/OutboxEntry.kt",
    REPO_ROOT
    / "j-store-outbox-spring/src/main/kotlin/com/jstore/outbox/spring/persistence/OutboxEntryPO.kt",
    REPO_ROOT
    / "j-store-integration-contracts/src/main/kotlin/com/jstore/contracts/commerce/CommerceIntegrationMessages.kt",
    REPO_ROOT / "j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql",
    REPO_ROOT
    / "j-store-boot/src/main/resources/db/migration/V20260807__event_delivery_targets.sql",
]


class MessageScopeSemanticsTest(unittest.TestCase):
    def test_message_scope_models_do_not_reintroduce_ambiguous_tenant_identity(self) -> None:
        for path in MESSAGE_SCOPE_FILES:
            content = path.read_text(encoding="utf-8")
            self.assertNotIn("tenantId", content, path)
            self.assertNotIn("tenant_id", content, path)

    def test_merchant_and_deployment_scopes_remain_independent(self) -> None:
        metadata = MESSAGE_SCOPE_FILES[0].read_text(encoding="utf-8")
        persistence = MESSAGE_SCOPE_FILES[3].read_text(encoding="utf-8")
        contracts = MESSAGE_SCOPE_FILES[4].read_text(encoding="utf-8")

        self.assertIn("val merchantScopeId: String?", metadata)
        self.assertIn("val deploymentScopeId: String?", metadata)
        self.assertIn('name = "merchant_scope_id"', persistence)
        self.assertIn('name = "deployment_scope_id"', persistence)
        self.assertIn("merchantScopeId = merchantId.toString()", contracts)


if __name__ == "__main__":
    unittest.main()
