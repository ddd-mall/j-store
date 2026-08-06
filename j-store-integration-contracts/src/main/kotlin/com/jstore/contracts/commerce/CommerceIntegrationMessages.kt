package com.jstore.contracts.commerce

import com.jstore.common.framework.event.outbox.IntegrationMessageType
import com.jstore.common.framework.messaging.IntegrationCommand
import com.jstore.common.framework.messaging.IntegrationEvent
import com.jstore.common.framework.messaging.stableIntegrationMessageId
import java.time.Instant

abstract class CommerceIntegrationCommand(
    final override val messageId: String,
    final override val messageName: String,
    final override val messageVersion: Int,
    final override val occurredAt: Instant,
    final override val partitionKey: String,
    final override val correlationId: String,
    final override val causationId: String?,
    final override val tenantId: String?,
    final override val destination: String,
) : IntegrationCommand

abstract class CommerceIntegrationEvent(
    final override val messageId: String,
    final override val messageName: String,
    final override val messageVersion: Int,
    final override val occurredAt: Instant,
    final override val partitionKey: String,
    final override val correlationId: String,
    final override val causationId: String?,
    final override val tenantId: String?,
    final override val destination: String,
) : IntegrationEvent

data class ContractItem(
    val skuId: Long,
    val quantity: Int,
    val amountFen: Long? = null,
    val orderItemId: Long? = null,
)

data class ContractSaleItem(
    val offerId: Long,
    val storeId: Long,
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
    val offerVersion: Long,
    val fulfillmentNodeId: String,
    val channelId: String,
    val unitPriceFen: Long,
)

data class ContractAuthorizedSaleItem(
    val authorizationId: String,
    val offerId: Long,
    val skuId: Long,
    val quantity: Int,
    val fulfillmentNodeId: String,
    val expiresAt: Instant,
)

data class ContractRecipient(
    val name: String,
    val phone: String?,
    val email: String?,
    val countryCode: String,
    val districtCode: String,
    val detailAddress: String?,
    val postalCode: String? = null,
    val customsFields: Map<String, String> = emptyMap(),
)

@IntegrationMessageType("sale.authorize", 1)
data class AuthorizeSaleCommand(
    val orderId: Long,
    val merchantId: Long,
    val items: List<ContractSaleItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("sale.authorize", orderId, occurredAtValue),
        "sale.authorize",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "store.commands",
    )

@IntegrationMessageType("sale.authorization.release", 1)
data class ReleaseSaleAuthorizationCommand(
    val orderId: Long,
    val authorizationIds: List<String>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("sale.authorization.release", orderId, occurredAtValue),
        "sale.authorization.release",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "store.commands",
    )

