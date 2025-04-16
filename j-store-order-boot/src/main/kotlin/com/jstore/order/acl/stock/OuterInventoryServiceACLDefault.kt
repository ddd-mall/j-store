package com.jstore.com.jstore.order.acl.stock

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.service.acl.OuterInventoryServiceACL
import org.springframework.stereotype.Service

@Service
class OuterInventoryServiceACLDefault(
) : OuterInventoryServiceACL {
    private val log: Logger = LoggerFactory.getLogger(this::class)
    override fun reserveAll(inventories: Iterable<Inventory>): Boolean {
        return true
    }

    override fun confirmAll(inventories: Iterable<Inventory>): Boolean {
        return true
    }

    override fun cancelAll(inventories: Iterable<Inventory>): Boolean {
        return true
    }
}