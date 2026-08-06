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
package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.flatMap
import com.jstore.common.utils.map
import com.jstore.common.utils.onSuccess
import com.jstore.order.domain.aftersale.AfterSale
import com.jstore.order.domain.aftersale.AfterSaleCommandReceipt
import com.jstore.order.domain.aftersale.AfterSaleCommandType
import com.jstore.order.domain.aftersale.AfterSaleErrors
import com.jstore.order.domain.aftersale.AfterSaleFactory
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.AfterSaleRepository
import com.jstore.order.domain.aftersale.AllocationAction
import com.jstore.order.domain.aftersale.MerchantActorId
import com.jstore.order.domain.aftersale.RefundCapacityCeiling
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCancelCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleReceiveReturnCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRejectCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRetryRefundCMD
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime

data class AfterSaleOrderAccess(
    val buyerId: Long,
    val merchantId: MerchantActorId,
    val afterSales: List<AfterSale>,
)

class AfterSaleApplicationService(
    private val factory: AfterSaleFactory,
    private val afterSaleRepository: AfterSaleRepository,
    private val orderRepository: OrderRepository,
    private val domainEventPublisher: DomainEventPublisher,
) : AfterSaleUseCase {
    override fun findById(id: AfterSaleId): Result<AfterSale, BusinessError> =
        afterSaleRepository.findById(id)?.let(::Success) ?: Failure(AfterSaleErrors.NOT_FOUND)

    override fun listByOrderForAccess(
        orderId: OrderId
    ): Result<AfterSaleOrderAccess, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        return Success(
            AfterSaleOrderAccess(
                buyerId = order.buyerInfo.uid,
                merchantId = MerchantActorId(order.merchantId.value),
                afterSales = afterSaleRepository.findByOrderId(orderId),
            )
        )
    }

    override fun create(cmd: AfterSaleCreateCMD): Result<AfterSale, BusinessError> {
        val valid =
            when (val result = cmd.validate()) {
                is Success -> result.value
                is Failure -> return result
            }
        receipt(
                valid.applicantId.value,
                AfterSaleCommandType.CREATE,
                valid.idempotencyKey,
                hash(valid.toString()),
            )
            ?.let {
                return it
            }
        val order =
            orderRepository.findById(valid.orderId)
                ?: return Failure(AfterSaleErrors.ORDER_NOT_FOUND)
        if (order.buyerInfo.uid != valid.applicantId.value)
            return Failure(AfterSaleErrors.APPLICANT_FORBIDDEN)
        val merchant = MerchantActorId(order.merchantId.value)
        val afterSale =
            when (
                val result =
                    factory.create(valid, order, merchant, LocalDateTime.now(), Instant.now())
            ) {
                is Success -> result.value
                is Failure -> return result
            }
        val requestedItemIds = afterSale.items.mapTo(mutableSetOf()) { it.orderItemId }
        val ceilings =
            order.items
                .asSequence()
                .filter { it.id in requestedItemIds }
                .map { RefundCapacityCeiling(order.id, it.id, it.quantity, it.purchasedAmount) }
                .toList()
        val result =
            afterSaleRepository.createWithAllocation(
                afterSale,
                ceilings,
                AfterSaleCommandReceipt(
                    valid.applicantId.value,
                    AfterSaleCommandType.CREATE,
                    valid.idempotencyKey,
                    hash(valid.toString()),
                    afterSale.id,
                    afterSale.status,
                    LocalDateTime.now(),
                ),
            )
        if (result is Success) afterSale.publishPendingEvents(domainEventPublisher)
        return result
    }

    override fun approve(cmd: AfterSaleApproveCMD): Result<AfterSale, BusinessError> =
        decide(
            cmd.merchantId.value,
            AfterSaleCommandType.APPROVE,
            cmd.idempotencyKey,
            cmd.afterSaleId,
            "",
            AllocationAction.APPROVE,
        ) {
            it.approve(cmd.merchantId, Instant.now())
        }

    override fun reject(cmd: AfterSaleRejectCMD): Result<AfterSale, BusinessError> =
        decide(
            cmd.merchantId.value,
            AfterSaleCommandType.REJECT,
            cmd.idempotencyKey,
            cmd.afterSaleId,
            cmd.rejectionReason.trim(),
            AllocationAction.RELEASE,
        ) {
            it.reject(cmd.merchantId, cmd.rejectionReason, Instant.now())
        }

    override fun cancel(cmd: AfterSaleCancelCMD): Result<AfterSale, BusinessError> =
        decide(
            cmd.applicantId.value,
            AfterSaleCommandType.CANCEL,
            cmd.idempotencyKey,
            cmd.afterSaleId,
            "",
            AllocationAction.RELEASE,
        ) {
            it.cancel(cmd.applicantId, Instant.now())
        }

    override fun receiveReturn(cmd: AfterSaleReceiveReturnCMD): Result<AfterSale, BusinessError> =
        mutate(cmd.afterSaleId) {
            it.receiveReturn(cmd.merchantId, Instant.now())
        }

    override fun retryRefund(cmd: AfterSaleRetryRefundCMD): Result<AfterSale, BusinessError> =
        mutate(cmd.afterSaleId) {
            it.retryRefund(cmd.merchantId, Instant.now())
        }

    override fun recordRefundSucceeded(
        afterSaleId: AfterSaleId,
        refundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        recordExternal(afterSaleId) {
            it.markRefundSucceeded(refundId, occurredAt)
        }

    override fun recordRefundFailed(
        afterSaleId: AfterSaleId,
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        recordExternal(afterSaleId) {
            it.markRefundFailed(refundId, reason, occurredAt)
        }

    private fun mutate(
        id: AfterSaleId,
        operation: (AfterSale) -> Result<Boolean, BusinessError>,
    ): Result<AfterSale, BusinessError> {
        val aggregate =
            afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        return operation(aggregate).map {
            val saved = afterSaleRepository.save(aggregate)
            aggregate.publishPendingEvents(domainEventPublisher)
            saved
        }
    }

    private fun recordExternal(
        id: AfterSaleId,
        operation: (AfterSale) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val aggregate =
            afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        return operation(aggregate).onSuccess { changed ->
            if (changed) {
                afterSaleRepository.save(aggregate)
                aggregate.publishPendingEvents(domainEventPublisher)
            }
        }
    }

    private fun decide(
        actor: Long,
        type: AfterSaleCommandType,
        key: String,
        id: AfterSaleId,
        payload: String,
        action: AllocationAction,
        operation: (AfterSale) -> Result<Unit, BusinessError>,
    ): Result<AfterSale, BusinessError> {
        if (key.trim().length !in 1..128) return Failure(AfterSaleErrors.IDEMPOTENCY_KEY_INVALID)
        val digest = hash("$type|$id|$actor|$payload")
        receipt(actor, type, key, digest)?.let {
            return it
        }
        val aggregate =
            afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        return operation(aggregate)
            .flatMap {
                afterSaleRepository.saveDecision(
                    aggregate,
                    action,
                    AfterSaleCommandReceipt(
                        actor,
                        type,
                        key.trim(),
                        digest,
                        id,
                        aggregate.status,
                        LocalDateTime.now(),
                    ),
                )
            }
            .onSuccess { aggregate.publishPendingEvents(domainEventPublisher) }
    }

    private fun receipt(
        actor: Long,
        type: AfterSaleCommandType,
        key: String,
        digest: String,
    ): Result<AfterSale, BusinessError>? {
        val receipt = afterSaleRepository.findReceipt(actor, type, key.trim()) ?: return null
        if (receipt.requestHash != digest) return Failure(AfterSaleErrors.IDEMPOTENCY_CONFLICT)
        return afterSaleRepository.findById(receipt.afterSaleId)?.let(::Success)
            ?: Failure(AfterSaleErrors.NOT_FOUND)
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
