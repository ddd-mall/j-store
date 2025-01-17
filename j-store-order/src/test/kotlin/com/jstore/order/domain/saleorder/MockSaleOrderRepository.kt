package com.jstore.order.domain.saleorder

import com.jstore.common.framework.Page
import com.jstore.order.common.MockPage
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.framwork.AbstractMockRepository
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

class MockSaleOrderRepository: SaleOrderRepository, AbstractMockRepository<SaleOrderId, SaleOrder>() {

    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        return super.objList.filter { saleOrder -> saleOrder.buyerInfo.uid == uid }.toList()
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        val actualPageSize: Int = max(1, pageSize)
        val fromOffset: Int = max(0, currentPage - 1) *  actualPageSize
        val toOffSet: Int = min(super.objList.size, fromOffset + actualPageSize)

        if (fromOffset >= super.objList.size) {
            return MockPage(currentPage, 0 , listOf())
        }

        val saleOrders =
            super.objList.filter { saleOrder -> saleOrder.buyerInfo.uid == uid }.subList(fromOffset, toOffSet)
        return MockPage(currentPage, saleOrders.size, saleOrders)
    }


    override fun nextId(): SaleOrderId {
        return SaleOrderId(snowFlakSequence.nextId())
    }

    override fun copyAnEntity(nextId: SaleOrderId, entity: SaleOrder): SaleOrder {
        val now = LocalDateTime.now()
        return SaleOrder(
            id = nextId,
            buyerInfo = entity.buyerInfo,
            orderItems = entity.orderItems,
            deliveryAddressInfo = entity.deliveryAddressInfo,
            freightBills = entity.freightBills,
            positiveStatus = entity.positiveStatus,
            reverseStatus = entity.reverseStatus,
            amount = entity.amount,
            actualPay = entity.actualPay,
            createTime = now,
            updateTime = now
        )
    }
}