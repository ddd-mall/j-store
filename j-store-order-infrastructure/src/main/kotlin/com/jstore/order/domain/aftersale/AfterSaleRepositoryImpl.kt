package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.*
import com.jstore.order.domain.aftersale.persistence.*
import com.jstore.order.domain.order.*
import org.springframework.stereotype.Repository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.LinkedList

@Repository
class AfterSaleRepositoryImpl(
    private val roots:AfterSalePOJpaRepository,
    private val capacities:AfterSaleCapacityPOJpaRepository,
    private val receipts:AfterSaleCommandReceiptPOJpaRepository,
    private val publisher:DomainEventPublisher,
    private val sequence:SnowFlakSequence,
    transactionManager: PlatformTransactionManager,
):AfterSaleRepository {
    private val transaction = TransactionTemplate(transactionManager)
    private val receiptRecovery = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }
    override fun save(entity: AfterSale)=roots.save(toPO(entity)).let(::toDomain)
    override fun findById(id:AfterSaleId)=roots.findById(id.value).orElse(null)?.let(::toDomain)
    override fun findByOrderId(orderId:OrderId)=roots.findByOrderIdOrderByCreateTimeDesc(orderId.value).map(::toDomain)
    override fun findReceipt(actorId:Long,type:AfterSaleCommandType,key:String)=receipts.findByActorIdAndCommandTypeAndIdempotencyKey(actorId,type.name,key)?.let { AfterSaleCommandReceipt(it.actorId,type,it.idempotencyKey,it.requestHash,AfterSaleId(it.afterSaleId),AfterSaleStatus.valueOf(it.resultStatus),it.createdAt) }
    override fun createWithAllocation(
        afterSale: AfterSale,
        ceilings: List<RefundCapacityCeiling>,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError> = execute(receipt) {
        val itemIds = afterSale.items.map { it.orderItemId.value }.sorted()
        if (ceilings.map { it.orderItemId.value }.sorted() != itemIds) {
            abort(AfterSaleErrors.CONCURRENT_MODIFICATION)
        }
        ceilings.sortedBy { it.orderItemId.value }.forEach { ceiling ->
            capacities.initialize(ceiling.orderItemId.value, ceiling.orderId.value, ceiling.quantity, ceiling.amount.toBigDecimal())
        }
        val locked = capacities.lockAll(itemIds).associateBy { it.orderItemId }
        ceilings.forEach { verifyCeiling(it, locked[it.orderItemId.value]) }
        afterSale.items.sortedBy { it.orderItemId.value }.forEach { item ->
            val capacity = locked[item.orderItemId.value] ?: abort(AfterSaleErrors.CONCURRENT_MODIFICATION)
            val quantityAfter = capacity.requestedQuantity + capacity.approvedQuantity + item.requestedQuantity
            val amountAfter = capacity.requestedAmount + capacity.approvedAmount + item.requestedAmount.toBigDecimal()
            if (quantityAfter > capacity.quantityCeiling || amountAfter > capacity.amountCeiling) {
                abort(AfterSaleErrors.CAPACITY_EXCEEDED)
            }
            capacity.requestedQuantity += item.requestedQuantity
            capacity.requestedAmount += item.requestedAmount.toBigDecimal()
            capacities.save(capacity)
        }
        roots.save(toPO(afterSale))
        saveReceipt(receipt)
        publish(afterSale)
        afterSale
    }
    override fun saveDecision(afterSale:AfterSale,allocationAction:AllocationAction,receipt:AfterSaleCommandReceipt):Result<AfterSale,BusinessError> = execute(receipt) {
        val persisted = roots.findByIdForUpdate(afterSale.id.value) ?: abort(AfterSaleErrors.NOT_FOUND)
        if (persisted.version != afterSale.version) abort(AfterSaleErrors.CONCURRENT_MODIFICATION)
        val locked = capacities.lockAll(afterSale.items.map { it.orderItemId.value }.sorted()).associateBy { it.orderItemId }
        afterSale.items.sortedBy { it.orderItemId.value }.forEach { item ->
            val capacity = locked[item.orderItemId.value] ?: abort(AfterSaleErrors.CONCURRENT_MODIFICATION)
            if (capacity.requestedQuantity < item.requestedQuantity || capacity.requestedAmount < item.requestedAmount.toBigDecimal()) {
                abort(AfterSaleErrors.CONCURRENT_MODIFICATION)
            }
            capacity.requestedQuantity -= item.requestedQuantity
            capacity.requestedAmount -= item.requestedAmount.toBigDecimal()
            if (allocationAction == AllocationAction.APPROVE) {
                capacity.approvedQuantity += item.requestedQuantity
                capacity.approvedAmount += item.requestedAmount.toBigDecimal()
            }
            capacities.save(capacity)
        }
        roots.save(toPO(afterSale))
        saveReceipt(receipt)
        publish(afterSale)
        afterSale
    }
    private fun verifyCeiling(expected: RefundCapacityCeiling, actual: AfterSaleCapacityPO?) {
        actual ?: abort(AfterSaleErrors.CONCURRENT_MODIFICATION)
        if (actual.orderId != expected.orderId.value || actual.quantityCeiling != expected.quantity ||
            actual.amountCeiling.compareTo(expected.amount.toBigDecimal()) != 0
        ) abort(AfterSaleErrors.CAPACITY_EXCEEDED)
    }
    private fun execute(receipt: AfterSaleCommandReceipt, work: () -> AfterSale): Result<AfterSale, BusinessError> = try {
        Success(requireNotNull(transaction.execute { work() }))
    } catch (failure: RepositoryAbort) {
        Failure(failure.error)
    } catch (failure: DataIntegrityViolationException) {
        recoverReceipt(receipt) ?: Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
    } catch (failure: ObjectOptimisticLockingFailureException) {
        Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
    }
    private fun recoverReceipt(expected: AfterSaleCommandReceipt): Result<AfterSale, BusinessError>? = receiptRecovery.execute {
        val actual = findReceipt(expected.actorId, expected.type, expected.key) ?: return@execute null
        if (actual.requestHash != expected.requestHash) Failure(AfterSaleErrors.IDEMPOTENCY_CONFLICT)
        else findById(actual.afterSaleId)?.let(::Success) ?: Failure(AfterSaleErrors.NOT_FOUND)
    }
    private fun abort(error:BusinessError):Nothing=throw RepositoryAbort(error)
    private class RepositoryAbort(val error:BusinessError):RuntimeException()
    private fun saveReceipt(r:AfterSaleCommandReceipt)=receipts.saveAndFlush(AfterSaleCommandReceiptPO(sequence.nextId(),r.actorId,r.type.name,r.key,r.requestHash,r.afterSaleId.value,r.resultStatus.name,r.createdAt))
    private fun publish(a:AfterSale){val events=a.domainEventQueue.toList();events.forEach(publisher::publishEvent);repeat(events.size){a.domainEventQueue.poll()}}
    internal fun toPO(a:AfterSale)=AfterSalePO(a.id.value,a.orderId.value,a.applicantId.value,a.merchantId.value,a.status.name,a.reason.category.name,a.reason.description,a.fulfillmentSnapshot.status.name,a.fulfillmentSnapshot.requireReturn,a.reviewDecision?.reviewerId?.value,a.reviewDecision?.reviewedAt,a.reviewDecision?.rejectionReason,a.cancelledAt,a.createTime,a.updateTime,a.version,a.items.map{AfterSaleItemPO(it.id.value,a.id.value,it.orderId.value,it.orderItemId.value,it.requestedQuantity,it.requestedAmount.toBigDecimal(),it.currency,it.eligibilitySnapshot.refundableQuantity,it.eligibilitySnapshot.refundableAmount.toBigDecimal(),it.eligibilitySnapshot.goods.skuId,it.eligibilitySnapshot.goods.spuId,it.eligibilitySnapshot.goods.goodsName,it.eligibilitySnapshot.goods.skuDescription)}.toMutableList())
    internal fun toDomain(p:AfterSalePO):AfterSale=AfterSaleImpl(AfterSaleId(p.id),OrderId(p.orderId),ApplicantActorId(p.applicantId),MerchantActorId(p.merchantId),AfterSaleStatus.valueOf(p.status),RefundReason(RefundCategory.valueOf(p.reasonCategory),p.reasonDescription),FulfillmentSnapshot(FulfillmentStatus.valueOf(p.fulfillmentStatus),p.requireReturn),p.items.map{val g=GoodsSnapshot(it.skuId,it.spuId,it.goodsName,it.skuDescription);AfterSaleItemImpl(AfterSaleItemId(it.id),OrderId(it.orderId),OrderItemId(it.orderItemId),it.requestedQuantity,Price.fromBigDecimal(it.requestedAmount),it.currency,RefundEligibilitySnapshot(OrderItemId(it.orderItemId),it.eligibleQuantity,Price.fromBigDecimal(it.eligibleAmount),it.currency,g))},p.reviewerId?.let{ReviewDecision(MerchantActorId(it),p.reviewedAt!!,p.rejectionReason)},p.cancelledAt,p.createTime,p.updateTime,p.version,LinkedList())
}
