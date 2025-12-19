//package com.jstore.order.domain.inventory.command
//
//import com.jstore.order.acl.OuterInventoryServiceACL
//import com.jstore.order.domain.inventory.InventoryFactory
//import com.jstore.order.domain.inventory.InventoryRepository
//import com.jstore.order.domain.order.Order
//import com.jstore.order.domain.order.OrderImpl
//import org.springframework.stereotype.Service
//
//class BatchCreateAndReserveCMD(
//    val orderImpl: Order,
//)
//
//
//@Service
//class BatchCreateAndReserveHandler(
//    private val inventoryFactory: InventoryFactory,
//    private val outerInventoryServiceACL: OuterInventoryServiceACL,
//    private val inventoryRepository: InventoryRepository,
//) {
//
//    fun handle(cmd: BatchCreateAndReserveCMD) {
//        val inventories = getInventoryBatchCreateCMD(cmd.orderImpl).map(inventoryFactory::create)
//        outerInventoryServiceACL.reserveAll(inventories)
//        inventories.forEach { inventory -> inventory.reserve() }
//        inventoryRepository.saveAll(inventories)
//    }
//
//    private fun getInventoryBatchCreateCMD(orderImpl: Order): List<CreateInventoryCMD> {
//        return orderImpl.orderItemImpls.map { orderItem ->
//            CreateInventoryCMD(
//                orderImpl.id,
//                orderItem.goodsId,
//                orderItem.quantity
//            )
//        }
//    }
//}