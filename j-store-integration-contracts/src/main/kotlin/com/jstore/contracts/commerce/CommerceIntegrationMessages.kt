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

@IntegrationMessageType("trade.start", 1)
data class StartTradeProcessCommand(
    val orderId: Long,
    val merchantId: Long,
    val items: List<ContractSaleItem>,
    val payableAmountFen: Long,
    val currency: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationCommand(
        id("trade.start", 1, orderId, sourceMessageId),
        "trade.start",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        merchantId.toString(),
        "trade.commands",
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
    override val acceptBefore: Instant? = null,
) :
    CommerceIntegrationCommand(
        id("sale.authorize", 1, orderId, sourceMessageId),
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
        id("sale.authorization.release", 1, orderId, sourceMessageId),
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
        id("sale.authorized", 1, orderId, sourceMessageId),
        "sale.authorized",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("sale.authorization-failed", 1)
data class SaleAuthorizationFailedIntegrationEvent(
    val orderId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("sale.authorization-failed", 1, orderId, sourceMessageId),
        "sale.authorization-failed",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("inventory.reserve", 1)
data class ReserveInventoryCommand(
    val orderId: Long,
    val items: List<ContractAuthorizedSaleItem>,
    val sourceMessageId: String,
    val merchantId: Long,
    val occurredAtValue: Instant,
    override val acceptBefore: Instant? = null,
) :
    CommerceIntegrationCommand(
        messageId = id("inventory.reserve", 1, orderId, sourceMessageId),
        messageName = "inventory.reserve",
        messageVersion = 1,
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
        id("inventory.confirm", 1, orderId, sourceMessageId),
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
        id("inventory.release", 1, orderId, sourceMessageId),
        "inventory.release",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "inventory.commands",
    )

@IntegrationMessageType("inventory.reserved", 1)
data class InventoryReservedIntegrationEvent(
    val orderId: Long,
    val authorizationIds: List<String>,
    val reservationIds: List<String>,
    val reservationExpiresAt: Instant,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("inventory.reserved", 1, orderId, sourceMessageId),
        "inventory.reserved",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("inventory.reservation-failed", 1)
data class InventoryReservationFailedIntegrationEvent(
    val orderId: Long,
    val authorizationIds: List<String>,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("inventory.reservation-failed", 1, orderId, sourceMessageId),
        "inventory.reservation-failed",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("trade.commitment-confirmed", 1)
data class TradeCommitmentConfirmedIntegrationEvent(
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("trade.commitment-confirmed", 1, orderId, sourceMessageId),
        "trade.commitment-confirmed",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("trade.commitment-failed", 1)
data class TradeCommitmentFailedIntegrationEvent(
    val orderId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("trade.commitment-failed", 1, orderId, sourceMessageId),
        "trade.commitment-failed",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "order.events",
    )

@IntegrationMessageType("order.cancelled.integration", 1)
data class OrderCancelledIntegrationEvent(
    val orderId: Long,
    val reason: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("order.cancelled.integration", 1, orderId, sourceMessageId),
        "order.cancelled.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
        sourceMessageId,
        null,
        "trade.events",
    )

@IntegrationMessageType("order.paid.integration", 1)
data class OrderPaidIntegrationEvent(
    val orderId: Long,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
) :
    CommerceIntegrationEvent(
        id("order.paid.integration", 1, orderId, sourceMessageId),
        "order.paid.integration",
        1,
        occurredAtValue,
        orderId.toString(),
        orderId.toString(),
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

@IntegrationMessageType("payment.create-for-order", 1)
data class CreatePaymentForOrderCommand(
    val orderId: Long,
    val merchantId: Long,
    val payableAmountFen: Long,
    val currency: String,
    val sourceMessageId: String,
    val occurredAtValue: Instant,
    override val acceptBefore: Instant? = null,
) :
    CommerceIntegrationCommand(
        id("payment.create-for-order", 1, orderId, sourceMessageId),
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
