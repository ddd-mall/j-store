package com.jstore.com.jstore.order.saleorder

import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPOJpaRepository
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPOJpaRepository
import com.jstore.common.framework.Page
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.order.saleorder.SaleOrderRepository
import org.springframework.stereotype.Repository

@Repository
open class SaleOrderRepositoryImpl(
    private val saleOrderPOJpaRepository: SaleOrderPOJpaRepository,
    private val saleOrderItemPOJpaRepository: SaleOrderItemPOJpaRepository
) : SaleOrderRepository {

    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        val saleOrderPOS = saleOrderPOJpaRepository.findSaleOrderPOSByUid(uid)
        if (saleOrderPOS.isEmpty()) {
            return listOf()
        }
        val saleOrderIdList = saleOrderPOS.stream().map { o -> o.saleOrderId!! }.toList()
        val saleOrderItemPOS = saleOrderItemPOJpaRepository.findSaleOrderItemPOSBySaleOrderIdIsIn(saleOrderIdList)
        return POConvertor.pos2Entities(saleOrderPOS, saleOrderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        TODO("Not yet implemented")
    }

    override fun save(entity: SaleOrder): SaleOrder {
        TODO("Not yet implemented")
    }

    override fun findById(id: SaleOrderId): SaleOrder? {
        TODO("Not yet implemented")
    }
}

object POConvertor {
    fun po2Entity(saleOrderPO: SaleOrderPO, saleOrderItemPOList: MutableCollection<SaleOrderItemPO> ):SaleOrder {
        TODO()
    }

    fun pos2Entities(saleOrderPOs: MutableCollection<SaleOrderPO>, saleOrderItemPOs: MutableCollection<SaleOrderItemPO>): List<SaleOrder> {
        val itemMap = saleOrderItemPOs.groupBy { item -> item.saleOrderId!! }
        TODO()
    }
}