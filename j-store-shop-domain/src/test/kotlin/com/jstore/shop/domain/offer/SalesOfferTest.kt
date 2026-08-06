package com.jstore.shop.domain.offer

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SalesOfferTest {
    @Test
    fun `authorization is a durable promise and later suspension only blocks new orders`() {
        val now = Instant.parse("2026-08-05T00:00:00Z")
        val offer = activeOffer(now)

        assertIs<Success<SaleAuthorization>>(offer.authorize(100, 1, 3_900, now))
        assertIs<Success<Unit>>(offer.suspend())
        assertIs<Failure<*>>(offer.authorize(101, 1, 3_900, now.plusSeconds(1)))
    }

    @Test
    fun `offer rejects stale version and price`() {
        val now = Instant.parse("2026-08-05T00:00:00Z")
        val offer = activeOffer(now)

        assertEquals(
            OfferErrors.VERSION_MISMATCH,
            (offer.authorize(100, 1, 3_900, now, 99) as Failure<BusinessError>).error,
        )
        assertEquals(
            OfferErrors.PRICE_MISMATCH,
            (offer.authorize(100, 1, 4_000, now) as Failure<BusinessError>).error,
        )
    }

    private fun activeOffer(now: Instant) =
        SalesOffer(
            SalesOfferId(1),
            StoreId(2),
            MerchantId(7),
            SkuId(11),
            Channel("ONLINE", "CN"),
            Price.ofFen(3_900),
            OfferStatus.ACTIVE,
            EffectivePeriod(now.minusSeconds(60), now.plusSeconds(3_600)),
            PurchaseLimit(5),
            FulfillmentPolicy(FulfillmentNodeId("CN-NORTH-1"), false),
            1,
        )
}
