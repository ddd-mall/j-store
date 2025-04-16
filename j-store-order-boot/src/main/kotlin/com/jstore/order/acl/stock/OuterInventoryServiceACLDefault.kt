package com.jstore.com.jstore.order.acl.stock

import com.jstore.common.errors.BusinessError
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.acl.OuterInventoryServiceACL
import org.springframework.stereotype.Service

@Service
class OuterInventoryServiceACLDefault : OuterInventoryServiceACL {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    override fun reserveAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError> {
        return Success(true)
    }

    override fun reserve(inventory: Inventory): Result<Boolean, BusinessError> {
        return Success(true)
    }

    override fun confirmAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError> {
        return Success(true)
    }

    override fun confirm(inventory: Inventory): Result<Boolean, BusinessError> {
        return Success(true)
    }

    override fun cancelAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError> {
        return Success(true)
    }

    override fun cancel(inventory: Inventory): Result<Boolean, BusinessError> {
        return Success(true)
    }
}