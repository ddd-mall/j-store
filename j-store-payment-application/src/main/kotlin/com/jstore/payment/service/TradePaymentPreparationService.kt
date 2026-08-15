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
package com.jstore.payment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.payment.domain.payment.*
import java.time.Instant

fun interface TradePaymentIdentityGenerator {
    fun nextId(): Long
}

data class PaymentProviderRequest(
    val paymentId: Long,
    val tradeId: Long,
    val amountFen: Long,
    val currency: String,
    val idempotencyKey: String,
    val expiresAt: Instant,
)

sealed interface PaymentProviderResult {
    data class Accepted(
        val providerReference: String,
        val payAction: String,
        val acceptedAt: Instant,
        val expiresAt: Instant,
    ) : PaymentProviderResult

    data class Rejected(val reason: String) : PaymentProviderResult

    data class Uncertain(val reason: String) : PaymentProviderResult
}

fun interface PaymentProviderGateway {
    fun prepare(request: PaymentProviderRequest): PaymentProviderResult
}

data class PaymentProviderCancellationRequest(
    val paymentId: Long,
    val tradeId: Long,
    val providerReference: String?,
    val idempotencyKey: String,
)

sealed interface PaymentProviderCancellationResult {
    data object Confirmed : PaymentProviderCancellationResult

    data class Uncertain(val reason: String) : PaymentProviderCancellationResult
}

fun interface PaymentProviderCancellationGateway {
    fun cancel(request: PaymentProviderCancellationRequest): PaymentProviderCancellationResult
}

fun interface TradePaymentPreparationUseCase {
    fun prepare(command: PreparePaymentInstallmentCommand): Result<Boolean, BusinessError>
}

sealed interface TradePaymentPreparationStart {
    data class Pending(val request: PaymentProviderRequest) : TradePaymentPreparationStart

    data class Completed(val changed: Boolean) : TradePaymentPreparationStart
}

fun interface TradePaymentCancellationUseCase {
    fun cancel(command: CancelPaymentInstallmentCommand): Result<Boolean, BusinessError>
}

sealed interface TradePaymentCancellationStart {
    data class Pending(val request: PaymentProviderCancellationRequest) :
        TradePaymentCancellationStart

    data class Completed(val changed: Boolean) : TradePaymentCancellationStart
}

