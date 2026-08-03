package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.*
import com.jstore.order.acl.AfterSaleMerchantResolver
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.aftersale.command.*
import com.jstore.order.domain.order.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime

class AfterSaleApplicationService(private val factory: AfterSaleFactory, private val afterSaleRepository: AfterSaleRepository, private val orderRepository: OrderRepository, private val merchantResolver: AfterSaleMerchantResolver) {
    fun create(cmd: AfterSaleCreateCMD): Result<AfterSale, BusinessError> {
        val valid = when (val r = cmd.validate()) { is Success -> r.value; is Failure -> return r }
        receipt(valid.applicantId.value, AfterSaleCommandType.CREATE, valid.idempotencyKey, hash(valid.toString()))?.let { return it }
        val order = orderRepository.findById(valid.orderId) ?: return Failure(AfterSaleErrors.ORDER_NOT_FOUND)
        if (order.buyerInfo.uid != valid.applicantId.value) return Failure(AfterSaleErrors.APPLICANT_FORBIDDEN)
        val merchant = when (val r = merchantResolver.merchantFor(order)) { is Success -> r.value; is Failure -> return r }
        val afterSale = when (val r = factory.create(valid, order, merchant, LocalDateTime.now(), Instant.now())) { is Success -> r.value; is Failure -> return r }
        val requestedItemIds = afterSale.items.mapTo(mutableSetOf()) { it.orderItemId }
        val ceilings = order.items
            .asSequence()
            .filter { it.id in requestedItemIds }
            .map { RefundCapacityCeiling(order.id, it.id, it.quantity, it.purchasedAmount) }
            .toList()
        return afterSaleRepository.createWithAllocation(afterSale, ceilings, AfterSaleCommandReceipt(valid.applicantId.value, AfterSaleCommandType.CREATE, valid.idempotencyKey, hash(valid.toString()), afterSale.id, afterSale.status, LocalDateTime.now()))
    }
    fun get(id: AfterSaleId, actorId: Long): Result<AfterSale, BusinessError> = afterSaleRepository.findById(id)?.takeIf { it.applicantId.value == actorId || it.merchantId.value == actorId }?.let(::Success) ?: Failure(AfterSaleErrors.NOT_FOUND)
    fun listByOrder(orderId: OrderId, actorId: Long): Result<List<AfterSale>, BusinessError> {
        val order=orderRepository.findById(orderId)?:return Failure(AfterSaleErrors.NOT_FOUND)
        val merchant=when(val resolved=merchantResolver.merchantFor(order)){is Success->resolved.value.value;is Failure->return Failure(AfterSaleErrors.NOT_FOUND)}
        if(order.buyerInfo.uid!=actorId&&merchant!=actorId)return Failure(AfterSaleErrors.NOT_FOUND)
        return Success(afterSaleRepository.findByOrderId(orderId))
    }
    fun approve(cmd: AfterSaleApproveCMD) = decide(cmd.merchantId.value, AfterSaleCommandType.APPROVE, cmd.idempotencyKey, cmd.afterSaleId, "", AllocationAction.APPROVE) { it.approve(cmd.merchantId, Instant.now()) }
    fun reject(cmd: AfterSaleRejectCMD) = decide(cmd.merchantId.value, AfterSaleCommandType.REJECT, cmd.idempotencyKey, cmd.afterSaleId, cmd.rejectionReason.trim(), AllocationAction.RELEASE) { it.reject(cmd.merchantId, cmd.rejectionReason, Instant.now()) }
    fun cancel(cmd: AfterSaleCancelCMD) = decide(cmd.applicantId.value, AfterSaleCommandType.CANCEL, cmd.idempotencyKey, cmd.afterSaleId, "", AllocationAction.RELEASE) { it.cancel(cmd.applicantId, Instant.now()) }
    private fun decide(actor: Long, type: AfterSaleCommandType, key: String, id: AfterSaleId, payload: String, action: AllocationAction, operation: (AfterSale) -> Result<Unit, BusinessError>): Result<AfterSale, BusinessError> {
        if (key.trim().length !in 1..128) return Failure(AfterSaleErrors.IDEMPOTENCY_KEY_INVALID); val digest = hash("$type|$id|$actor|$payload")
        receipt(actor, type, key, digest)?.let { return it }; val aggregate = afterSaleRepository.findById(id) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        operation(aggregate).onFailure { return Failure(it) }; return afterSaleRepository.saveDecision(aggregate, action, AfterSaleCommandReceipt(actor, type, key.trim(), digest, id, aggregate.status, LocalDateTime.now()))
    }
    private fun receipt(actor: Long, type: AfterSaleCommandType, key: String, digest: String): Result<AfterSale, BusinessError>? { val r = afterSaleRepository.findReceipt(actor, type, key.trim()) ?: return null; if (r.requestHash != digest) return Failure(AfterSaleErrors.IDEMPOTENCY_CONFLICT); return afterSaleRepository.findById(r.afterSaleId)?.let(::Success) ?: Failure(AfterSaleErrors.NOT_FOUND) }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
