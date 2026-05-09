package com.jstore.goods.domain.commodity

import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.SpuSnapshotPO
import com.jstore.goods.domain.commodity.persistence.SpuSnapshotPOJpaRepository
import com.jstore.goods.domain.commodity.snapshot.*
import org.springframework.stereotype.Repository

@Repository
class SpuSnapshotRepositoryImpl(
    private val jpaRepository: SpuSnapshotPOJpaRepository,
) : SpuSnapshotRepository {

    override fun save(entity: SpuSnapshot): SpuSnapshot {
        val po = Converter.toPO(entity)
        jpaRepository.save(po)
        return entity
    }

    override fun findById(id: SpuSnapshotId): SpuSnapshot? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findBySpuIdAndVersion(spuId: SpuId, version: Long): SpuSnapshot? {
        return jpaRepository.findBySpuIdAndSnapshotVersion(spuId.value, version)
            ?.let { Converter.toDomain(it) }
    }

    override fun findLatestBySpuId(spuId: SpuId): SpuSnapshot? {
        return jpaRepository.findFirstBySpuIdOrderBySnapshotVersionDesc(spuId.value)
            ?.let { Converter.toDomain(it) }
    }

    private object Converter {

        fun toPO(snapshot: SpuSnapshot): SpuSnapshotPO {
            return SpuSnapshotPO(
                id = snapshot.id.value,
                spuId = snapshot.spuId.value,
                snapshotVersion = snapshot.snapshotVersion,
                spuName = snapshot.spuName,
                description = snapshot.description,
                skuSnapshots = JsonUtils.toJsonString(snapshot.skuSnapshots.map { toSkuSnapshotMap(it) }),
                createdAt = snapshot.createdAt,
            )
        }

        private fun toSkuSnapshotMap(sku: SkuSnapshot): Map<String, Any?> {
            return mapOf(
                "skuId" to sku.skuId.value,
                "skuName" to sku.skuName,
                "attributes" to sku.attributes.map { mapOf("key" to it.key, "value" to it.value) },
                "price" to sku.price.fen,
                "merchantCode" to sku.merchantCode,
                "barcode" to sku.barcode,
            )
        }

        fun toDomain(po: SpuSnapshotPO): SpuSnapshot {
            val skuMaps: List<Map<String, Any?>> = JsonUtils.deserialize(po.skuSnapshots)
            return SpuSnapshot(
                id = SpuSnapshotId(po.id),
                spuId = SpuId(po.spuId),
                snapshotVersion = po.snapshotVersion,
                spuName = po.spuName,
                description = po.description,
                skuSnapshots = skuMaps.map { toSkuSnapshot(it) },
                createdAt = po.createdAt,
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun toSkuSnapshot(map: Map<String, Any?>): SkuSnapshot {
            val skuId = (map["skuId"] as Number).toLong()
            val skuName = map["skuName"] as String
            val attrList = map["attributes"] as List<Map<String, String>>
            val price = (map["price"] as Number).toLong()
            val merchantCode = map["merchantCode"] as? String
            val barcode = map["barcode"] as? String
            return SkuSnapshot(
                skuId = SkuId(skuId),
                skuName = skuName,
                attributes = attrList.map { Attribute(it["key"]!!, it["value"]!!) },
                price = Price.ofFen(price),
                merchantCode = merchantCode,
                barcode = barcode,
            )
        }
    }
}
