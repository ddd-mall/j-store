package com.jstore.com.jstore.order

import com.jstore.order.saleorder.SaleOrderHandler
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.SaleOrderCreateCMD
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/saleOrder")
class SaleOrderController(private val saleOrderHandler: SaleOrderHandler) {
    @PostMapping("/create")
    fun create(@RequestBody @Validated saleOrderCreateCMD: SaleOrderCreateCMD): SaleOrder {
        return saleOrderHandler.create(saleOrderCreateCMD)
    }
}