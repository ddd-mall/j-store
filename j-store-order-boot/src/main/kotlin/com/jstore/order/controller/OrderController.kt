package com.jstore.com.jstore.order.controller

import com.jstore.order.service.OrderCreationService
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.command.OrderCreateCmd
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/order")
class OrderController(private val orderCreationService: OrderCreationService) {
    @PostMapping("/create")
    fun create(@RequestBody @Validated orderCreateCMD: OrderCreateCmd): Order {
        return orderCreationService.create(orderCreateCMD)
    }
}