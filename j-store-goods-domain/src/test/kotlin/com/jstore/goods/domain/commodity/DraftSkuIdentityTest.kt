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
package com.jstore.goods.domain.commodity

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DraftSkuIdentityTest {
    @Test
    fun `draft copy cannot be published as an independent product`() {
        val source = publishedSpu(SkuImpl(SkuId(101), "红色", emptyList()))
        val draft =
            assertIs<Success<Spu>>(SpuFactoryImpl(SnowFlakSequence()).createDraftCopy(source)).value

        assertIs<Failure<*>>(draft.publish())
    }

    @Test
    fun `draft owns distinct sku identities and remembers their published sources`() {
        val sourceSku =
            SkuImpl(
                id = SkuId(101),
                skuName = "红色 / XL",
                attributes = listOf(Attribute("color", "red"), Attribute("size", "XL")),
            )
        val source = publishedSpu(sourceSku)

        val draft =
            assertIs<Success<Spu>>(SpuFactoryImpl(SnowFlakSequence()).createDraftCopy(source)).value

        assertNotEquals(sourceSku.id, draft.skus.single().id)
        assertEquals(sourceSku.id, draft.skus.single().sourceSkuId)
        assertEquals(sourceSku.skuName, draft.skus.single().skuName)
        assertEquals(sourceSku.attributes, draft.skus.single().attributes)
    }

    @Test
    fun `publishing a draft restores existing sku ids and keeps new sku ids`() {
        val source = publishedSpu(SkuImpl(SkuId(101), "红色", listOf(Attribute("color", "red"))))
        val draft =
            assertIs<Success<Spu>>(SpuFactoryImpl(SnowFlakSequence()).createDraftCopy(source)).value
        val newSku = SkuImpl(SkuId(902), "蓝色", listOf(Attribute("color", "blue")))
        assertIs<Success<Unit>>(draft.addSku(newSku))

        assertIs<Success<Unit>>(source.mergeFromDraft(draft))

        assertEquals(listOf(SkuId(101), SkuId(902)), source.skus.map { it.id })
        source.skus.forEach { assertNull(it.sourceSkuId) }
        val event = assertIs<CommodityPublishedEvent>(source.pendingDomainEvents().single())
        assertEquals(source.version, event.snapshotVersion)
    }

    private fun publishedSpu(vararg skus: Sku): Spu =
        SpuImpl(
            id = SpuId(1),
            merchantId = MerchantId(7),
            name = "T恤",
            _status = CommodityStatus.PUBLISHED,
            _skus = skus.toMutableList(),
            _version = 3,
        )
}
