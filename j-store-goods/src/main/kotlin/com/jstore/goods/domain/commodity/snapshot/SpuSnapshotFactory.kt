package com.jstore.goods.domain.commodity.snapshot

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.Spu
import java.time.LocalDateTime

interface SpuSnapshotFactory {
    fun createSnapshot(spu: Spu): SpuSnapshot
}

class SpuSnapshotFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
) : SpuSnapshotFactory {

    override fun createSnapshot(spu: Spu): SpuSnapshot {
        return SpuSnapshot(
            id = SpuSnapshotId(snowFlakSequence.nextId()),
            merchantId = spu.merchantId,
            spuId = spu.id,
            snapshotVersion = spu.version,
            spuName = spu.name,
            description = spu.description,
            skuSnapshots = spu.skus.map { sku ->
                SkuSnapshot(
                    skuId = sku.id,
                    skuName = sku.skuName,
                    attributes = sku.attributes.toList(),
                    price = sku.price,
                    merchantCode = sku.merchantCode,
                    barcode = sku.barcode,
                )
            },
            createdAt = LocalDateTime.now(),
        )
    }
}
