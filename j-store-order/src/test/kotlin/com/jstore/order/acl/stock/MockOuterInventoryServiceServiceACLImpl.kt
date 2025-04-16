package com.jstore.order.acl.stock

import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.string.StringUtils
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.domain.inventory.InventoryErrors.InventoryInsufficient
import com.jstore.order.domain.inventory.InventoryErrors.InventoryNotFound
import com.jstore.order.domain.inventory.InventoryErrors.IllegalState
import com.jstore.order.domain.inventory.InventoryStatus

import com.jstore.order.service.acl.GoodsId
import com.jstore.order.service.acl.OuterInventoryServiceACL
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class MockOuterInventoryServiceServiceACLImpl : OuterInventoryServiceACL {
    private val stockMap: ConcurrentHashMap<GoodsId, AtomicReference<BigDecimal>> = ConcurrentHashMap()
    private val stockPOMap: ConcurrentHashMap<String, StockPO> = ConcurrentHashMap()

    private val log = LoggerFactory.getLogger(this::class)

    init {
        stockMap[GoodsId(1, 1)] = AtomicReference(BigDecimal(2))
        stockMap[GoodsId(2, 2)] = AtomicReference(BigDecimal(1))
    }

    override fun reserveAll(inventories: Iterable<Inventory>): Boolean {
        inventories.forEach { require ->
            stockMap[require.goodsId]?.also { stock ->
                if (stock.get() < require.quantity) {
                    throw InventoryInsufficient.msg("the corresponding stocks of ${require.goodsId} not enough")
                }
                synchronized(stock) {
                    if (stock.get() < require.quantity) {
                        throw InventoryInsufficient.msg("the corresponding stocks of ${require.goodsId} not enough")
                    }
                    val afterSub = stock.accumulateAndGet(require.quantity) { pre, sub ->
                        pre.subtract(sub)
                    }
                    if (afterSub.toDouble() >= 0) {
                        stock.compareAndSet(stock.get(), afterSub)
                    } else {
                        throw InventoryInsufficient.msg("failed to reserve ${require.goodsId} corresponding stocks")
                    }
                    log.info("goods ${require.goodsId}'s stock has been reserved with quantity ${require.quantity}, remind $afterSub")
                }
            } ?: throw InventoryNotFound.msg("${require.goodsId} corresponding stocks not found")

            val outerStockId = snowFlakSequence.nextId().toString()
            stockPOMap[outerStockId] = StockPO(
                stockId = outerStockId,
                goodsId = require.goodsId,
                quantity = require.quantity,
                status = InventoryStatus.RESERVED
            )
            require.outerInventoryId = outerStockId
            require.reserve()
        }

        return true
    }

    override fun confirmAll(inventories: Iterable<Inventory>): Boolean {
        inventories.forEach { require ->
            if (require.outerInventoryId.isEmpty()) {
                throw  IllegalState.msg("外部库存ID为空")
            }
            val stockPO = stockPOMap[require.outerInventoryId] ?:
            throw InventoryNotFound.msg("${require.outerInventoryId} corresponding stock not found")
            stockPO.status = InventoryStatus.CONFIRMED
            require.confirm()
        }
        return true
    }

    override fun cancelAll(inventories: Iterable<Inventory>): Boolean {
        for (inventory in inventories) {


            if (StringUtils.isEmpty(inventory.outerInventoryId)) {
                continue
            }
            val stockPO: StockPO = stockPOMap[inventory.outerInventoryId] ?: continue

            when (stockPO.status) {
                InventoryStatus.CREATED -> continue
                InventoryStatus.RESERVED -> {
                    val atomicReference = stockMap[stockPO.goodsId] ?: throw InventoryNotFound
                    val stockQuantity = atomicReference.get()
                    atomicReference.compareAndSet(stockQuantity, stockQuantity.add(stockPO.quantity))
                    stockMap[stockPO.goodsId] = atomicReference
                    return true
                }
                InventoryStatus.CONFIRMED -> {
                    stockPO.status = InventoryStatus.RESERVED
                    return true
                }
            }
        }

        return true
    }

    private class StockPO(
        val stockId: String,
        val goodsId: GoodsId,
        val quantity: BigDecimal,
        var status: InventoryStatus
    ) {

    }
}