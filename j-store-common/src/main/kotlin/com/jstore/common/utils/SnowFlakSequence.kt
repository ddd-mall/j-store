package com.jstore.common.utils

class SnowFlakSequence {
    companion object {

    }
}

class Demo private constructor(private val value: Int) {
    companion object {
        operator fun invoke(): Demo {
            return Demo(10)
        }
    }

    fun add(a: Int, b: Int): Int {
        return value + a + b;
    }
}

fun main(args: Array<String>) {
    val demo = Demo()
    println(demo.add(10, 20))
    Demo::class.constructors.forEach {

    }
}