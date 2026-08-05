package com.jstore.inventory.domain

import com.jstore.inventory.domain.persistence.StockPositionPO
import com.jstore.inventory.domain.persistence.StockPositionPOJpaRepository
import com.jstore.inventory.domain.persistence.StockReservationPO
import com.jstore.inventory.domain.persistence.StockReservationPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class StockPositionRepositoryImpl(private val jpa: StockPositionPOJpaRepository) :
    StockPositionRepository, StockPositionGuard {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: StockPosition): StockPosition = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: StockPositionId): StockPosition? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findBySkuAndNode(
        skuId: SkuId,
        nodeId: FulfillmentNodeId,
    ): StockPosition? = findById(StockPositionId("${skuId.value}@${nodeId.value}"))

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lock(keys: List<StockPositionId>): List<StockPosition> =
        if (keys.isEmpty()) emptyList()
        else jpa.findAllByIdForUpdate(keys.map { it.value }.distinct().sorted()).map(::toDomain)

    private fun toPO(position: StockPosition) =
        StockPositionPO(
            id = position.id.value,
            skuId = position.skuId.value,
            fulfillmentNodeId = position.fulfillmentNodeId.value,
            onHand = position.onHand,
            reserved = position.reserved,
            safetyStock = position.safetyStock,
            isolatedQuantity = position.isolatedQuantity,
            sourceVersion = position.sourceVersion,
            persistenceVersion = position.persistenceVersion,
        )

    private fun toDomain(po: StockPositionPO) =
        StockPosition(
            StockPositionId(po.id),
            SkuId(po.skuId),
            FulfillmentNodeId(po.fulfillmentNodeId),
            po.onHand,
            po.reserved,
            po.safetyStock,
            po.isolatedQuantity,
            po.sourceVersion,
            po.persistenceVersion,
        )
}

@Repository
class StockReservationRepositoryImpl(private val jpa: StockReservationPOJpaRepository) :
    StockReservationRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: StockReservation): StockReservation = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: StockReservationId): StockReservation? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByBusinessKey(businessKey: String): StockReservation? =
        jpa.findByBusinessKey(businessKey)?.let(::toDomain)

    override fun findByOrderId(orderId: Long): List<StockReservation> =
        jpa.findAllByOrderIdOrderBySkuIdAsc(orderId).map(::toDomain)

    private fun toPO(record: StockReservation) =
        StockReservationPO(
            id = record.id.value,
            businessKey = record.businessKey,
            orderId = record.orderId,
            saleAuthorizationId = record.saleAuthorizationId,
            skuId = record.skuId.value,
            fulfillmentNodeId = record.fulfillmentNodeId.value,
            quantity = record.quantity,
            expiresAt = record.expiresAt,
            status = record.status,
            persistenceVersion = record.persistenceVersion,
        )

    private fun toDomain(po: StockReservationPO) =
        StockReservation(
            StockReservationId(po.id),
            po.businessKey,
            po.orderId,
            po.saleAuthorizationId,
            SkuId(po.skuId),
            FulfillmentNodeId(po.fulfillmentNodeId),
            po.quantity,
            po.expiresAt,
            po.status,
            po.persistenceVersion,
        )
}