class TradePaymentCancellationService(
    private val payments: TradePaymentRepository,
    private val provider: PaymentProviderCancellationGateway,
    private val publisher: IntegrationMessagePublisher,
    private val now: () -> Instant = Instant::now,
) : TradePaymentCancellationUseCase {
    override fun cancel(command: CancelPaymentInstallmentCommand): Result<Boolean, BusinessError> {
        return when (val start = start(command)) {
            is Failure -> start
            is Success ->
                when (val value = start.value) {
                    is TradePaymentCancellationStart.Completed -> Success(value.changed)
                    is TradePaymentCancellationStart.Pending ->
                        complete(command, value.request, invokeProvider(value.request))
                }
        }
    }

    fun start(
        command: CancelPaymentInstallmentCommand
    ): Result<TradePaymentCancellationStart, BusinessError> {
        val payment =
            payments.findByInstallment(command.settlementPlanId, command.installmentId)
                ?: return Failure(PaymentErrors.ORDER_NOT_FOUND)
        if (payment.tradeId != command.tradeId) return Failure(PaymentErrors.ORDER_CONFLICT)
        if (payment.status == TradePaymentStatus.CANCELLED) {
            publishCancellationConfirmed(payment, command)
            return Success(TradePaymentCancellationStart.Completed(false))
        }
        val changed = payment.requestCancellation(command.reason)
        if (changed is Failure) return changed
        if (changed is Success && changed.value) payments.save(payment)
        if (payment.status == TradePaymentStatus.PREPARATION_CANCELLING) {
            return Success(
                TradePaymentCancellationStart.Completed(changed is Success && changed.value)
            )
        }
        return Success(
            TradePaymentCancellationStart.Pending(
                PaymentProviderCancellationRequest(
                    payment.id.value,
                    payment.tradeId,
                    payment.providerReference,
                    "${payment.settlementPlanId}:${payment.installmentId}:cancel",
                )
            )
        )
    }

    fun invokeProvider(
        request: PaymentProviderCancellationRequest
    ): PaymentProviderCancellationResult =
        try {
            provider.cancel(request)
        } catch (exception: Exception) {
            PaymentProviderCancellationResult.Uncertain(
                exception.message?.takeIf { it.isNotBlank() } ?: "provider cancellation failed"
            )
        }

    fun complete(
        command: CancelPaymentInstallmentCommand,
        request: PaymentProviderCancellationRequest,
        result: PaymentProviderCancellationResult,
    ): Result<Boolean, BusinessError> {
        val payment =
            payments.findByInstallment(command.settlementPlanId, command.installmentId)
                ?: return Failure(PaymentErrors.ORDER_NOT_FOUND)
        if (payment.id.value != request.paymentId || payment.tradeId != command.tradeId) {
            return Failure(PaymentErrors.ORDER_CONFLICT)
        }
        if (payment.status == TradePaymentStatus.CANCELLED) {
            return Success(false)
        }
        if (payment.status != TradePaymentStatus.CANCELLING) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        if (payment.providerReference != request.providerReference) {
            return Failure(PaymentErrors.CANCELLATION_UNCERTAIN)
        }
        return when (result) {
            PaymentProviderCancellationResult.Confirmed -> {
                payment.confirmCancellation().let { if (it is Failure) return it }
                payments.save(payment)
                publishCancellationConfirmed(payment, command)
                Success(true)
            }
            is PaymentProviderCancellationResult.Uncertain -> {
                val reason =
                    result.reason.takeIf { it.isPersistableFailureReason() }
                        ?: "provider returned an invalid cancellation result"
                payment.recordCancellationUncertain(reason).let { if (it is Failure) return it }
                payments.save(payment)
                Failure(PaymentErrors.CANCELLATION_UNCERTAIN)
            }
        }
    }

    private fun publishCancellationConfirmed(
        payment: TradePayment,
        command: CancelPaymentInstallmentCommand,
    ) {
        publisher.publish(
            PaymentCancellationConfirmedIntegrationEvent(
                payment.tradeId,
                payment.settlementPlanId,
                payment.installmentId,
                payment.id.value,
                requireNotNull(payment.cancellationReason),
                command.messageId,
                now(),
            )
        )
    }
}

