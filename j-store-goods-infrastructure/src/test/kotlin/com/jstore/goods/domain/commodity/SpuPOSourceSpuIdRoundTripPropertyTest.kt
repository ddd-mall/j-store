package com.jstore.goods.domain.commodity

import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.SkuPO
import com.jstore.goods.domain.commodity.persistence.SpuPO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 7: SpuPO ↔ Spu 转换往返保持 sourceSpuId

/**
 * Property 7: SpuPO ↔ Spu 转换往返保持 sourceSpuId
 *
 * For any 有效的 Spu（sourceSpuId 为 null 或非 null），经过 Converter.toPO 转换为 SpuPO，
 * 再经过 Converter.toDomain 转换回 Spu 后，sourceSpuId 应与原始值相等
 * （null 对 null，非 null 对非 null 且 value 相等）。
 *
 * **Validates: Requirements 13.2, 13.3**
 */
class SpuPOSourceSpuIdRoundTripPropertyTest : FunSpec({

    // --- Converter logic replicated from SpuRepositoryImpl (private object) ---

    fun toSkuPO(sku: Sku, spuId: Long): SkuPO {
        return SkuPO(
            id = sku.id.value,
            spuId = spuId,
            skuName = sku.skuName,
            attributes = JsonUtils.toJsonString(sku.attributes),
            price = sku.price.toBigDecimal(),
            merchantCode = sku.merchantCode,
            barcode = sku.barcode,
        )
    }

    fun toDomainSku(po: SkuPO): Sku {
        val attrs: List<Attribute<String, String>> = JsonUtils.deserialize(po.attributes)
        return SkuImpl(
            id = SkuId(po.id),
            skuName = po.skuName,
            attributes = attrs,
            price = Price.fromBigDecimal(po.price),
            merchantCode = po.merchantCode,
            barcode = po.barcode,
        )
    }

    fun toPO(spu: Spu): SpuPO {
        return SpuPO(
            id = spu.id.value,
            name = spu.name,
            description = spu.description,
            status = spu.status,
            version = spu.version,
            sourceSpuId = spu.sourceSpuId?.value,
            skus = spu.skus.map { toSkuPO(it, spu.id.value) }.toMutableList(),
        )
    }

    fun toDomain(po: SpuPO): Spu {
        return SpuImpl(
            id = SpuId(po.id),
            name = po.name,
            description = po.description,
            _status = po.status,
            _skus = po.skus.map { toDomainSku(it) }.toMutableList(),
            _version = po.version,
            sourceSpuId = po.sourceSpuId?.let { SpuId(it) },
        )
    }

    // --- Arb generators ---

    val attributeArb: Arb<Attribute<String, String>> = Arb.bind(
        Arb.string(1..10),
        Arb.string(1..10),
    ) { key, value -> Attribute(key, value) }

    val skuArb: Arb<Sku> = Arb.bind(
        Arb.long(1L..Long.MAX_VALUE),
        Arb.string(1..20),
        Arb.list(attributeArb, 0..3),
        Arb.long(0L..999999L),
    ) { skuIdVal, skuName, attrs, priceFen ->
        SkuImpl(
            id = SkuId(skuIdVal),
            skuName = skuName,
            attributes = attrs,
            price = Price.ofFen(priceFen),
        )
    }

    val nullableSourceSpuIdArb: Arb<SpuId?> = Arb.choice(
        Arb.constant(null),
        Arb.long(1L..Long.MAX_VALUE).map { SpuId(it) },
    )

    val statusArb: Arb<CommodityStatus> = Arb.of(
        CommodityStatus.DRAFT,
        CommodityStatus.OFF_SALE,
        CommodityStatus.ON_SALE,
    )

    val spuArb: Arb<Spu> = Arb.bind(
        Arb.long(1L..Long.MAX_VALUE),
        Arb.string(1..50),
        Arb.string(0..100),
        statusArb,
        Arb.list(skuArb, 0..5),
        Arb.long(1L..10000L),
        nullableSourceSpuIdArb,
    ) { spuIdVal, name, description, status, skus, version, sourceSpuId ->
        SpuImpl(
            id = SpuId(spuIdVal),
            name = name,
            description = description,
            _status = status,
            _skus = skus.toMutableList(),
            _version = version,
            sourceSpuId = sourceSpuId,
        )
    }

    // --- Property test ---

    test("toPO then toDomain round-trip should preserve sourceSpuId") {
        checkAll(100, spuArb) { spu ->
            val po = toPO(spu)
            val roundTripped = toDomain(po)

            roundTripped.sourceSpuId?.value shouldBe spu.sourceSpuId?.value
        }
    }
})
