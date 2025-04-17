package com.jstore.com.jstore.order.controller

import com.jstore.order.domain.order.command.OrderCreateHandler
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCancelHandler
import com.jstore.order.domain.order.command.OrderCreateCMD
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/order")
class OrderController(
    private val orderCreateHandler: OrderCreateHandler,
    private val orderCancelHandler: OrderCancelHandler,
) {
    @PostMapping("/create")
    fun create(@RequestBody @Validated orderCreateCMD: OrderCreateCMD): Order {
        return orderCreateHandler.create(orderCreateCMD)
    }

    @PutMapping("/cancel")
    fun cancel(@RequestBody @Validated orderCancelCMD: OrderCancelCMD): Boolean {
        orderCancelHandler.handle(orderCancelCMD)
        return true
    }
}