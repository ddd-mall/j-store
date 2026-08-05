package com.jstore.goods.domain.commodity.snapshot

import com.jstore.goods.domain.commodity.SpuId

/** Immutable historical snapshot store; deliberately not an aggregate repository. */
interface SpuSnapshotRepository {
    fun save(snapshot: SpuSnapshot): SpuSnapshot

    fun findById(id: SpuSnapshotId): SpuSnapshot?

    /** 根据 SPU ID 和版本号查询快照 */
    fun findBySpuIdAndVersion(spuId: SpuId, version: Long): SpuSnapshot?

    /** 查询某个 SPU 的最新快照 */
    fun findLatestBySpuId(spuId: SpuId): SpuSnapshot?
}
