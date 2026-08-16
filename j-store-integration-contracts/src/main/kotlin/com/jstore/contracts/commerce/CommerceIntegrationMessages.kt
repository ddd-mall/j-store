/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.contracts.commerce

import com.jstore.messaging.IntegrationCommand
import com.jstore.messaging.IntegrationEvent
import com.jstore.messaging.IntegrationMessageType
import com.jstore.messaging.stableIntegrationMessageId
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
) : IntegrationCommand {
    override val acceptBefore: Instant?
        get() = null
}

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

data class ContractPaymentAllocation(
    val orderPlanId: Long,
    val orderId: Long,
    val merchantId: Long,
    val amountFen: Long,
)

data class ContractAddressComponent(
    val code: String,
    val levelDepth: Int,
    val levelName: String,
    val names: Map<String, String>,
    val defaultLocale: String,
)

data class ContractShippingAddress(
    val countryCode: String,
    val components: List<ContractAddressComponent>,
)

data class ContractTradeOrderItem(
    val spuId: Long,
    val skuId: Long,
    val offerId: Long,
    val storeId: Long,
    val offerVersion: Long,
    val fulfillmentNodeId: String,
    val channelId: String,
    val goodsName: String,
    val skuDescription: String,
    val quantity: Int,
    val unitPriceFen: Long,
    val catalogSnapshotVersion: Long,
)

@IntegrationMessageType("order.create-from-trade", 1)
data class CreateOrderFromTradeIntegrationCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val planDigest: String,
    val merchantId: Long,
    val buyerId: Long,
    val buyerName: String,
    val buyerPhone: String?,
    val recipient: ContractRecipient,
    val shippingAddress: ContractShippingAddress,
    val items: List<ContractTradeOrderItem>,
    val payableAmountFen: Long,
    val currency: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("order.create-from-trade", 1, orderPlanId, planDigest),
        "order.create-from-trade",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "order.commands",
    )

@IntegrationMessageType("order.created-from-trade", 1)
data class OrderCreatedFromTradeIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("order.created-from-trade", 1, orderPlanId, orderId.toString()),
        "order.created-from-trade",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("order.creation-rejected-from-trade", 1)
data class OrderCreationRejectedFromTradeIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("order.creation-rejected-from-trade", 1, orderPlanId, sourceMessageId),
        "order.creation-rejected-from-trade",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("order.cancel-from-trade", 1)
data class CancelOrderFromTradeIntegrationCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("order.cancel-from-trade", 1, orderPlanId, sourceMessageId),
        "order.cancel-from-trade",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "order.commands",
    )

@IntegrationMessageType("payment.installment.prepare", 1)
data class PreparePaymentInstallmentCommand(
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val amountFen: Long,
    val currency: String,
    val allocations: List<ContractPaymentAllocation>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
    override val acceptBefore: Instant,
    val expiresAt: Instant,
) :
    CommerceIntegrationCommand(
        id("payment.installment.prepare", 1, settlementPlanId, installmentId),
        "payment.installment.prepare",
        1,
        occurredAtValue,
        tradeId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "payment.commands",
    )

@IntegrationMessageType("payment.installment.cancel", 1)
data class CancelPaymentInstallmentCommand(
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("payment.installment.cancel", 1, settlementPlanId, "$installmentId:$sourceMessageId"),
        "payment.installment.cancel",
        1,
        occurredAtValue,
        tradeId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "payment.commands",
    )

