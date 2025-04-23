package com.jstore.goods.boot.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RefreshScope
@RestController
@RequestMapping("/test")
class TestController {

    var content: String = "test"
        @Value("\${test.content:}")
        set

    @GetMapping("/get")
    fun get() : ResponseEntity<String> {
        return ResponseEntity.ok(content)
    }
}

