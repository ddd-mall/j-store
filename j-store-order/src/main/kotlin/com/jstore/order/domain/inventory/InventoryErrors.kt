package com.jstore.order.domain.inventory

import com.jstore.common.errors.Errors

object InventoryErrors {
    val InventoryNotFound : Errors = Errors("Inventory not found", "Inventory.Resource.Notfound", 404)
    val InventoryInsufficient : Errors = Errors("Insufficient Inventory", "Inventory.Resource.Insufficient", 200)
    val IllegalState : Errors = Errors("Illegal Inventory state", "Inventory.State.Illegal", 200)
}