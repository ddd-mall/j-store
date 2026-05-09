package com.jstore.goods.domain.commodity.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SpuSnapshotPOJpaRepository : JpaRepository<SpuSnapshotPO, Long> {

    fun findBySpuIdAndSnapshotVersion(spuId: Long, snapshotVersion: Long): SpuSnapshotPO?

    fun findFirstBySpuIdOrderBySnapshotVersionDesc(spuId: Long): SpuSnapshotPO?
}