@IntegrationMessageType("sale.authorized", 1)
data class SaleAuthorizedIntegrationEvent(
    val orderId: Long,
    val items: List<ContractAuthorizedSaleItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("sale.authorized", orderId, occurredAtValue),
        "sale.authorized",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("sale.authorization-failed", 1)
data class SaleAuthorizationFailedIntegrationEvent(
    val orderId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("sale.authorization-failed", orderId, occurredAtValue),
        "sale.authorization-failed",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("inventory.reserve", 3)
data class ReserveInventoryCommand(
    val orderId: Long,
    val items: List<ContractAuthorizedSaleItem>,
    val sourceMessageId: String,
    val merchantId: Long,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        messageId = id("inventory.reserve", orderId, occurredAtValue),
        messageName = "inventory.reserve",
        messageVersion = 3,
        occurredAt = occurredAtValue,
        partitionKey = orderId.toString(),
        correlationId = orderId.toString(),
        causationId = sourceMessageId,
        tenantId = merchantId.toString(),
        destination = "inventory.commands",
    )

@IntegrationMessageType("inventory.confirm", 1)
data class ConfirmInventoryCommand(
    val orderId: Long,
    val items: List<ContractItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("inventory.confirm", orderId, occurredAtValue),
        "inventory.confirm",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "inventory.commands",
    )

@IntegrationMessageType("inventory.release", 1)
data class ReleaseInventoryCommand(
    val orderId: Long,
    val items: List<ContractItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("inventory.release", orderId, occurredAtValue),
        "inventory.release",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "inventory.commands",
    )

@IntegrationMessageType("inventory.reserved", 2)
data class InventoryReservedIntegrationEvent(
    val orderId: Long,
    val authorizationIds: List<String>,
    val reservationIds: List<String>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("inventory.reserved", orderId, occurredAtValue),
        "inventory.reserved",
        2,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("inventory.reservation-failed", 2)
data class InventoryReservationFailedIntegrationEvent(
    val orderId: Long,
    val authorizationIds: List<String>,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("inventory.reservation-failed", orderId, occurredAtValue),
        "inventory.reservation-failed",
        2,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("warehouse.physical-stock-changed", 1)
data class PhysicalStockChangedIntegrationEvent(
    val skuId: Long,
    val fulfillmentNodeId: String,
    val onHand: Int,
    val sourceVersion: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("warehouse.physical-stock-changed", skuId, occurredAtValue),
        "warehouse.physical-stock-changed",
        1,
        occurredAtValue,
        "$skuId@$fulfillmentNodeId",
        "$skuId@$fulfillmentNodeId",
        sourceMessageId,
        null,
        "inventory.events",
    )

@IntegrationMessageType("payment.create-for-order", 1)
data class CreatePaymentForOrderCommand(
    val orderId: Long,
    val merchantId: Long,
    val payableAmountFen: Long,
    val currency: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("payment.create-for-order", orderId, occurredAtValue),
        "payment.create-for-order",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "payment.commands",
    )

data class ContractRefundItem(
    val orderItemId: Long,
    val skuId: Long,
    val quantity: Int,
    val amountFen: Long,
)

@IntegrationMessageType("payment.request-refund", 1)
data class RequestPaymentRefundCommand(
    val orderId: Long,
    val afterSaleId: Long,
    val items: List<ContractRefundItem>,
    val amountFen: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("payment.request-refund", afterSaleId, occurredAtValue),
        "payment.request-refund",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "payment.commands",
    )

@IntegrationMessageType("payment.captured.integration", 1)
data class PaymentCapturedIntegrationEvent(
    val paymentId: Long,
    val orderId: Long,
    val merchantId: Long,
    val providerTransactionId: String,
    val amountFen: Long,
    val currency: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.captured.integration", orderId, occurredAtValue),
        "payment.captured.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "commerce.events",
    )

@IntegrationMessageType("payment.refund-succeeded.integration", 1)
data class PaymentRefundSucceededIntegrationEvent(
    val paymentId: Long,
    val refundId: Long,
    val orderId: Long,
    val afterSaleId: Long,
    val merchantId: Long,
    val providerRefundId: String,
    val items: List<ContractRefundItem>,
    val amountFen: Long,
    val currency: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.refund-succeeded.integration", afterSaleId, occurredAtValue),
        "payment.refund-succeeded.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "commerce.events",
    )

@IntegrationMessageType("payment.refund-failed.integration", 1)
data class PaymentRefundFailedIntegrationEvent(
    val paymentId: Long,
    val refundId: Long,
    val orderId: Long,
    val afterSaleId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.refund-failed.integration", afterSaleId, occurredAtValue),
        "payment.refund-failed.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "commerce.events",
    )

@IntegrationMessageType("fulfillment.create-for-order", 1)
data class CreateFulfillmentForOrderCommand(
    val orderId: Long,
    val merchantId: Long,
    val recipient: ContractRecipient,
    val items: List<ContractItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("fulfillment.create-for-order", orderId, occurredAtValue),
        "fulfillment.create-for-order",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "fulfillment.commands",
    )

@IntegrationMessageType("fulfillment.prepared.integration", 1)
data class FulfillmentPreparedIntegrationEvent(
    val fulfillmentId: Long,
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("fulfillment.prepared.integration", orderId, occurredAtValue),
        "fulfillment.prepared.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("fulfillment.dispatched.integration", 1)
data class FulfillmentDispatchedIntegrationEvent(
    val fulfillmentId: Long,
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("fulfillment.dispatched.integration", orderId, occurredAtValue),
        "fulfillment.dispatched.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("fulfillment.delivered.integration", 1)
data class FulfillmentDeliveredIntegrationEvent(
    val fulfillmentId: Long,
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("fulfillment.delivered.integration", orderId, occurredAtValue),
        "fulfillment.delivered.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("order.completed.integration", 1)
data class OrderCompletedIntegrationEvent(
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("order.completed.integration", orderId, occurredAtValue),
        "order.completed.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "accounting.events",
    )

private fun id(name: String, key: Long, occurredAt: Instant): String =
    stableIntegrationMessageId(name, 1, key.toString(), occurredAt)
