/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.goods.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactoryImpl
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import com.jstore.goods.service.CommodityService
import com.jstore.goods.service.CommodityUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

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

    @Bean
    @Primary
    fun transactionalCommodityUseCase(
        commodityService: CommodityService,
        transactionManager: PlatformTransactionManager,
    ): TransactionalCommodityUseCase =
        TransactionalCommodityUseCase(commodityService, commodityService, transactionManager)
}
