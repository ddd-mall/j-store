package com.jstore.order.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.Page
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import com.jstore.order.common.MockPage
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

class MockSaleOrderRepository: SaleOrderRepository {
    companion object {
        private var nextIdValue: AtomicLong = AtomicLong(0)
        private val saleOrderList: MutableList<SaleOrder> = ArrayList()
        private val idxSaleOrderIdIndex: MutableMap<SaleOrderId, Int> = ConcurrentHashMap()
        private val snowFlakSequence: SnowFlakSequence = SnowFlakSequence.SnowFlakSequence()
    }

    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        return saleOrderList.filter { saleOrder -> saleOrder.buyerInfo.uid == uid }.toList()
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        val actualPageSize: Int = max(1, pageSize)
        val fromOffset: Int = max(0, currentPage - 1) *  actualPageSize
        val toOffSet: Int = min(saleOrderList.size, fromOffset + actualPageSize)

        if (fromOffset >= saleOrderList.size) {
            return MockPage(currentPage, 0 , listOf())
        }

        val saleOrders =
            saleOrderList.filter { saleOrder -> saleOrder.buyerInfo.uid == uid }.subList(fromOffset, toOffSet)
        return MockPage(currentPage, saleOrders.size, saleOrders)
    }

    override fun save(entity: SaleOrder): SaleOrder {
        val now = LocalDateTime.now()
        if (null == entity.getId()) {
            val saleOrder = SaleOrder(
                SaleOrderId(snowFlakSequence.nextId()),
                entity.buyerInfo,
                entity.orderItems,
                entity.deliveryAddressInfo,
                entity.freightBills,
                entity.positiveStatus,
                entity.reverseStatus,
                entity.amount,
                entity.actualPay,
                now,
                now
            )
            idxSaleOrderIdIndex.putIfAbsent(saleOrder.getId()!!, saleOrderList.size)
            saleOrderList.add(saleOrder)
            return saleOrder
        } else {
            idxSaleOrderIdIndex[entity.getId()]?.let { index -> saleOrderList[index] = entity }
            return entity
        }
    }

    override fun findById(id: SaleOrderId): SaleOrder {
        val index = idxSaleOrderIdIndex[id]
            ?: throw CommonErrors.RESOURCE_NOT_FOUND.withMsg("没有找到id为 $id 的订单")
        return saleOrderList[index]
    }
}