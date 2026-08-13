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
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.kotlin.*

class CommodityServiceDraftStyleFlowTest {
    private val spuRepository = mock<SpuRepository>()
    private val snapshotRepository = mock<SpuSnapshotRepository>()
    private val styleRepository = mock<GoodsStyleRepository>()
    private val styleFactory = mock<GoodsStyleFactory>()
    private val service =
        CommodityService(
            spuFactory = SpuFactoryImpl(SnowFlakSequence()),
            spuRepository = spuRepository,
            domainEventPublisher = mock<DomainEventPublisher>(),
            snapshotFactory = mock<SpuSnapshotFactory>(),
            snapshotRepository = snapshotRepository,
            goodsStyleRepository = styleRepository,
            goodsStyleFactory = styleFactory,
            brandRepository = mock(),
        )

    @Test
    fun `published style cannot be edited without a draft`() {
        val published = publishedSpu()
        whenever(spuRepository.findById(published.id)).thenReturn(published)

        val result =
            service.saveGoodsStyle(
                GoodsStyleSaveCmd(published.id, listOf("main"), "<p>detail</p>", emptyMap())
            )

        assertIs<Failure<*>>(result)
        verify(styleRepository, never()).save(any())
    }

    @Test
    fun `creating a draft copies style and remaps sku image keys`() {
        val source = publishedSpu()
        val sourceStyle =
            GoodsStyleImpl(
                GoodsStyleId(2),
                source.id,
                mutableListOf("main"),
                "<p>detail</p>",
                mutableMapOf(SkuId(11) to listOf("red")),
            )
        whenever(spuRepository.findById(source.id)).thenReturn(source)
        whenever(spuRepository.findDraftBySourceSpuId(source.id)).thenReturn(null)
        whenever(spuRepository.save(any())).thenAnswer { it.arguments[0] as Spu }
        whenever(styleRepository.findBySpuId(source.id)).thenReturn(sourceStyle)
        whenever(styleFactory.create(any(), any(), any(), any())).thenAnswer { invocation ->
            GoodsStyleImpl(
                GoodsStyleId(3),
                invocation.getArgument(0),
                invocation.getArgument<List<String>>(1).toMutableList(),
                invocation.getArgument(2),
                invocation.getArgument<Map<SkuId, List<String>>>(3).toMutableMap(),
            )
        }
        whenever(styleRepository.save(any())).thenAnswer { it.arguments[0] as GoodsStyle }

        val draft = assertIs<Success<Spu>>(service.getDraft(source.id)).value

        val captured = argumentCaptor<GoodsStyle>()
        verify(styleRepository).save(captured.capture())
        assertEquals(draft.id, captured.firstValue.spuId)
        assertEquals(
            mapOf(draft.skus.single().id to listOf("red")),
            captured.firstValue.skuImages,
        )
    }

    private fun publishedSpu(): Spu =
        SpuImpl(
            id = SpuId(1),
            merchantId = MerchantId(7),
            name = "T恤",
            _status = CommodityStatus.PUBLISHED,
            _skus = mutableListOf(SkuImpl(SkuId(11), "红色", listOf(Attribute("color", "red")))),
            _version = 3,
        )
}
