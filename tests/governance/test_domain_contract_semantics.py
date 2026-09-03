import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class DomainContractSemanticsTest(unittest.TestCase):
    def test_order_trade_facts_have_no_fabricated_defaults(self):
        files = (
            ROOT
            / "j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderItemImpl.kt",
            ROOT
            / "j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/command/OrderCreateCMD.kt",
            ROOT
            / "j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/event/OrderDomainEvent.kt",
        )
        forbidden = re.compile(
            r"(?:offerId\s*:\s*Long\s*=\s*skuId|"
            r"storeId\s*:\s*Long\s*=|"
            r"offerVersion\s*:\s*Long\s*=|"
            r"fulfillmentNodeId\s*:\s*String\s*=|"
            r"channelId\s*:\s*String\s*=)"
        )
        for path in files:
            self.assertIsNone(forbidden.search(path.read_text(encoding="utf-8")), path)

    def test_after_sale_repository_adapter_contains_no_business_decisions(self):
        path = (
            ROOT
            / "j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepositoryImpl.kt"
        )
        text = path.read_text(encoding="utf-8")
        self.assertNotIn("AfterSaleErrors.", text)
        self.assertNotIn("return Failure", text)

    def test_aggregate_repository_does_not_save_a_different_aggregate(self):
        path = (
            ROOT
            / "j-store-shop-domain/src/main/kotlin/com/jstore/shop/domain/merchant/MerchantRepositories.kt"
        )
        self.assertNotIn("createWithOwner", path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
