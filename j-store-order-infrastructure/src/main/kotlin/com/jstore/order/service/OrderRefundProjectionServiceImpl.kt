package com.jstore.order.service
import com.jstore.common.utils.Failure
import com.jstore.order.domain.aftersale.event.AfterSaleApprovedEvent
import com.jstore.order.domain.order.*
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
@Service class OrderRefundProjectionServiceImpl(private val jpa:OrderPOJpaRepository):OrderRefundProjectionService{
 @Transactional override fun project(event:AfterSaleApprovedEvent){val po=jpa.findByIdForUpdate(event.orderId.value)?:throw NonRetryableRefundProjectionException("order not found");val order=OrderRepositoryImpl.Converter.toDomain(po);val result=order.registerApprovedAfterSale(event.afterSaleId,event.items.map{ApprovedRefundItem(it.orderItemId,it.quantity,it.amount)},event.occurredAt);if(result is Failure)throw NonRetryableRefundProjectionException(result.error.message);jpa.save(OrderRepositoryImpl.Converter.toPO(order))}
}
