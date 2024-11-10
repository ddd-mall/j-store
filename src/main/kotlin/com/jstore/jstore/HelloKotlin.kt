package com.jstore.jstore

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class HelloKotlin {
    @GetMapping("/")
    fun helloKotlin():String {
        return "Hello Kotlin"
    }
}

