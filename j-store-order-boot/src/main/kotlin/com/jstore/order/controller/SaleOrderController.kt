package com.jstore.com.jstore.order.controller

import com.jstore.order.service.SaleOrderService
import com.jstore.order.domain.saleorder.SaleOrder
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCmd
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/saleOrder")
class SaleOrderController(private val saleOrderService: SaleOrderService) {
    @PostMapping("/create")
    fun create(@RequestBody @Validated normalSaleOrderCreateCMD: NormalSaleOrderCreateCmd): SaleOrder {
        return saleOrderService.create(normalSaleOrderCreateCMD)
    }
}