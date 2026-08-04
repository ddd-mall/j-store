package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 1: createDraftCopy 保持源商品数据完整性

/**
 * Property 1: createDraftCopy 保持源商品数据完整性
 *
 * For any 有效的 ON_SALE 状态 SPU（包含任意 name、description、SKU 列表和 version）， 通过 SpuFactory.createDraftCopy
 * 创建的草稿副本应满足： 草稿的 name 等于源商品的 name，草稿的 description 等于源商品的 description， 草稿的 SKU 列表与源商品的 SKU
 * 列表内容一致，草稿的 version 等于源商品的 version， 草稿的 status 为 DRAFT，草稿的 sourceSpuId 等于源商品的 id， 草稿的 id 不等于源商品的
 * id。
 *
 * **Validates: Requirements 2.2, 2.3**
 */
class CreateDraftCopyDataIntegrityPropertyTest :
    FunSpec({
        val snowFlakSequence = SnowFlakSequence()
        val factory = SpuFactoryImpl(snowFlakSequence)

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
                        price = Price.ofFen(priceFen),
                    )
                },
                1..5,
            )

        // Generator for an ON_SALE source SPU
        val onSaleSpuArb: Arb<SpuImpl> =
            Arb.bind(
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

        test("createDraftCopy should preserve source data integrity for ON_SALE SPU") {
            checkAll(100, onSaleSpuArb) { source ->
                val result = factory.createDraftCopy(source)

                result.shouldBeInstanceOf<Success<Spu>>()
                val draft = result.value

                draft.name shouldBe source.name
                draft.description shouldBe source.description
                draft.skus shouldBe source.skus
                draft.version shouldBe source.version
                draft.status shouldBe CommodityStatus.DRAFT
                draft.sourceSpuId shouldBe source.id
                draft.id shouldNotBe source.id
            }
        }
    })
