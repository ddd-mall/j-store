package com.jstore.monolithic.controller

import com.jstore.goods.domain.commodity.persistence.SpuPOJpaRepository
import com.jstore.goods.domain.inventory.persistence.InventoryPOJpaRepository
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 演示多数据源使用的示例控制器
 */
@RestController
@RequestMapping("/demo")
class DemoController(
    private val orderRepository: OrderPOJpaRepository,
    private val inventoryRepository: InventoryPOJpaRepository,
    private val spuRepository: SpuPOJpaRepository
) {

    /**
     * 测试订单数据源
     */
    @GetMapping("/orders")
    fun getOrders(): Map<String, Any> {
        val orders = orderRepository.findAll()
        return mapOf(
            "dataSource" to "order",
            "count" to orders.size,
            "message" to "从订单数据库查询成功"
        )
    }

    /**
     * 测试商品库存数据源
     */
    @GetMapping("/inventory")
    fun getInventory(): Map<String, Any> {
        val inventory = inventoryRepository.findAll()
        return mapOf(
            "dataSource" to "goods",
            "count" to inventory.size,
            "message" to "从商品数据库查询库存成功"
        )
    }

    /**
     * 测试商品SPU数据源
     */
    @GetMapping("/spus")
    fun getSpus(): Map<String, Any> {
        val spus = spuRepository.findAll()
        return mapOf(
            "dataSource" to "goods",
            "count" to spus.size,
            "message" to "从商品数据库查询SPU成功"
        )
    }

    /**
     * 综合测试
     */
    @GetMapping("/status")
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "orderDataSource" to mapOf(
                "status" to "connected",
                "orderCount" to orderRepository.count()
            ),
            "goodsDataSource" to mapOf(
                "status" to "connected",
                "inventoryCount" to inventoryRepository.count(),
                "spuCount" to spuRepository.count()
            ),
            "message" to "多数据源连接正常"
        )
    }
}

