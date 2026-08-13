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
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.SkuRemoveCmd
import com.jstore.goods.domain.commodity.comand.SkuUpdateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.kotlin.*

class CommodityServiceSkuManagementTest {
    private val repository = mock<SpuRepository>()
    private val factory = mock<SpuFactory>()
    private val service =
        CommodityService(
            spuFactory = factory,
            spuRepository = repository,
            domainEventPublisher = mock<DomainEventPublisher>(),
            snapshotFactory = mock<SpuSnapshotFactory>(),
            snapshotRepository = mock<SpuSnapshotRepository>(),
            goodsStyleRepository = mock<GoodsStyleRepository>(),
            goodsStyleFactory = mock<GoodsStyleFactory>(),
        )

    @Test
    fun `update sku delegates domain behavior and saves the draft`() {
        val draft = draft()
        val command =
            SkuUpdateCmd(
                spuId = draft.id,
                skuId = SkuId(11),
                skuName = "正红色",
                attributes = listOf(Attribute("color", "crimson")),
            )
        val replacement = SkuImpl(SkuId(11), "正红色", command.attributes)
        whenever(repository.findById(draft.id)).thenReturn(draft)
        whenever(factory.createSku(command)).thenReturn(replacement)
        whenever(repository.save(draft)).thenReturn(draft)

        val result = service.updateSku(command)

        assertIs<Success<Spu>>(result)
        assertEquals("正红色", draft.skus.single().skuName)
        verify(repository).save(draft)
    }

    @Test
    fun `remove sku delegates domain behavior and saves the draft`() {
        val draft = draft()
        whenever(repository.findById(draft.id)).thenReturn(draft)
        whenever(repository.save(draft)).thenReturn(draft)

        val result = service.removeSku(SkuRemoveCmd(draft.id, SkuId(11)))

        assertIs<Success<Spu>>(result)
        assertEquals(emptyList(), draft.skus)
        verify(repository).save(draft)
    }

    private fun draft(): Spu =
        SpuImpl(
            id = SpuId(1),
            name = "T恤",
            _status = CommodityStatus.DRAFT,
            _skus = mutableListOf(SkuImpl(SkuId(11), "红色", listOf(Attribute("color", "red")))),
        )
}
