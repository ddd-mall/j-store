package com.jstore.order

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache


class DemoData(
    val date: String,

) {
    val id: Int = date.hashCode()
}
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val cache: LoadingCache<String, DemoData> = CacheBuilder.newBuilder()
        .weakValues()
        .build<String, DemoData>(
            CacheLoader<String, DemoData>.from {

            }
        )
}