package com.jstore.order.domain.aftersale.persistence

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "after_sales")
class AfterSalePO(
    @Id var id: Long = 0,
    @Column(name = "order_id") var orderId: Long = 0,
    @Column(name = "applicant_id") var applicantId: Long = 0,
    @Column(name = "merchant_id") var merchantId: Long = 0,
    var status: String = "REQUESTED",
    @Column(name = "reason_category") var reasonCategory: String = "OTHER",
    @Column(name = "reason_description") var reasonDescription: String = "",
    @Column(name = "fulfillment_status") var fulfillmentStatus: String = "UNFULFILLED",
    @Column(name = "require_return") var requireReturn: Boolean = false,
    @Column(name = "reviewer_id") var reviewerId: Long? = null,
    @Column(name = "reviewed_at") var reviewedAt: LocalDateTime? = null,
    @Column(name = "rejection_reason") var rejectionReason: String? = null,
    @Column(name = "cancelled_at") var cancelledAt: LocalDateTime? = null,
    @Column(name = "return_received_at") var returnReceivedAt: LocalDateTime? = null,
    @Column(name = "refund_id") var refundId: String? = null,
    @Column(name = "refund_failure_reason") var refundFailureReason: String? = null,
    @Column(name = "create_time") var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time") var updateTime: LocalDateTime = LocalDateTime.now(),
    @Version var version: Long = 0,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "after_sale_id")
    var items: MutableList<AfterSaleItemPO> = mutableListOf(),
)

@Entity
@Table(name = "after_sale_items")
class AfterSaleItemPO(
    @Id var id: Long = 0,
    @Column(name = "after_sale_id", insertable = false, updatable = false)
    var afterSaleId: Long = 0,
    @Column(name = "order_id") var orderId: Long = 0,
    @Column(name = "order_item_id") var orderItemId: Long = 0,
    @Column(name = "requested_quantity") var requestedQuantity: Int = 0,
    @Column(name = "requested_amount") var requestedAmount: BigDecimal = BigDecimal.ZERO,
    var currency: String = "CNY",
    @Column(name = "eligible_quantity") var eligibleQuantity: Int = 0,
    @Column(name = "eligible_amount") var eligibleAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "sku_id") var skuId: Long = 0,
    @Column(name = "spu_id") var spuId: Long = 0,
    @Column(name = "goods_name") var goodsName: String = "",
    @Column(name = "sku_description") var skuDescription: String = "",
)

@Entity
@Table(name = "after_sale_capacities")
class AfterSaleCapacityPO(
    @Id @Column(name = "order_item_id") var orderItemId: Long = 0,
    @Column(name = "order_id") var orderId: Long = 0,
    @Column(name = "quantity_ceiling") var quantityCeiling: Int = 0,
    @Column(name = "amount_ceiling") var amountCeiling: BigDecimal = BigDecimal.ZERO,
    @Column(name = "requested_quantity") var requestedQuantity: Int = 0,
    @Column(name = "requested_amount") var requestedAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "approved_quantity") var approvedQuantity: Int = 0,
    @Column(name = "approved_amount") var approvedAmount: BigDecimal = BigDecimal.ZERO,
    @Version var version: Long = 0,
)

@Entity
@Table(
    name = "after_sale_command_receipts",
    uniqueConstraints =
        [UniqueConstraint(columnNames = ["actor_id", "command_type", "idempotency_key"])],
)
class AfterSaleCommandReceiptPO(
    @Id var id: Long = 0,
    @Column(name = "actor_id") var actorId: Long = 0,
    @Column(name = "command_type") var commandType: String = "CREATE",
    @Column(name = "idempotency_key") var idempotencyKey: String = "",
    @Column(name = "request_hash") var requestHash: String = "",
    @Column(name = "after_sale_id") var afterSaleId: Long = 0,
    @Column(name = "result_status") var resultStatus: String = "REQUESTED",
    @Column(name = "created_at") var createdAt: LocalDateTime = LocalDateTime.now(),
)
