package com.jstore.shop.service

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.contracts.commerce.ContractSaleItem
import com.jstore.shop.domain.offer.*
import com.jstore.shop.domain.offer.event.SaleAuthorizedEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OfferAuthorizationServiceTest {
    private val now = Instant.parse("2026-08-05T00:00:00Z")

    @Test
    fun `authorization is durable idempotent and freezes fulfillment policy`() {
        val offer = activeOffer()
        val authorizations = FakeAuthorizations()
        val published = mutableListOf<DomainEvent>()
        val service = service(offer, authorizations, published)

        val first = assertIs<Success<List<SaleAuthorization>>>(service.authorize(command()))
        assertEquals("ORDER-100-OFFER-1", first.value.single().id.value)
        assertEquals("CN-NORTH-1", first.value.single().fulfillmentPolicy.preferredNodeId.value)
        assertEquals(1, authorizations.values.size)
        assertEquals(1, published.filterIsInstance<SaleAuthorizedEvent>().size)

        assertIs<Success<*>>(service.authorize(command()))
        assertEquals(1, authorizations.values.size)
        assertEquals(1, published.filterIsInstance<SaleAuthorizedEvent>().size)
    }

    @Test
    fun `suspended offer rejects new authorization without persisting a token`() {
        val offer = activeOffer().also { it.suspend() }
        val authorizations = FakeAuthorizations()

        assertIs<Failure<*>>(service(offer, authorizations, mutableListOf()).authorize(command()))
        assertEquals(0, authorizations.values.size)
    }

    private fun service(
        offer: SalesOffer,
        authorizations: FakeAuthorizations,
        published: MutableList<DomainEvent>,
    ) =
        OfferAuthorizationService(
            StoreGuard {
                listOf(Store(StoreId(2), MerchantId(7), "旗舰店", StoreStatus.ACTIVE))
            },
            SalesOfferGuard { ids -> ids.filter { it == offer.id }.map { offer } },
            authorizations,
            object : DomainEventPublisher {
                override fun publishEvent(event: DomainEvent) {
                    published.add(event)
                }
            },
        )

    private fun command() =
        AuthorizeSaleCommand(
            orderId = 100,
            merchantId = 7,
            items =
                listOf(
                    ContractSaleItem(
                        offerId = 1,
                        storeId = 2,
                        spuId = 10,
                        skuId = 11,
                        quantity = 2,
                        catalogSnapshotVersion = 3,
                        offerVersion = 1,
                        fulfillmentNodeId = "CN-NORTH-1",
                        channelId = "ONLINE",
                        unitPriceFen = 3_900,
                    )
                ),
            sourceMessageId = "order-created-100",
            occurredAtValue = now,
        )

    private fun activeOffer() =
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

private class FakeAuthorizations : SaleAuthorizationRepository {
    val values = linkedMapOf<SaleAuthorizationId, SaleAuthorization>()

    override fun save(aggregate: SaleAuthorization): SaleAuthorization = aggregate.also {
        values[it.id] = it
    }

    override fun findById(id: SaleAuthorizationId): SaleAuthorization? = values[id]

    override fun findByOrderId(orderId: Long): List<SaleAuthorization> =
        values.values.filter { it.orderId == orderId }
}
