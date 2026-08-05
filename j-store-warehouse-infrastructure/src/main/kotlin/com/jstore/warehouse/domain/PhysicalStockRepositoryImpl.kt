package com.jstore.warehouse.domain

import com.jstore.warehouse.domain.persistence.PhysicalStockPO
import com.jstore.warehouse.domain.persistence.PhysicalStockPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class PhysicalStockRepositoryImpl(private val jpa: PhysicalStockPOJpaRepository) :
    PhysicalStockRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: PhysicalStock): PhysicalStock = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: PhysicalStockId): PhysicalStock? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    private fun toPO(stock: PhysicalStock) =
        PhysicalStockPO(
            stock.id.value,
            stock.skuId,
            stock.fulfillmentNodeId,
            stock.onHand,
            stock.sourceVersion,
            stock.persistenceVersion,
        )

    private fun toDomain(po: PhysicalStockPO) =
        PhysicalStock(
            PhysicalStockId(po.id),
            po.skuId,
            po.fulfillmentNodeId,
            po.onHand,
            po.sourceVersion,
            po.persistenceVersion,
        )
}
