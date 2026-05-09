package com.jstore.goods.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactoryImpl
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import com.jstore.goods.service.CommodityService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoodsBootConfiguration {

    @Bean
    fun spuFactory(snowFlakSequence: SnowFlakSequence): SpuFactory {
        return SpuFactoryImpl(snowFlakSequence)
    }

    @Bean
    fun goodsStyleFactory(snowFlakSequence: SnowFlakSequence): GoodsStyleFactory {
        return GoodsStyleFactoryImpl(snowFlakSequence)
    }

    @Bean
    fun spuSnapshotFactory(snowFlakSequence: SnowFlakSequence): SpuSnapshotFactory {
        return SpuSnapshotFactoryImpl(snowFlakSequence)
    }

    @Bean
    fun commodityService(
        spuFactory: SpuFactory,
        spuRepository: SpuRepository,
        domainEventPublisher: DomainEventPublisher,
        snapshotFactory: SpuSnapshotFactory,
        snapshotRepository: SpuSnapshotRepository,
        goodsStyleRepository: GoodsStyleRepository,
        goodsStyleFactory: GoodsStyleFactory,
    ): CommodityService {
        return CommodityService(
            spuFactory = spuFactory,
            spuRepository = spuRepository,
            domainEventPublisher = domainEventPublisher,
            snapshotFactory = snapshotFactory,
            snapshotRepository = snapshotRepository,
            goodsStyleRepository = goodsStyleRepository,
            goodsStyleFactory = goodsStyleFactory,
        )
    }
}
