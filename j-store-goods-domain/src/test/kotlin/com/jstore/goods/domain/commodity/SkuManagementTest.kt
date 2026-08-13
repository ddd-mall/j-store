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

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SkuManagementTest {
    @Test
    fun `draft supports updating and removing a sku`() {
        val draft = draftSpu(SkuImpl(SkuId(11), "红色", listOf(Attribute("color", "red"))))
        val replacement =
            SkuImpl(
                id = SkuId(11),
                skuName = "正红色",
                attributes = listOf(Attribute("color", "crimson")),
                merchantCode = "RED-01",
            )

        assertIs<Success<Unit>>(draft.updateSku(replacement))
        assertEquals("正红色", draft.skus.single().skuName)
        assertIs<Success<Unit>>(draft.removeSku(SkuId(11)))
        assertEquals(emptyList(), draft.skus)
    }

    @Test
    fun `published product rejects direct sku changes`() {
        val published =
            SpuImpl(
                id = SpuId(1),
                name = "T恤",
                _status = CommodityStatus.PUBLISHED,
                _skus = mutableListOf(SkuImpl(SkuId(11), "红色", emptyList())),
            )

        assertIs<Failure<*>>(published.addSku(SkuImpl(SkuId(12), "蓝色", emptyList())))
        assertIs<Failure<*>>(published.removeSku(SkuId(11)))
    }

    @Test
    fun `sku codes and barcodes must be unique inside a product`() {
        val draft =
            draftSpu(
                SkuImpl(
                    SkuId(11),
                    "红色",
                    listOf(Attribute("color", "red")),
                    merchantCode = "TSHIRT-01",
                    barcode = "690000000001",
                )
            )

        assertIs<Failure<*>>(
            draft.addSku(
                SkuImpl(
                    SkuId(12),
                    "蓝色",
                    listOf(Attribute("color", "blue")),
                    merchantCode = "TSHIRT-01",
                )
            )
        )
        assertIs<Failure<*>>(
            draft.addSku(
                SkuImpl(
                    SkuId(13),
                    "绿色",
                    listOf(Attribute("color", "green")),
                    barcode = "690000000001",
                )
            )
        )
    }

    private fun draftSpu(vararg skus: Sku): Spu =
        SpuImpl(
            id = SpuId(1),
            name = "T恤",
            _status = CommodityStatus.DRAFT,
            _skus = skus.toMutableList(),
        )
}
