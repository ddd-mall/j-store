package com.jstore.order.domain.aftersale.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AfterSalePOJpaRepository : JpaRepository<AfterSalePO, Long> {
    fun findByOrderIdOrderByCreateTimeDesc(orderId: Long): List<AfterSalePO>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AfterSalePO a where a.id=:id")
    fun findByIdForUpdate(@Param("id") id: Long): AfterSalePO?
}

interface AfterSaleCapacityPOJpaRepository : JpaRepository<AfterSaleCapacityPO, Long> {
    @Modifying
    @Query(
        value =
            "insert into after_sale_capacities(order_item_id,order_id,quantity_ceiling,amount_ceiling,requested_quantity,requested_amount,approved_quantity,approved_amount,version) values (:itemId,:orderId,:quantity,:amount,0,0,0,0,0) on conflict (order_item_id) do nothing",
        nativeQuery = true,
    )
    fun initialize(
        @Param("itemId") itemId: Long,
        @Param("orderId") orderId: Long,
        @Param("quantity") quantity: Int,
        @Param("amount") amount: java.math.BigDecimal,
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AfterSaleCapacityPO c where c.orderItemId in :ids order by c.orderItemId")
    fun lockAll(@Param("ids") ids: Collection<Long>): List<AfterSaleCapacityPO>
}

interface AfterSaleCommandReceiptPOJpaRepository : JpaRepository<AfterSaleCommandReceiptPO, Long> {
    @Modifying
    @Query(
        value =
            "insert into after_sale_command_receipts(id,actor_id,command_type,idempotency_key,request_hash,after_sale_id,result_status,created_at) values (:id,:actorId,:commandType,:idempotencyKey,:requestHash,:afterSaleId,:resultStatus,:createdAt) on conflict (actor_id,command_type,idempotency_key) do nothing",
        nativeQuery = true,
    )
    fun tryInsert(
        @Param("id") id: Long,
        @Param("actorId") actorId: Long,
        @Param("commandType") commandType: String,
        @Param("idempotencyKey") idempotencyKey: String,
        @Param("requestHash") requestHash: String,
        @Param("afterSaleId") afterSaleId: Long,
        @Param("resultStatus") resultStatus: String,
        @Param("createdAt") createdAt: java.time.LocalDateTime,
    ): Int

    fun findByActorIdAndCommandTypeAndIdempotencyKey(
        actorId: Long,
        commandType: String,
        idempotencyKey: String,
    ): AfterSaleCommandReceiptPO?
}
