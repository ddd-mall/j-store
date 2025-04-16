package com.jstore.order.service.acl

import com.jstore.order.domain.inventory.Inventory

interface OuterInventoryServiceACL {
    fun reserveAll(inventories: Iterable<Inventory>): Boolean
    fun confirmAll(inventories: Iterable<Inventory>): Boolean
    fun cancelAll(inventories: Iterable<Inventory>): Boolean
}
