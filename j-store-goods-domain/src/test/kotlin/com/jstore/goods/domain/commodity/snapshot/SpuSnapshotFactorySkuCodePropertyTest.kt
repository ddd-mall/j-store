package com.jstore.goods.domain.commodity.snapshot

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 7: 快照保持 SKU 编码字段

/**
 * Property 7: 快照保持 SKU 编码字段
 *
 * For any SPU 及其包含的 SKU 列表（SKU 具有任意 merchantCode 和 barcode 值，含 null）， 通过
 * SpuSnapshotFactory.createSnapshot 创建快照后，每个 SkuSnapshot 的 merchantCode 和 barcode 应与对应 SKU 的值完全一致。
 *
 * **Validates: Requirements 10.3, 10.4**
 */
class SpuSnapshotFactorySkuCodePropertyTest :
    FunSpec({
        val snowFlakSequence = SnowFlakSequence()
        val factory = SpuSnapshotFactoryImpl(snowFlakSequence)

        // Generator for nullable strings (merchantCode / barcode)
        val nullableStringArb: Arb<String?> =
            Arb.choice(
                Arb.constant(null),
                Arb.string(1..64),
            )

        // Generator for a single SKU with random merchantCode and barcode
        val skuArb: Arb<Sku> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE), // skuId
                Arb.string(1..20), // skuName
                Arb.long(1L..999999L), // price in fen
                nullableStringArb, // merchantCode
                nullableStringArb, // barcode
            ) { skuIdVal, skuName, priceFen, merchantCode, barcode ->
                SkuImpl(
                    id = SkuId(skuIdVal),
                    skuName = skuName,
                    attributes = listOf(Attribute("color", "red")),
                    merchantCode = merchantCode,
                    barcode = barcode,
                )
            }

        // Generator for a list of SKUs (1..5 items, each with unique attributes to pass addSku)
        val skuListArb: Arb<List<Sku>> =
            Arb.list(
                Arb.bind(
                    Arb.long(1L..Long.MAX_VALUE),
                    Arb.string(1..20),
                    Arb.long(1L..999999L),
                    nullableStringArb,
                    nullableStringArb,
                    Arb.string(1..10), // unique attribute value to avoid duplicate check
                ) { skuIdVal, skuName, priceFen, merchantCode, barcode, attrValue ->
                    SkuImpl(
                        id = SkuId(skuIdVal),
                        skuName = skuName,
                        attributes = listOf(Attribute("variant", attrValue)),
                        merchantCode = merchantCode,
                        barcode = barcode,
                    )
                },
                1..5,
            )

        test("snapshot should preserve merchantCode and barcode from each SKU") {
            checkAll(100, skuListArb) { skus ->
                // Build an SPU with the generated SKUs
                val spu =
                    SpuImpl(
                        id = SpuId(snowFlakSequence.nextId()),
                        name = "Test SPU",
                        description = "desc",
                        _status = CommodityStatus.DRAFT,
                        _skus = skus.toMutableList(),
                    )

                val snapshot = factory.createSnapshot(spu)

                // Verify each SkuSnapshot preserves the encoding fields
                snapshot.skuSnapshots.size shouldBe skus.size
                snapshot.skuSnapshots.forEachIndexed { index, skuSnapshot ->
                    val originalSku = skus[index]
                    skuSnapshot.merchantCode shouldBe originalSku.merchantCode
                    skuSnapshot.barcode shouldBe originalSku.barcode
                }
            }
        }
    })
