package com.jstore

fun main() {
    var str: String? = "123"
    val finalStr = str?: throw IllegalArgumentException("str is null")
    println(finalStr)
}