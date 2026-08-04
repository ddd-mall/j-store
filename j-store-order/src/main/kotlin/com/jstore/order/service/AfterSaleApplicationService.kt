package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.common.utils.onFailure
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

class AfterSaleApplicationService(
    private val factory: AfterSaleFactory,
    private val afterSaleRepository: AfterSaleRepository,
    private val orderRepository: OrderRepository,
) {
    fun create(cmd: AfterSaleCreateCMD): Result<AfterSale, BusinessError> {
        val valid = when (val result = cmd.validate()) {
            is Success -> result.value
            is Failure -> return result
        }
        receipt(valid.applicantId.value, AfterSaleCommandType.CREATE, valid.idempotencyKey, hash(valid.toString()))?.let {
            return it
        }
        val order = orderRepository.findById(valid.orderId) ?: return Failure(AfterSaleErrors.ORDER_NOT_FOUND)
        if (order.buyerInfo.uid != valid.applicantId.value) return Failure(AfterSaleErrors.APPLICANT_FORBIDDEN)
        val merchant = MerchantActorId(order.merchantId.value)
        val afterSale = when (
            val result = factory.create(valid, order, merchant, LocalDateTime.now(), Instant.now())
        ) {
            is Success -> result.value
            is Failure -> return result
        }
        val requestedItemIds = afterSale.items.mapTo(mutableSetOf()) { it.orderItemId }
        val ceilings = order.items
            .asSequence()
            .filter { it.id in requestedItemIds }
            .map { RefundCapacityCeiling(order.id, it.id, it.quantity, it.purchasedAmount) }
            .toList()
        return afterSaleRepository.createWithAllocation(
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
    }

    fun get(id: AfterSaleId, actorId: Long): Result<AfterSale, BusinessError> =
        afterSaleRepository.findById(id)
            ?.takeIf { it.applicantId.value == actorId || it.merchantId.value == actorId }
            ?.let(::Success)
            ?: Failure(AfterSaleErrors.NOT_FOUND)

    fun listByOrder(orderId: OrderId, actorId: Long): Result<List<AfterSale>, BusinessError> {
        val order = orderRepository.findById(orderId) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        if (order.buyerInfo.uid != actorId && order.merchantId.value != actorId) {
            return Failure(AfterSaleErrors.NOT_FOUND)
        }
        return Success(afterSaleRepository.findByOrderId(orderId))
    }

    fun approve(cmd: AfterSaleApproveCMD): Result<AfterSale, BusinessError> = decide(
        cmd.merchantId.value,
        AfterSaleCommandType.APPROVE,
        cmd.idempotencyKey,
        cmd.afterSaleId,
        "",
        AllocationAction.APPROVE,
    ) { it.approve(cmd.merchantId, Instant.now()) }

    fun reject(cmd: AfterSaleRejectCMD): Result<AfterSale, BusinessError> = decide(
        cmd.merchantId.value,
        AfterSaleCommandType.REJECT,
        cmd.idempotencyKey,
        cmd.afterSaleId,
        cmd.rejectionReason.trim(),
        AllocationAction.RELEASE,
    ) { it.reject(cmd.merchantId, cmd.rejectionReason, Instant.now()) }

    fun cancel(cmd: AfterSaleCancelCMD): Result<AfterSale, BusinessError> = decide(
        cmd.applicantId.value,
        AfterSaleCommandType.CANCEL,
        cmd.idempotencyKey,
        cmd.afterSaleId,
        "",
        AllocationAction.RELEASE,
    ) { it.cancel(cmd.applicantId, Instant.now()) }

    fun receiveReturn(cmd: AfterSaleReceiveReturnCMD): Result<AfterSale, BusinessError> = mutate(cmd.afterSaleId) {
        it.receiveReturn(cmd.merchantId, Instant.now())
    }

    fun retryRefund(cmd: AfterSaleRetryRefundCMD): Result<AfterSale, BusinessError> = mutate(cmd.afterSaleId) {
        it.retryRefund(cmd.merchantId, Instant.now())
    }

    fun recordRefundSucceeded(
        afterSaleId: AfterSaleId,
        refundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> = recordExternal(afterSaleId) {
        it.markRefundSucceeded(refundId, occurredAt)
    }

    fun recordRefundFailed(
        afterSaleId: AfterSaleId,
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> = recordExternal(afterSaleId) {
        it.markRefundFailed(refundId, reason, occurredAt)
    }

    private fun mutate(
        id: AfterSaleId,
        operation: (AfterSale) -> Result<Boolean, BusinessError>,
    ): Result<AfterSale, BusinessError> {
        val aggregate = afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        operation(aggregate).onFailure { return Failure(it) }
        return Success(afterSaleRepository.save(aggregate))
    }

    private fun recordExternal(
        id: AfterSaleId,
        operation: (AfterSale) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val aggregate = afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        val changed = operation(aggregate)
        changed.onFailure { return Failure(it) }
        if (changed.getOrThrow()) afterSaleRepository.save(aggregate)
        return changed
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
        receipt(actor, type, key, digest)?.let { return it }
        val aggregate = afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        operation(aggregate).onFailure { return Failure(it) }
        return afterSaleRepository.saveDecision(
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

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
