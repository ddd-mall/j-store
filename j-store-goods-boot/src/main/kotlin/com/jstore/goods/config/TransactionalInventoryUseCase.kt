package com.jstore.goods.config

import com.jstore.goods.domain.inventory.CommodityCode
import com.jstore.goods.domain.inventory.StorageCreateCMD
import com.jstore.goods.service.InventoryUseCase
import java.math.BigDecimal
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 为库存的跨仓储写入提供单一事务入口；具体端口齐备时由部署模块装配。 */
class TransactionalInventoryUseCase(
    private val delegate: InventoryUseCase,
    transactionManager: PlatformTransactionManager,
) : InventoryUseCase {
    private val write = TransactionTemplate(transactionManager)

    override fun create(cmd: StorageCreateCMD) = tx { delegate.create(cmd) }
    override fun reserve(bizCode: String, commodityCode: CommodityCode, amount: BigDecimal) =
        tx { delegate.reserve(bizCode, commodityCode, amount) }
    override fun confirm(bizCode: String) = tx { delegate.confirm(bizCode) }
    override fun release(bizCode: String) = tx { delegate.release(bizCode) }
    override fun add(commodityCode: CommodityCode, quantity: BigDecimal) =
        tx { delegate.add(commodityCode, quantity) }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })
}
