package com.jstore.goods.domain.commodity

import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 5: mergeFromDraft 拒绝非 PUBLISHED 目标

/**
 * Property 5: mergeFromDraft 拒绝非 PUBLISHED 目标
 *
 * For any 状态不是 PUBLISHED 的 SPU（即 DRAFT 或 ARCHIVED）， 调用 mergeFromDraft 应返回 Failure，且 SPU 的所有字段保持不变。
 *
 * **Validates: Requirements 9.5**
 */
class MergeFromDraftStatusGuardPropertyTest :
    FunSpec({

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

        // Generator for a non-PUBLISHED SPU (target)
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

        // Generator for a draft SPU (the source of merge data) with at least one SKU
        val draftSpuArb: Arb<SpuImpl> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE),
                Arb.string(1..50),
                Arb.string(0..100),
                skuListArb,
            ) { spuIdVal, name, description, skus ->
                SpuImpl(
                    id = SpuId(spuIdVal),
                    name = name,
                    description = description,
                    _status = CommodityStatus.DRAFT,
                    _skus = skus.toMutableList(),
                    _version = 1L,
                )
            }

        test(
            "mergeFromDraft should return Failure for non-PUBLISHED SPU and leave all fields unchanged"
        ) {
            checkAll(100, nonOnSaleSpuArb, draftSpuArb) { target, draft ->
                // Capture original field values before the call
                val originalName = target.name
                val originalDescription = target.description
                val originalSkus = target.skus.toList()
                val originalVersion = target.version
                val originalStatus = target.status

                val result = target.mergeFromDraft(draft)

                result.shouldBeInstanceOf<Failure<*>>()
                target.name shouldBe originalName
                target.description shouldBe originalDescription
                target.skus shouldBe originalSkus
                target.version shouldBe originalVersion
                target.status shouldBe originalStatus
            }
        }
    })
