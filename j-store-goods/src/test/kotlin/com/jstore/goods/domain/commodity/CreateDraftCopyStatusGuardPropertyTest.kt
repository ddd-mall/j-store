package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 2: createDraftCopy 拒绝非 ON_SALE 源商品

/**
 * Property 2: createDraftCopy 拒绝非 ON_SALE 源商品
 *
 * For any 状态不是 ON_SALE 的 SPU（即 DRAFT 或 OFF_SALE），
 * 调用 SpuFactory.createDraftCopy 应返回 Failure。
 *
 * **Validates: Requirements 2.4**
 */
class CreateDraftCopyStatusGuardPropertyTest : FunSpec({

    val snowFlakSequence = SnowFlakSequence()
    val factory = SpuFactoryImpl(snowFlakSequence)

    // Non-ON_SALE statuses
    val nonOnSaleStatusArb: Arb<CommodityStatus> = Arb.of(
        CommodityStatus.DRAFT,
        CommodityStatus.OFF_SALE,
    )

    // Generator for a non-empty list of SKUs (1..5)
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

    // Generator for a non-ON_SALE SPU
    val nonOnSaleSpuArb: Arb<SpuImpl> = Arb.bind(
        Arb.long(1L..Long.MAX_VALUE),
        Arb.string(1..50),
        Arb.string(0..100),
        Arb.long(1L..10000L),
        skuListArb,
        nonOnSaleStatusArb,
    ) { spuIdVal, name, description, version, skus, status ->
        SpuImpl(
            id = SpuId(spuIdVal),
            name = name,
            description = description,
            _status = status,
            _skus = skus.toMutableList(),
            _version = version,
        )
    }

    test("createDraftCopy should return Failure for non-ON_SALE SPU") {
        checkAll(100, nonOnSaleSpuArb) { source ->
            val result = factory.createDraftCopy(source)

            result.shouldBeInstanceOf<Failure<*>>()
        }
    }
})
