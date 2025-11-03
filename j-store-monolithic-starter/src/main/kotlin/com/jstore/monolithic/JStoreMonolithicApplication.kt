package com.jstore.monolithic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

/**
 * J-Store 单体应用启动器
 *
 * 功能说明：
 * 1. 集成订单模块和商品模块到单个应用中
 * 2. 支持多数据源配置，不同模块连接不同数据库
 * 3. 使用Spring Modulith进行模块化架构设计
 * 4. 提供统一的REST API入口
 */
@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.jstore.monolithic",     // 单体应用配置包
        "com.jstore.order",          // 订单模块
        "com.jstore.goods",          // 商品模块
        "com.jstore.common"          // 通用组件
    ]
)
class JStoreMonolithicApplication

fun main(args: Array<String>) {
    runApplication<JStoreMonolithicApplication>(*args)
}