class TradePaymentPreparationService(
    private val payments: TradePaymentRepository,
    private val ids: TradePaymentIdentityGenerator,
    private val provider: PaymentProviderGateway,
    private val publisher: IntegrationMessagePublisher,
    private val now: () -> Instant = Instant::now,
) : TradePaymentPreparationUseCase {
    override fun prepare(
        command: PreparePaymentInstallmentCommand
    ): Result<Boolean, BusinessError> {
        return when (val start = start(command)) {
            is Failure -> start
            is Success ->
                when (val value = start.value) {
                    is TradePaymentPreparationStart.Completed -> Success(value.changed)
                    is TradePaymentPreparationStart.Pending ->
                        complete(command, value.request.paymentId, invokeProvider(value.request))
                }
        }
    }

    fun start(
        command: PreparePaymentInstallmentCommand
    ): Result<TradePaymentPreparationStart, BusinessError> {
        val allocations = command.toAllocations()
        val existing = payments.findByInstallment(command.settlementPlanId, command.installmentId)
        val payment =
            existing
                ?: TradePayment.prepare(
                    TradePaymentId(ids.nextId()),
                    command.tradeId,
                    command.settlementPlanId,
                    command.installmentId,
                    Price.ofFen(command.amountFen),
                    command.currency,
                    allocations,
                    command.occurredAt,
                )
        if (
            !payment.matches(
                command.tradeId,
                command.settlementPlanId,
                command.installmentId,
                Price.ofFen(command.amountFen),
                command.currency,
                allocations,
            )
        ) {
            return Failure(PaymentErrors.ORDER_CONFLICT)
        }
        if (existing == null) payments.save(payment)
        if (payment.status == TradePaymentStatus.PREPARATION_CANCELLING) {
            return Success(TradePaymentPreparationStart.Pending(payment.toProviderRequest(command)))
        }
        if (payment.status != TradePaymentStatus.PREPARING) {
            if (
                payment.status !in
                    setOf(TradePaymentStatus.CANCELLED, TradePaymentStatus.CANCELLING)
            ) {
                publishCurrentResult(payment, command)
            }
            return Success(TradePaymentPreparationStart.Completed(false))
        }

        if (now() > command.acceptBefore) {
            payment.reject("payment preparation deadline expired")
            payments.save(payment)
            publishCurrentResult(payment, command)
            return Success(TradePaymentPreparationStart.Completed(true))
        }

        return Success(TradePaymentPreparationStart.Pending(payment.toProviderRequest(command)))
    }

    fun invokeProvider(request: PaymentProviderRequest): PaymentProviderResult =
        try {
            provider.prepare(request)
        } catch (exception: Exception) {
            PaymentProviderResult.Uncertain(
                exception.message?.takeIf { it.isNotBlank() } ?: "provider call failed"
            )
        }

    fun complete(
        command: PreparePaymentInstallmentCommand,
        paymentId: Long,
        result: PaymentProviderResult,
    ): Result<Boolean, BusinessError> {
        val allocations = command.toAllocations()
        val payment =
            payments.findByInstallment(command.settlementPlanId, command.installmentId)
                ?: return Failure(PaymentErrors.ORDER_NOT_FOUND)
        if (
            payment.id.value != paymentId ||
                !payment.matches(
                    command.tradeId,
                    command.settlementPlanId,
                    command.installmentId,
                    Price.ofFen(command.amountFen),
                    command.currency,
                    allocations,
                )
        ) {
            return Failure(PaymentErrors.ORDER_CONFLICT)
        }
        if (
            result is PaymentProviderResult.Accepted &&
                payment.status in
                    setOf(
                        TradePaymentStatus.PREPARATION_CANCELLING,
                        TradePaymentStatus.CANCELLING,
                        TradePaymentStatus.CANCELLED,
                    )
        ) {
            val changed =
                payment.recordLateProviderAcceptance(
                    result.providerReference,
                    result.payAction,
                    result.acceptedAt,
                    command.acceptBefore,
                    result.expiresAt,
                )
            if (changed is Failure) return changed
            if (changed is Success && changed.value) {
                payments.save(payment)
                publisher.publish(
                    CancelPaymentInstallmentCommand(
                        payment.tradeId,
                        payment.settlementPlanId,
                        payment.installmentId,
                        requireNotNull(payment.cancellationReason),
                        command.messageId,
                        now(),
                    )
                )
            }
            return changed
        }
        if (payment.status == TradePaymentStatus.PREPARATION_CANCELLING) {
            return when (result) {
                is PaymentProviderResult.Accepted -> Failure(PaymentErrors.CANCELLATION_UNCERTAIN)
                is PaymentProviderResult.Rejected -> {
                    val changed = payment.confirmCancellationAfterPreparationRejected()
                    if (changed is Failure) return changed
                    if (changed is Success && changed.value) {
                        payments.save(payment)
                        publisher.publish(
                            PaymentCancellationConfirmedIntegrationEvent(
                                payment.tradeId,
                                payment.settlementPlanId,
                                payment.installmentId,
                                payment.id.value,
                                requireNotNull(payment.cancellationReason),
                                command.messageId,
                                now(),
                            )
                        )
                    }
                    changed
                }
                is PaymentProviderResult.Uncertain -> {
                    val changed = payment.continueCancellationAfterPreparation()
                    if (changed is Failure) return changed
                    if (changed is Success && changed.value) {
                        payments.save(payment)
                        publisher.publish(
                            CancelPaymentInstallmentCommand(
                                payment.tradeId,
                                payment.settlementPlanId,
                                payment.installmentId,
                                requireNotNull(payment.cancellationReason),
                                command.messageId,
                                now(),
                            )
                        )
                    }
                    changed
                }
            }
        }
        if (payment.status != TradePaymentStatus.PREPARING) {
            if (
                payment.status !in
                    setOf(TradePaymentStatus.CANCELLED, TradePaymentStatus.CANCELLING)
            ) {
                publishCurrentResult(payment, command)
            }
            return Success(false)
        }
        when (result) {
            is PaymentProviderResult.Accepted ->
                if (
                    result.acceptedAt <= command.acceptBefore &&
                        result.acceptedAt < result.expiresAt &&
                        result.expiresAt <= command.expiresAt &&
                        result.providerReference.isNotBlank() &&
                        result.providerReference.length <=
                            TradePayment.MAX_PROVIDER_REFERENCE_LENGTH &&
                        result.payAction.isNotBlank() &&
                        result.payAction.length <= TradePayment.MAX_PAY_ACTION_LENGTH
                ) {
                    payment.markReady(
                        result.providerReference,
                        result.payAction,
                        result.acceptedAt,
                        command.acceptBefore,
                        result.expiresAt,
                    )
                } else {
                    payment.markUncertain("provider returned an invalid preparation result")
                }
            is PaymentProviderResult.Rejected ->
                if (result.reason.isPersistableFailureReason()) payment.reject(result.reason)
                else payment.markUncertain("provider returned an invalid rejection reason")
            is PaymentProviderResult.Uncertain ->
                if (result.reason.isPersistableFailureReason()) payment.markUncertain(result.reason)
                else payment.markUncertain("provider returned an invalid uncertainty reason")
        }.let { changed ->
            if (changed is Failure) return changed
        }
        payments.save(payment)
        publishCurrentResult(payment, command)
        return Success(true)
    }

    private fun PreparePaymentInstallmentCommand.toAllocations() = allocations.map {
        PaymentAllocationSnapshot(
            it.orderPlanId,
            it.orderId,
            it.merchantId,
            Price.ofFen(it.amountFen),
        )
    }

    private fun TradePayment.toProviderRequest(command: PreparePaymentInstallmentCommand) =
        PaymentProviderRequest(
            id.value,
            tradeId,
            payableAmount.fen,
            currency,
            "$settlementPlanId:$installmentId",
            command.expiresAt,
        )

    private fun publishCurrentResult(
        payment: TradePayment,
        command: PreparePaymentInstallmentCommand,
    ) {
        val event =
            when (payment.status) {
                TradePaymentStatus.READY ->
                    PaymentPreparedIntegrationEvent(
                        payment.tradeId,
                        payment.settlementPlanId,
                        payment.installmentId,
                        payment.id.value,
                        payment.payableAmount.fen,
                        payment.currency,
                        requireNotNull(payment.acceptBefore),
                        requireNotNull(payment.expiresAt),
                        command.messageId,
                        now(),
                    )
                TradePaymentStatus.REJECTED ->
                    PaymentPreparationRejectedIntegrationEvent(
                        payment.tradeId,
                        payment.settlementPlanId,
                        payment.installmentId,
                        payment.id.value,
                        requireNotNull(payment.failureReason),
                        command.messageId,
                        now(),
                    )
                TradePaymentStatus.UNCERTAIN ->
                    PaymentPreparationUncertainIntegrationEvent(
                        payment.tradeId,
                        payment.settlementPlanId,
                        payment.installmentId,
                        payment.id.value,
                        requireNotNull(payment.failureReason),
                        command.messageId,
                        now(),
                    )
                else -> error("Payment ${payment.id.value} has no preparation result")
            }
        publisher.publish(event)
    }
}

private fun String.isPersistableFailureReason() =
    isNotBlank() && length <= TradePayment.MAX_FAILURE_REASON_LENGTH

class PreparePaymentInstallmentCommandHandler(private val service: TradePaymentPreparationUseCase) :
    com.jstore.messaging.IntegrationMessageHandler<PreparePaymentInstallmentCommand> {
    override fun handlerId() = "payment.prepare-installment.v1"

    override fun handle(message: PreparePaymentInstallmentCommand) {
        when (val result = service.prepare(message)) {
            is Success -> Unit
            is Failure -> throw IllegalStateException(result.error.message)
        }
    }
}

class CancelPaymentInstallmentCommandHandler(private val service: TradePaymentCancellationUseCase) :
    com.jstore.messaging.IntegrationMessageHandler<CancelPaymentInstallmentCommand> {
    override fun handlerId() = "payment.cancel-installment.v1"

    override fun handle(message: CancelPaymentInstallmentCommand) {
        when (val result = service.cancel(message)) {
            is Success -> Unit
            is Failure -> throw IllegalStateException(result.error.message)
        }
    }
}
