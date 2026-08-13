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
package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.goods.domain.brand.Brand
import com.jstore.goods.domain.brand.BrandErrors
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.brand.BrandRepository
import com.jstore.goods.domain.commodity.CommodityStatus
import com.jstore.goods.domain.commodity.GoodsStyleFactory
import com.jstore.goods.domain.commodity.GoodsStyleRepository
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.commodity.SkuId
import com.jstore.goods.domain.commodity.SkuImpl
import com.jstore.goods.domain.commodity.SpuFactory
import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.commodity.SpuImpl
import com.jstore.goods.domain.commodity.SpuRepository
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import com.jstore.goods.domain.content.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommodityServiceBrandValidationTest {
    private val brandRepository = mock<BrandRepository>()
    private val spuRepository = mock<SpuRepository>()
    private val service =
        CommodityService(
            spuFactory = mock<SpuFactory>(),
            spuRepository = spuRepository,
            domainEventPublisher = mock<DomainEventPublisher>(),
            snapshotFactory = mock<SpuSnapshotFactory>(),
            snapshotRepository = mock<SpuSnapshotRepository>(),
            goodsStyleRepository = mock<GoodsStyleRepository>(),
            goodsStyleFactory = mock<GoodsStyleFactory>(),
            brandRepository = brandRepository,
        )

    @Test
    fun `save rejects a missing brand reference`() {
        val result = service.createOrUpdate(command(BrandId(9)))

        assertEquals(BrandErrors.NOT_FOUND, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `save rejects a brand owned by another merchant`() {
        whenever(brandRepository.findById(BrandId(9)))
            .thenReturn(
                Brand(
                    BrandId(9),
                    MerchantId(8),
                    LocalizedText.of("zh-CN" to "其他商户品牌"),
                )
            )

        val result = service.createOrUpdate(command(BrandId(9)))

        assertEquals(BrandErrors.MERCHANT_MISMATCH, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `save rejects an inactive brand`() {
        val brand =
            Brand(
                BrandId(9),
                MerchantId(7),
                LocalizedText.of("zh-CN" to "停用品牌"),
            )
        brand.deactivate()
        whenever(brandRepository.findById(brand.id)).thenReturn(brand)

        val result = service.createOrUpdate(command(brand.id))

        assertEquals(BrandErrors.INACTIVE, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `publish rejects a brand that became inactive after draft save`() {
        val brand =
            Brand(
                BrandId(9),
                MerchantId(7),
                LocalizedText.of("zh-CN" to "停用品牌"),
            )
        brand.deactivate()
        val draft =
            SpuImpl(
                id = SpuId(1),
                merchantId = MerchantId(7),
                name = "商品",
                brandId = brand.id,
                _status = CommodityStatus.DRAFT,
                _skus = mutableListOf(SkuImpl(SkuId(2), "默认规格", emptyList())),
            )
        whenever(brandRepository.findById(brand.id)).thenReturn(brand)
        whenever(spuRepository.findById(draft.id)).thenReturn(draft)

        val result = service.publish(draft.id)

        assertEquals(BrandErrors.INACTIVE, assertIs<Failure<*>>(result).error)
    }

    private fun command(brandId: BrandId) =
        CommodityCreateCmd(
            spuId = null,
            merchantId = 7,
            spuName = "商品",
            brandId = brandId,
        )
}
