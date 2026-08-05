package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 2: createDraftCopy 拒绝非 PUBLISHED 源商品

/**
 * Property 2: createDraftCopy 拒绝非 PUBLISHED 源商品
 *
 * For any 状态不是 PUBLISHED 的 SPU（即 DRAFT 或 ARCHIVED）， 调用 SpuFactory.createDraftCopy 应返回 Failure。
 *
 * **Validates: Requirements 2.4**
 */
class CreateDraftCopyStatusGuardPropertyTest :
    FunSpec({
        val snowFlakSequence = SnowFlakSequence()
        val factory = SpuFactoryImpl(snowFlakSequence)

        // Non-PUBLISHED statuses
        val nonOnSaleStatusArb: Arb<CommodityStatus> =
            Arb.of(
                CommodityStatus.DRAFT,
                CommodityStatus.ARCHIVED,
            )

        // Generator for a non-empty list of SKUs (1..5)
        val skuListArb: Arb<List<Sku>> =
            Arb.list(
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
                    )
                },
                1..5,
            )

        // Generator for a non-PUBLISHED SPU
        val nonOnSaleSpuArb: Arb<SpuImpl> =
            Arb.bind(
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

        test("createDraftCopy should return Failure for non-PUBLISHED SPU") {
            checkAll(100, nonOnSaleSpuArb) { source ->
                val result = factory.createDraftCopy(source)

                result.shouldBeInstanceOf<Failure<*>>()
            }
        }
    })
