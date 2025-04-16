package com.jstore.order.acl.stock

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.string.StringUtils
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.domain.inventory.InventoryErrors.InventoryInsufficient
import com.jstore.order.domain.inventory.InventoryErrors.InventoryNotFound
import com.jstore.order.domain.inventory.InventoryErrors.IllegalState
import com.jstore.order.domain.inventory.InventoryStatus

import com.jstore.order.domain.acl.GoodsId
import com.jstore.order.domain.acl.OuterInventoryServiceACL
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

    override fun reserveAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError> {
        inventories.forEach(this::reserve)
        return Success(true)
    }

    override fun reserve(inventory: Inventory): Result<Boolean, BusinessError> {
        stockMap[inventory.goodsId]?.also { stock ->
            if (stock.get() < inventory.quantity) {
                throw InventoryInsufficient.msg("the corresponding stocks of ${inventory.goodsId} not enough")
            }
            synchronized(stock) {
                if (stock.get() < inventory.quantity) {
                    throw InventoryInsufficient.msg("the corresponding stocks of ${inventory.goodsId} not enough")
                }
                val afterSub = stock.accumulateAndGet(inventory.quantity) { pre, sub ->
                    pre.subtract(sub)
                }
                if (afterSub.toDouble() >= 0) {
                    stock.compareAndSet(stock.get(), afterSub)
                } else {
                    throw InventoryInsufficient.msg("failed to reserve ${inventory.goodsId} corresponding stocks")
                }
                log.info("goods ${inventory.goodsId}'s stock has been reserved with quantity ${inventory.quantity}, remind $afterSub")
            }
        } ?: throw InventoryNotFound.msg("${inventory.goodsId} corresponding stocks not found")

        val outerStockId = snowFlakSequence.nextId().toString()
        stockPOMap[outerStockId] = StockPO(
            stockId = outerStockId,
            goodsId = inventory.goodsId,
            quantity = inventory.quantity,
            status = InventoryStatus.RESERVED
        )
        inventory.outerInventoryId = outerStockId
        inventory.reserve()
        return Success(true)
    }

    override fun confirmAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError> {
        inventories.forEach(this::confirm)
        return Success(true)
    }

    override fun confirm(inventory: Inventory): Result<Boolean, BusinessError> {
        if (inventory.outerInventoryId.isEmpty()) {
            throw  IllegalState.msg("外部库存ID为空")
        }
        val stockPO = stockPOMap[inventory.outerInventoryId] ?:
        throw InventoryNotFound.msg("${inventory.outerInventoryId} corresponding stock not found")
        stockPO.status = InventoryStatus.CONFIRMED
        inventory.confirm()
        return Success(true)
    }

    override fun cancelAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError> {
        inventories.forEach(this::cancel)
        return Success(true)
    }

    override fun cancel(inventory: Inventory): Result<Boolean, BusinessError> {
        if (StringUtils.isEmpty(inventory.outerInventoryId)) {
            return Failure(CommonBusinessError.ILLEGAL_STATE)
        }
        val stockPO: StockPO = stockPOMap[inventory.outerInventoryId] ?: return Failure(CommonBusinessError.OBJECT_NOT_FOUNT)

        when (stockPO.status) {
            InventoryStatus.CREATED -> return Failure(CommonBusinessError.ILLEGAL_STATE)
            InventoryStatus.RESERVED -> {
                val atomicReference = stockMap[stockPO.goodsId] ?: throw InventoryNotFound
                val stockQuantity = atomicReference.get()
                atomicReference.compareAndSet(stockQuantity, stockQuantity.add(stockPO.quantity))
                stockMap[stockPO.goodsId] = atomicReference
                stockPO.status = InventoryStatus.CANCELED
                return Success(true)
            }
            InventoryStatus.CONFIRMED -> {
                stockPO.status = InventoryStatus.CANCELED
                return Success(true)
            }

            InventoryStatus.CANCELED -> return Success(true)
        }
    }

    private class StockPO(
        val stockId: String,
        val goodsId: GoodsId,
        val quantity: BigDecimal,
        var status: InventoryStatus
    ) {

    }
}