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
package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderSaleAuthorizedEvent
import com.jstore.order.domain.order.event.OrderStockConfirmedEvent
import java.time.Instant
import java.time.LocalDateTime

class OrderImpl(
    override val id: OrderId,
    override val merchantId: MerchantId,
    override val buyerInfo: UserInfo,
    private val _items: MutableList<OrderItem>,
    override val recipientInfo: RecipientInfo,
    private var _tradeStatus: TradeStatus,
    private var _paymentStatus: PaymentStatus,
    private var _fulfillmentStatus: FulfillmentStatus,
    private var _commitmentStatus: CommitmentStatus =
        if (_tradeStatus == TradeStatus.CREATED) CommitmentStatus.PENDING_OFFER
        else CommitmentStatus.CONFIRMED,
    private val _saleAuthorizations: MutableList<SaleAuthorizationRef> = mutableListOf(),
    override val amountSnapshot: OrderAmountSnapshot,
    private var _paidAmount: Price = Price.ZERO,
    private var _refundedAmount: Price = Price.ZERO,
    private var _paymentReference: String? = null,
    private var _fulfillmentReference: String? = null,
    private val refundFacts: MutableList<RefundFact> = mutableListOf(),
    override val createTime: LocalDateTime = LocalDateTime.now(),
    private var _updateTime: LocalDateTime = LocalDateTime.now(),
) : EventRecordingAggregateRoot<OrderId>(), Order {
    override val items: List<OrderItem>
        get() = _items.toList()

    override val tradeStatus: TradeStatus
        get() = _tradeStatus

    override val paymentStatus: PaymentStatus
        get() = _paymentStatus

    override val fulfillmentStatus: FulfillmentStatus
        get() = _fulfillmentStatus

    override val commitmentStatus: CommitmentStatus
        get() = _commitmentStatus

    override val saleAuthorizations: List<SaleAuthorizationRef>
        get() = _saleAuthorizations.toList()

    override val paidAmount: Price
        get() = _paidAmount

    override val refundedAmount: Price
        get() = _refundedAmount

    override val paymentReference: String?
        get() = _paymentReference

    override val fulfillmentReference: String?
        get() = _fulfillmentReference

    override val successfulRefundFacts: List<RefundFact>
        get() = refundFacts.toList()

    override val updateTime: LocalDateTime
        get() = _updateTime

    init {
        require(_items.isNotEmpty())
        require(amountSnapshot.itemsSubtotal == Price.sumOf(_items.map { it.purchasedAmount }))
        require(_refundedAmount == Price.sumOf(_items.map { it.refundedAmount }))
        require(_refundedAmount <= _paidAmount)
        require(_paidAmount <= amountSnapshot.payableAmount)
        require((_paymentReference == null) == (_paidAmount == Price.ZERO))
    }

    internal fun recordCreated() {
        raise(
            OrderCreatedEvent(
                orderId = id,
                merchantId = merchantId,
                payableAmount = amountSnapshot.payableAmount,
                currency = amountSnapshot.currency,
                items = orderItemSnapshots(),
            )
        )
    }

    override fun recordSaleAuthorized(
        authorizations: List<SaleAuthorizationRef>
    ): Result<Unit, BusinessError> =
        transition(
            _tradeStatus == TradeStatus.CREATED &&
                _commitmentStatus == CommitmentStatus.PENDING_OFFER &&
                authorizations.isNotEmpty() &&
                authorizations.map { it.authorizationId }.distinct().size == authorizations.size &&
                authorizations.map { it.offerId }.toSet() == _items.map { it.offerId }.toSet(),
            "登记销售授权",
        ) {
            _saleAuthorizations.clear()
            _saleAuthorizations.addAll(authorizations)
            _commitmentStatus = CommitmentStatus.OFFER_AUTHORIZED
            raise(
                OrderSaleAuthorizedEvent(
                    orderId = id,
                    merchantId = merchantId,
                    authorizations = authorizations,
                    items = orderItemSnapshots(),
                )
            )
        }

    override fun markSaleAuthorizationFailed(reason: String): Result<Unit, BusinessError> =
        transition(
            _tradeStatus == TradeStatus.CREATED &&
                _commitmentStatus == CommitmentStatus.PENDING_OFFER,
            "销售授权失败",
        ) {
            _commitmentStatus = CommitmentStatus.FAILED
            _tradeStatus = TradeStatus.CLOSED
            mutableItems().forEach { it.markCanceled() }
            raise(OrderCancelledEvent(id, reason))
        }

    override fun confirmStock(): Result<Unit, BusinessError> =
        transition(
            _tradeStatus == TradeStatus.CREATED &&
                _commitmentStatus == CommitmentStatus.OFFER_AUTHORIZED &&
                unpaid(),
            "确认库存",
        ) {
            _commitmentStatus = CommitmentStatus.CONFIRMED
            _tradeStatus = TradeStatus.ACTIVE
            raise(
                OrderStockConfirmedEvent(
                    orderId = id,
                    merchantId = merchantId,
                    payableAmount = amountSnapshot.payableAmount,
                    currency = amountSnapshot.currency,
                )
            )
        }

    override fun markStockInsufficient(reason: String): Result<Unit, BusinessError> =
        transition(
            _tradeStatus == TradeStatus.CREATED &&
                _commitmentStatus == CommitmentStatus.OFFER_AUTHORIZED &&
                unpaid(),
            "库存不足取消",
        ) {
            _commitmentStatus = CommitmentStatus.FAILED
            _tradeStatus = TradeStatus.CLOSED
            mutableItems().forEach { it.markCanceled() }
            raise(OrderCancelledEvent(id, reason))
        }

    override fun recordPaymentCaptured(
        paymentReference: String,
        capturedAmount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        if (_paymentReference == paymentReference) return Success(false)
        if (_paymentReference != null) return Failure(OrderErrors.PAYMENT_REFERENCE_CONFLICT)
        if (_tradeStatus != TradeStatus.ACTIVE || _paymentStatus != PaymentStatus.UNPAID) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前订单不允许登记支付成功"))
        }
        if (
            paymentReference.isBlank() ||
                currency != amountSnapshot.currency ||
                capturedAmount != amountSnapshot.payableAmount
        ) {
            return Failure(OrderErrors.PAYMENT_FACT_INVALID)
        }

        _paymentReference = paymentReference
        _paidAmount = capturedAmount
        _paymentStatus = PaymentStatus.PAID
        touch()
        raise(
            OrderPaidEvent(
                orderId = id,
                merchantId = merchantId,
                paymentReference = paymentReference,
                paidAmount = capturedAmount,
                currency = currency,
                items = orderItemSnapshots(),
                occurredAt = occurredAt,
            )
        )
        return Success(true)
    }

    override fun recordFulfillmentPrepared(
        fulfillmentReference: String
    ): Result<Boolean, BusinessError> {
        if (
            _fulfillmentReference == fulfillmentReference &&
                _fulfillmentStatus != FulfillmentStatus.UNFULFILLED
        ) {
            return Success(false)
        }
        if (
            _paymentStatus != PaymentStatus.PAID ||
                _fulfillmentStatus != FulfillmentStatus.UNFULFILLED
        ) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前订单不允许进入待发货"))
        }
        if (fulfillmentReference.isBlank()) return Failure(OrderErrors.FULFILLMENT_FACT_INVALID)

        _fulfillmentReference = fulfillmentReference
        _fulfillmentStatus = FulfillmentStatus.PENDING_SHIPMENT
        mutableItems().forEach { it.markWaitingShipment() }
        touch()
        return Success(true)
    }

    override fun recordShipmentDispatched(
        fulfillmentReference: String
    ): Result<Boolean, BusinessError> {
        if (
            _fulfillmentReference == fulfillmentReference &&
                _fulfillmentStatus == FulfillmentStatus.SHIPPED
        ) {
            return Success(false)
        }
        if (
            _fulfillmentReference != fulfillmentReference ||
                _fulfillmentStatus != FulfillmentStatus.PENDING_SHIPMENT
        ) {
            return Failure(OrderErrors.FULFILLMENT_FACT_INVALID)
        }

        _fulfillmentStatus = FulfillmentStatus.SHIPPED
        mutableItems().forEach { it.markShipping() }
        touch()
        return Success(true)
    }

    override fun recordShipmentDelivered(
        fulfillmentReference: String
    ): Result<Boolean, BusinessError> {
        if (
            _fulfillmentReference == fulfillmentReference &&
                _fulfillmentStatus == FulfillmentStatus.DELIVERED
        ) {
            return Success(false)
        }
        if (
            _fulfillmentReference != fulfillmentReference ||
                _fulfillmentStatus != FulfillmentStatus.SHIPPED
        ) {
            return Failure(OrderErrors.FULFILLMENT_FACT_INVALID)
        }

        _fulfillmentStatus = FulfillmentStatus.DELIVERED
        mutableItems().forEach { it.markDelivered() }
        touch()
        return Success(true)
    }

    override fun complete(): Result<Unit, BusinessError> =
        transition(
            _tradeStatus == TradeStatus.ACTIVE &&
                _paymentStatus in setOf(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED) &&
                _fulfillmentStatus == FulfillmentStatus.DELIVERED,
            "完成订单",
        ) {
            _tradeStatus = TradeStatus.COMPLETED
            raise(OrderCompletedEvent(id))
        }

    override fun cancel(reason: CancellationReason): Result<Unit, BusinessError> =
        transition(
            (_tradeStatus == TradeStatus.CREATED || _tradeStatus == TradeStatus.ACTIVE) && unpaid(),
            "取消订单",
        ) {
            _tradeStatus = TradeStatus.CLOSED
            mutableItems().forEach { it.markCanceled() }
            raise(OrderCancelledEvent(id, reason.description))
        }

    override fun refundEligibility(): Result<RefundEligibility, BusinessError> {
        if (
            _paymentStatus !in setOf(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED) ||
                _tradeStatus !in setOf(TradeStatus.ACTIVE, TradeStatus.COMPLETED)
        ) {
            return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        }
        val refundable =
            _items
                .filter { it.refundableQuantity > 0 && it.refundableAmount > Price.ZERO }
                .map {
                    RefundableOrderItem(
                        orderItemId = it.id,
                        purchasedQuantity = it.quantity,
                        purchasedAmount = it.purchasedAmount,
                        refundedQuantity = it.refundedQuantity,
                        refundedAmount = it.refundedAmount,
                        refundableQuantity = it.refundableQuantity,
                        refundableAmount = it.refundableAmount,
                        skuId = it.skuId,
                        spuId = it.spuId,
                        goodsName = it.goodsName,
                        skuDescription = it.skuDescription,
                    )
                }
        if (refundable.isEmpty()) return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        return Success(
            RefundEligibility(
                orderId = id,
                merchantId = merchantId,
                buyerId = buyerInfo.uid,
                paymentStatus = _paymentStatus,
                tradeStatus = _tradeStatus,
                fulfillmentStatus = _fulfillmentStatus,
                paidAmount = _paidAmount,
                totalRefundedAmount = _refundedAmount,
                currency = amountSnapshot.currency,
                items = refundable,
            )
        )
    }

    override fun recordRefundSucceeded(
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ): Result<RefundProjectionResult, BusinessError> {
        if (refundFacts.any { it.refundId == refundId })
            return Success(RefundProjectionResult(false))
        if (
            refundId.isBlank() ||
                items.isEmpty() ||
                items.map { it.orderItemId }.toSet().size != items.size
        ) {
            return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        }
        val byId = mutableItems().associateBy { it.id }
        for (item in items) {
            val target =
                byId[item.orderItemId] ?: return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
            if (
                item.quantity <= 0 ||
                    item.amount <= Price.ZERO ||
                    item.quantity > target.refundableQuantity ||
                    item.amount > target.refundableAmount
            ) {
                return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
            }
        }
        val amount = Price.sumOf(items.map { it.amount })
        if (_refundedAmount + amount > _paidAmount)
            return Failure(OrderErrors.REFUND_PROJECTION_INVALID)

        items.forEach { item ->
            byId.getValue(item.orderItemId).registerRefund(item.quantity, item.amount)
            refundFacts +=
                RefundFact(
                    refundId = refundId,
                    afterSaleId = afterSaleId,
                    orderItemId = item.orderItemId,
                    quantity = item.quantity,
                    amount = item.amount,
                    occurredAt = occurredAt,
                )
        }
        _refundedAmount += amount
        if (_refundedAmount == _paidAmount) {
            _paymentStatus = PaymentStatus.REFUNDED
            _tradeStatus = TradeStatus.CLOSED
        } else {
            _paymentStatus = PaymentStatus.PARTIALLY_REFUNDED
        }
        touch()
        return Success(RefundProjectionResult(true))
    }

    private fun unpaid(): Boolean =
        _paymentStatus == PaymentStatus.UNPAID && _paidAmount == Price.ZERO

    private inline fun transition(
        valid: Boolean,
        operation: String,
        action: () -> Unit,
    ): Result<Unit, BusinessError> {
        if (!valid) return Failure(OrderErrors.ILLEGAL_STATE.msg("$operation 不允许"))
        action()
        touch()
        return Success(Unit)
    }

    private fun mutableItems(): List<OrderItemImpl> = _items.map { it as OrderItemImpl }

    private fun orderItemSnapshots(): List<OrderItemSnapshot> = _items.map {
        OrderItemSnapshot(
            offerId = it.offerId,
            storeId = it.storeId,
            spuId = it.spuId,
            skuId = it.skuId,
            quantity = it.quantity,
            catalogSnapshotVersion = it.snapshotVersion,
            offerVersion = it.offerVersion,
            fulfillmentNodeId = it.fulfillmentNodeId,
            channelId = it.channelId,
            unitPrice = it.unitPrice,
        )
    }

    private fun touch() {
        _updateTime = LocalDateTime.now()
    }
}
