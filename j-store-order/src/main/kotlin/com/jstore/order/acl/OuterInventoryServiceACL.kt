package com.jstore.order.acl

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.order.domain.inventory.Inventory

interface OuterInventoryServiceACL {
    fun reserveAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError>
    fun reserve(inventory: Inventory): Result<Boolean, BusinessError>

    fun confirmAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError>
    fun confirm(inventory: Inventory): Result<Boolean, BusinessError>


    fun cancelAll(inventories: Iterable<Inventory>): Result<Boolean, BusinessError>
    fun cancel(inventory: Inventory): Result<Boolean, BusinessError>
}
