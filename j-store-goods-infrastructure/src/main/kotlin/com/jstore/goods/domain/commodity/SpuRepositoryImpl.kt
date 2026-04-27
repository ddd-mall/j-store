package com.jstore.goods.domain.commodity

import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.SkuPO
import com.jstore.goods.domain.commodity.persistence.SpuPO
import com.jstore.goods.domain.commodity.persistence.SpuPOJpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SpuRepositoryImpl(
    private val jpaRepository: SpuPOJpaRepository,
) : SpuRepository {

    override fun save(entity: Spu): Spu {
        val po = Converter.toPO(entity)
        po.updateTime = LocalDateTime.now()
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: SpuId): Spu? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    private object Converter {

        fun toPO(spu: Spu): SpuPO {
            return SpuPO(
                id = spu.id.value,
                name = spu.name,
                description = spu.description,
                status = spu.status,
                version = spu.version,
                skus = spu.skus.map { toSkuPO(it, spu.id.value) }.toMutableList(),
            )
        }

        fun toSkuPO(sku: Sku, spuId: Long): SkuPO {
            return SkuPO(
                id = sku.id.value,
                spuId = spuId,
                skuName = sku.skuName,
                attributes = JsonUtils.toJsonString(sku.attributes),
                price = sku.price.toBigDecimal(),
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
            )
        }

        fun toDomainSku(po: SkuPO): Sku {
            val attrs: List<Attribute<String, String>> = JsonUtils.deserialize(po.attributes)
            return SkuImpl(
                id = SkuId(po.id),
                skuName = po.skuName,
                attributes = attrs,
                price = Price.fromBigDecimal(po.price),
            )
        }
    }
}
