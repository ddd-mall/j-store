package com.jstore.com.jstore.order.config

import com.jstore.com.jstore.order.saleorder.SaleOrderRepositoryImpl
import com.jstore.order.acl.goods.GoodsService
import com.jstore.order.saleorder.SaleOrderRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class OrderBootConfiguration {
    @Bean
    open fun SaleOrderRepository(): SaleOrderRepository {
        return SaleOrderRepositoryImpl()
    }

    @Bean
    open fun goodsService(): GoodsService {
        TODO("goods Service waiting for implementation")
    }


}