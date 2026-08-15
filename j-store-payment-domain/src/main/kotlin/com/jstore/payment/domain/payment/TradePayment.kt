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
package com.jstore.payment.domain.payment

import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant

data class TradePaymentId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class PaymentAllocationSnapshot(
    val orderPlanId: Long,
    val orderId: Long,
    val merchantId: Long,
    val amount: Price,
) {
    init {
        require(orderPlanId > 0 && orderId > 0 && merchantId > 0 && amount > Price.ZERO)
    }
}

enum class TradePaymentStatus {
    PREPARING,
    READY,
    REJECTED,
    CAPTURED,
    UNCERTAIN,
    PREPARATION_CANCELLING,
    CANCELLING,
    CANCELLED,
    REFUNDED,
}

class TradePayment(
    override val id: TradePaymentId,
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val payableAmount: Price,
    val currency: String,
    allocations: List<PaymentAllocationSnapshot>,
    initialStatus: TradePaymentStatus,
    providerReference: String? = null,
    payAction: String? = null,
    providerAcceptedAt: Instant? = null,
    acceptBefore: Instant? = null,
    expiresAt: Instant? = null,
    failureReason: String? = null,
    cancellationReason: String? = null,
    val createdAt: Instant,
    val persistenceVersion: Long = 0,
) : AggregateRoot<TradePaymentId> {
    val allocations: List<PaymentAllocationSnapshot> = allocations.toList()
    private var _status: TradePaymentStatus = initialStatus
    val status: TradePaymentStatus
        get() = _status

    private var _providerReference: String? = providerReference
    val providerReference: String?
        get() = _providerReference

    private var _payAction: String? = payAction
    val payAction: String?
        get() = _payAction

    private var _providerAcceptedAt: Instant? = providerAcceptedAt
    val providerAcceptedAt: Instant?
        get() = _providerAcceptedAt

    private var _acceptBefore: Instant? = acceptBefore
    val acceptBefore: Instant?
        get() = _acceptBefore

    private var _expiresAt: Instant? = expiresAt
    val expiresAt: Instant?
        get() = _expiresAt

    private var _failureReason: String? = failureReason
    val failureReason: String?
        get() = _failureReason

    private var _cancellationReason: String? = cancellationReason
    val cancellationReason: String?
        get() = _cancellationReason

    init {
        require(tradeId > 0 && settlementPlanId > 0 && installmentId.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}")) && this.allocations.isNotEmpty())
        require(this.allocations.map { it.orderPlanId }.distinct().size == this.allocations.size)
        require(Price.sumOf(this.allocations.map { it.amount }) == payableAmount)
        require(providerReference == null || providerReference.isNotBlank())
        require(payAction == null || payAction.isPersistablePayAction())
        require(failureReason == null || failureReason.isPersistableFailureReason())
        require(cancellationReason == null || cancellationReason.isPersistableFailureReason())
        val hasProviderAcceptance =
            listOf(providerReference, payAction, providerAcceptedAt, acceptBefore, expiresAt).all {
                it != null
            }
        when (initialStatus) {
            TradePaymentStatus.READY,
            TradePaymentStatus.CAPTURED,
            TradePaymentStatus.REFUNDED ->
                require(
                    hasProviderAcceptance &&
                        requireNotNull(providerReference).isPersistableProviderReference()
                )
            TradePaymentStatus.PREPARING,
            TradePaymentStatus.REJECTED,
            TradePaymentStatus.UNCERTAIN -> require(!hasProviderAcceptance)
            TradePaymentStatus.PREPARATION_CANCELLING,
            TradePaymentStatus.CANCELLING,
            TradePaymentStatus.CANCELLED -> Unit
        }
    }

    fun matches(
        tradeId: Long,
        settlementPlanId: Long,
        installmentId: String,
        amount: Price,
        currency: String,
        allocations: List<PaymentAllocationSnapshot>,
    ) =
        this.tradeId == tradeId &&
            this.settlementPlanId == settlementPlanId &&
            this.installmentId == installmentId &&
            payableAmount == amount &&
            this.currency == currency &&
            this.allocations == allocations

    fun markReady(
        providerReference: String,
        payAction: String,
        acceptedAt: Instant,
        acceptBefore: Instant,
        expiresAt: Instant,
    ): Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (
            status == TradePaymentStatus.READY &&
                _providerReference == providerReference &&
                _payAction == payAction &&
                _providerAcceptedAt == acceptedAt &&
                _acceptBefore == acceptBefore &&
                _expiresAt == expiresAt
        ) {
            return Success(false)
        }
        if (
            status != TradePaymentStatus.PREPARING ||
                providerReference.isBlank() ||
                providerReference.length > MAX_PROVIDER_REFERENCE_LENGTH ||
                payAction.isBlank() ||
                payAction.length > MAX_PAY_ACTION_LENGTH ||
                acceptedAt > acceptBefore ||
                acceptedAt >= expiresAt
        ) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _providerReference = providerReference
        _payAction = payAction
        _providerAcceptedAt = acceptedAt
        _acceptBefore = acceptBefore
        _expiresAt = expiresAt
        _status = TradePaymentStatus.READY
        return Success(true)
    }

    fun recordLateProviderAcceptance(
        providerReference: String,
        payAction: String,
        acceptedAt: Instant,
        acceptBefore: Instant,
        expiresAt: Instant,
    ): Result<Boolean, com.jstore.common.errors.BusinessError> {
        val cancellableStates =
            setOf(
                TradePaymentStatus.PREPARATION_CANCELLING,
                TradePaymentStatus.CANCELLING,
                TradePaymentStatus.CANCELLED,
            )
        val sameAcceptance =
            _providerReference == providerReference &&
                _payAction == payAction &&
                _providerAcceptedAt == acceptedAt &&
                _acceptBefore == acceptBefore &&
                _expiresAt == expiresAt
        if (status == TradePaymentStatus.CANCELLED && sameAcceptance) return Success(false)
        if (status == TradePaymentStatus.CANCELLING && _providerReference == providerReference) {
            return Success(false)
        }
        if (
            status !in cancellableStates ||
                (_providerReference != null && _providerReference != providerReference)
        ) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _providerReference = providerReference.takeIf { it.isNotBlank() }
        val publicAcceptanceValid =
            providerReference.isPersistableProviderReference() &&
                payAction.isPersistablePayAction() &&
                acceptedAt <= acceptBefore &&
                acceptedAt < expiresAt
        if (publicAcceptanceValid) {
            _payAction = payAction
            _providerAcceptedAt = acceptedAt
            _acceptBefore = acceptBefore
            _expiresAt = expiresAt
        } else {
            _payAction = null
            _providerAcceptedAt = null
            _acceptBefore = null
            _expiresAt = null
        }
        _status = TradePaymentStatus.CANCELLING
        return Success(true)
    }

    fun markUncertain(reason: String): Result<Boolean, com.jstore.common.errors.BusinessError> =
        finishPreparation(TradePaymentStatus.UNCERTAIN, reason)

    fun reject(reason: String): Result<Boolean, com.jstore.common.errors.BusinessError> =
        finishPreparation(TradePaymentStatus.REJECTED, reason)

    private fun finishPreparation(
        target: TradePaymentStatus,
        reason: String,
    ): Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (status == target && failureReason == reason) return Success(false)
        if (status != TradePaymentStatus.PREPARING || !reason.isPersistableFailureReason()) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _failureReason = reason
        _status = target
        return Success(true)
    }

    fun requestCancellation(
        reason: String
    ): Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (!reason.isPersistableFailureReason()) return Failure(PaymentErrors.INVALID_STATE)
        if (
            status in
                setOf(
                    TradePaymentStatus.PREPARATION_CANCELLING,
                    TradePaymentStatus.CANCELLING,
                )
        ) {
            return Success(false)
        }
        if (status == TradePaymentStatus.CANCELLED) return Success(false)
        if (
            status !in
                setOf(
                    TradePaymentStatus.PREPARING,
                    TradePaymentStatus.READY,
                    TradePaymentStatus.REJECTED,
                    TradePaymentStatus.UNCERTAIN,
                )
        ) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _cancellationReason = reason
        _status =
            if (status == TradePaymentStatus.PREPARING) {
                TradePaymentStatus.PREPARATION_CANCELLING
            } else {
                TradePaymentStatus.CANCELLING
            }
        return Success(true)
    }

    fun continueCancellationAfterPreparation():
        Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (status == TradePaymentStatus.CANCELLING) return Success(false)
        if (status != TradePaymentStatus.PREPARATION_CANCELLING) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _status = TradePaymentStatus.CANCELLING
        return Success(true)
    }

    fun confirmCancellationAfterPreparationRejected():
        Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (status == TradePaymentStatus.CANCELLED) return Success(false)
        if (status != TradePaymentStatus.PREPARATION_CANCELLING) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _status = TradePaymentStatus.CANCELLED
        return Success(true)
    }

    fun confirmCancellation(): Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (status == TradePaymentStatus.CANCELLED) return Success(false)
        if (status != TradePaymentStatus.CANCELLING) return Failure(PaymentErrors.INVALID_STATE)
        _status = TradePaymentStatus.CANCELLED
        return Success(true)
    }

    fun recordCancellationUncertain(
        reason: String
    ): Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (status != TradePaymentStatus.CANCELLING || !reason.isPersistableFailureReason()) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        if (failureReason == reason) return Success(false)
        _failureReason = reason
        return Success(true)
    }

    companion object {
        const val MAX_PROVIDER_REFERENCE_LENGTH = 256
        const val MAX_PAY_ACTION_LENGTH = 2048
        const val MAX_FAILURE_REASON_LENGTH = 1024

        fun prepare(
            id: TradePaymentId,
            tradeId: Long,
            settlementPlanId: Long,
            installmentId: String,
            payableAmount: Price,
            currency: String,
            allocations: List<PaymentAllocationSnapshot>,
            createdAt: Instant = Instant.now(),
        ) =
            TradePayment(
                id,
                tradeId,
                settlementPlanId,
                installmentId,
                payableAmount,
                currency,
                allocations,
                TradePaymentStatus.PREPARING,
                createdAt = createdAt,
            )
    }
}

private fun String.isPersistableProviderReference() =
    isNotBlank() && length <= TradePayment.MAX_PROVIDER_REFERENCE_LENGTH

private fun String.isPersistablePayAction() =
    isNotBlank() && length <= TradePayment.MAX_PAY_ACTION_LENGTH

private fun String.isPersistableFailureReason() =
    isNotBlank() && length <= TradePayment.MAX_FAILURE_REASON_LENGTH
