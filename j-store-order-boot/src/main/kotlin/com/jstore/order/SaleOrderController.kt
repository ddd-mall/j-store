package com.jstore.com.jstore.order

import com.jstore.order.saleorder.service.OrderService
import com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.order.saleorder.SaleOrder
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/saleOrder")
class SaleOrderController(private val saleOrderService: OrderService) {
    @PostMapping("/create")
    fun create(@RequestBody @Validated saleOrderCreateParam: SaleOrderCreateParam): SaleOrder {
        return saleOrderService.createSaleOrder(saleOrderCreateParam)
    }
}