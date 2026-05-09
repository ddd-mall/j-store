package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import org.mockito.kotlin.*

// Feature: commodity-draft-copy-on-write, Property 8: discardDraft 不影响源商品

/**
 * Property 8: discardDraft 不影响源商品
 *
 * For any ON_SALE 状态的源 SPU 及其草稿副本，执行 discardDraft 后，
 * 源 SPU 的 name、description、SKU 列表、version、status 均保持不变。
 *
 * **Validates: Requirements 7.2, 7.4**
 */
class DiscardDraftSourceUnchangedPropertyTest : FunSpec({

    lateinit var spuFactory: SpuFactory
    lateinit var spuRepository: SpuRepository
    lateinit var domainEventPublisher: DomainEventPublisher
    lateinit var snapshotFactory: SpuSnapshotFactory
    lateinit var snapshotRepository: SpuSnapshotRepository
    lateinit var goodsStyleRepository: GoodsStyleRepository
    lateinit var goodsStyleFactory: GoodsStyleFactory
    lateinit var service: CommodityService

    beforeEach {
        spuFactory = mock()
        spuRepository = mock()
        domainEventPublisher = mock()
        snapshotFactory = mock()
        snapshotRepository = mock()
        goodsStyleRepository = mock()
        goodsStyleFactory = mock()
        service = CommodityService(
            spuFactory = spuFactory,
            spuRepository = spuRepository,
            domainEventPublisher = domainEventPublisher,
            snapshotFactory = snapshotFactory,
            snapshotRepository = snapshotRepository,
            goodsStyleRepository = goodsStyleRepository,
            goodsStyleFactory = goodsStyleFactory,
        )
    }

    // Generator for a non-empty list of SKUs
    val skuListArb: Arb<List<Sku>> = Arb.list(
        Arb.bind(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.string(1..20),
            Arb.long(1L..999999L),
            Arb.string(1..10),
        ) { skuIdVal, skuName, priceFen, attrValue ->
            SkuImpl(
                id = SkuId(skuIdVal),
                skuName = skuName,
                attributes = listOf(Attribute("variant", attrValue)),
                price = Price.ofFen(priceFen),
            )
        },
        1..5,
    )

    // Generator for an ON_SALE source SPU
    val sourceSpuArb: Arb<SpuImpl> = Arb.bind(
        Arb.long(1L..Long.MAX_VALUE),
        Arb.string(1..50),
        Arb.string(0..100),
        Arb.long(1L..10000L),
        skuListArb,
    ) { spuIdVal, name, description, version, skus ->
        SpuImpl(
            id = SpuId(spuIdVal),
            name = name,
            description = description,
            _status = CommodityStatus.ON_SALE,
            _skus = skus.toMutableList(),
            _version = version,
        )
    }

    test("discardDraft should not affect source SPU fields") {
        checkAll(100, sourceSpuArb, skuListArb) { source, draftSkus ->
            // Capture source SPU state before discardDraft
            val originalName = source.name
            val originalDescription = source.description
            val originalSkus = source.skus.toList()
            val originalVersion = source.version
            val originalStatus = source.status

            // Create a draft copy referencing the source
            val draftSpuId = SpuId(source.id.value + 1)
            val draft: Spu = SpuImpl(
                id = draftSpuId,
                name = "draft-modified-name",
                description = "draft-modified-desc",
                _status = CommodityStatus.DRAFT,
                _skus = draftSkus.toMutableList(),
                _version = source.version,
                sourceSpuId = source.id,
            )

            whenever(spuRepository.findById(draftSpuId)).thenReturn(draft)

            val result = service.discardDraft(draftSpuId)

            result.shouldBeInstanceOf<Success<Unit>>()
            verify(spuRepository).delete(draft)

            // Verify source SPU fields are completely unchanged
            source.name shouldBe originalName
            source.description shouldBe originalDescription
            source.skus shouldBe originalSkus
            source.version shouldBe originalVersion
            source.status shouldBe originalStatus
        }
    }
})