@IntegrationMessageType("payment.installment.cancellation-confirmed", 1)
data class PaymentCancellationConfirmedIntegrationEvent(
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val paymentId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.installment.cancellation-confirmed", 1, settlementPlanId, installmentId),
        "payment.installment.cancellation-confirmed",
        1,
        occurredAtValue,
        tradeId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("payment.installment.prepared", 1)
data class PaymentPreparedIntegrationEvent(
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val paymentId: Long,
    val amountFen: Long,
    val currency: String,
    val acceptBefore: Instant,
    val expiresAt: Instant,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.installment.prepared", 1, settlementPlanId, installmentId),
        "payment.installment.prepared",
        1,
        occurredAtValue,
        tradeId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("payment.installment.rejected", 1)
data class PaymentPreparationRejectedIntegrationEvent(
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val paymentId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.installment.rejected", 1, settlementPlanId, installmentId),
        "payment.installment.rejected",
        1,
        occurredAtValue,
        tradeId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("payment.installment.uncertain", 1)
data class PaymentPreparationUncertainIntegrationEvent(
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val paymentId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("payment.installment.uncertain", 1, settlementPlanId, installmentId),
        "payment.installment.uncertain",
        1,
        occurredAtValue,
        tradeId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("sale.authorize", 1)
data class AuthorizeSaleCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val merchantId: Long,
    val items: List<ContractSaleItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
    override val acceptBefore: Instant? = null,
) :
    CommerceIntegrationCommand(
        id("sale.authorize", 1, orderPlanId, sourceMessageId),
        "sale.authorize",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "store.commands",
    )

@IntegrationMessageType("sale.authorization.release", 1)
data class ReleaseSaleAuthorizationCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val authorizationIds: List<String>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("sale.authorization.release", 1, orderPlanId, sourceMessageId),
        "sale.authorization.release",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "store.commands",
    )

@IntegrationMessageType("sale.authorized", 1)
data class SaleAuthorizedIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val items: List<ContractAuthorizedSaleItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("sale.authorized", 1, orderPlanId, sourceMessageId),
        "sale.authorized",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("sale.authorization-failed", 1)
data class SaleAuthorizationFailedIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("sale.authorization-failed", 1, orderPlanId, sourceMessageId),
        "sale.authorization-failed",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("inventory.reserve", 1)
data class ReserveInventoryCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val items: List<ContractAuthorizedSaleItem>,
    val sourceMessageId: String,
    val merchantId: Long,
    val occurredAtValue: Instant,
    override val acceptBefore: Instant? = null,
) :
    CommerceIntegrationCommand(
        messageId = id("inventory.reserve", 1, orderPlanId, sourceMessageId),
        messageName = "inventory.reserve",
        messageVersion = 1,
        occurredAt = occurredAtValue,
        partitionKey = orderPlanId.toString(),
        correlationId = tradeId.toString(),
        causationId = sourceMessageId,
        tenantId = merchantId.toString(),
        destination = "inventory.commands",
    )

@IntegrationMessageType("inventory.confirm", 1)
data class ConfirmInventoryCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val items: List<ContractItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("inventory.confirm", 1, orderPlanId, sourceMessageId),
        "inventory.confirm",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "inventory.commands",
    )

@IntegrationMessageType("inventory.release", 1)
data class ReleaseInventoryCommand(
    val tradeId: Long,
    val orderPlanId: Long,
    val items: List<ContractItem>,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("inventory.release", 1, orderPlanId, sourceMessageId),
        "inventory.release",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "inventory.commands",
    )

@IntegrationMessageType("inventory.reserved", 1)
data class InventoryReservedIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val authorizationIds: List<String>,
    val reservationIds: List<String>,
    val reservationExpiresAt: Instant,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("inventory.reserved", 1, orderPlanId, sourceMessageId),
        "inventory.reserved",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("inventory.reservation-failed", 1)
data class InventoryReservationFailedIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val authorizationIds: List<String>,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("inventory.reservation-failed", 1, orderPlanId, sourceMessageId),
        "inventory.reservation-failed",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("order.cancelled.integration", 1)
data class OrderCancelledIntegrationEvent(
    val tradeId: Long,
    val orderPlanId: Long,
    val orderId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("order.cancelled.integration", 1, orderPlanId, sourceMessageId),
        "order.cancelled.integration",
        1,
        occurredAtValue,
        orderPlanId.toString(),
        tradeId.toString(),
        sourceMessageId,
        null,
        "trade.events",
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
        id("warehouse.physical-stock-changed", 1, skuId, sourceMessageId),
        "warehouse.physical-stock-changed",
        1,
        occurredAtValue,
        "$skuId@$fulfillmentNodeId",
        "$skuId@$fulfillmentNodeId",
        sourceMessageId,
        null,
        "inventory.events",
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
        id("payment.request-refund", 1, afterSaleId, sourceMessageId),
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
        id("payment.captured.integration", 1, orderId, sourceMessageId),
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
        id("payment.refund-succeeded.integration", 1, afterSaleId, sourceMessageId),
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
        id("payment.refund-failed.integration", 1, afterSaleId, sourceMessageId),
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
        id("fulfillment.create-for-order", 1, orderId, sourceMessageId),
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
        id("fulfillment.prepared.integration", 1, orderId, sourceMessageId),
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
        id("fulfillment.dispatched.integration", 1, orderId, sourceMessageId),
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
        id("fulfillment.delivered.integration", 1, orderId, sourceMessageId),
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
        id("order.completed.integration", 1, orderId, sourceMessageId),
        "order.completed.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "accounting.events",
    )

private fun id(name: String, version: Int, key: Long, sourceMessageId: String): String =
    stableIntegrationMessageId(name, version, sourceMessageId, key.toString())
