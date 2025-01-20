package com.jstore.order.domain.stock

import com.jstore.common.errors.Errors

object StockErrors {
    val StockResourceNotFound : Errors = Errors("stock not found", "Stock.Resource.Notfound", 200)
    val StockInsufficient : Errors = Errors("Insufficient Stock", "Stock.Resource.Insufficient", 200)
    val IllegalState : Errors = Errors("Illegal stock state", "Stock.State.Illegal", 200)
}