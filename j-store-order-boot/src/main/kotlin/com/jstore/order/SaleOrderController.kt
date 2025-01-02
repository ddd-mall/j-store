package com.jstore.com.jstore.order

import com.jstore.order.saleorder.service.SaleOrderFactory
import com.jstore.order.saleorder.service.SaleOrderCreateCMD
import com.jstore.order.saleorder.SaleOrder
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/saleOrder")
class SaleOrderController(private val saleSaleOrderFactory: SaleOrderFactory) {
    @PostMapping("/create")
    fun create(@RequestBody @Validated saleOrderCreateCMD: SaleOrderCreateCMD): SaleOrder {
        return saleSaleOrderFactory.createSaleOrder(saleOrderCreateCMD)
    }
}