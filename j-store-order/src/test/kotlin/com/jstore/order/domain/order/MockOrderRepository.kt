package com.jstore.order.domain.order

import com.jstore.common.framework.Page
import com.jstore.order.common.MockPage
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.framwork.AbstractMockRepository
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

class MockOrderRepository: OrderRepository, AbstractMockRepository<OrderId, NormalOrderImpl>() {

    override fun findByBuyerUserId(uid: Long): List<NormalOrderImpl> {
        return super.objList.filter { order -> order.buyerInfo.uid == uid }.toList()
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<NormalOrderImpl> {
        val actualPageSize: Int = max(1, pageSize)
        val fromOffset: Int = max(0, currentPage - 1) *  actualPageSize
        val toOffSet: Int = min(super.objList.size, fromOffset + actualPageSize)

        if (fromOffset >= super.objList.size) {
            return MockPage(currentPage, 0 , listOf())
        }

        val orders =
            super.objList.filter { order -> order.buyerInfo.uid == uid }.subList(fromOffset, toOffSet)
        return MockPage(currentPage, orders.size, orders)
    }


    override fun nextId(): OrderId {
        return OrderId(snowFlakSequence.nextId())
    }

    override fun copyAnEntity(nextId: OrderId, entity: NormalOrderImpl): NormalOrderImpl {
        val now = LocalDateTime.now()
        return NormalOrderImpl(
            id = nextId,
            buyerInfo = entity.buyerInfo,
            orderItemImpls = entity.orderItemImpls,
            shippingAddressInfo = entity.shippingAddressInfo,
            status = entity.status,
            amount = entity.amount,
            actualPay = entity.actualPay,
            createTime = now,
            updateTime = now
        )
    }
}