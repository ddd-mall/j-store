package com.jstore.goods.domain.commodity.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * SPU JPA Repository
 */
@Repository
interface SpuPOJpaRepository : JpaRepository<SpuPO, Long> {

    fun findBySpuId(spuId: Long): SpuPO?

    fun findByStatus(status: String, pageable: Pageable): Page<SpuPO>

    fun findBySpuNameContaining(spuName: String, pageable: Pageable): Page<SpuPO>
}

