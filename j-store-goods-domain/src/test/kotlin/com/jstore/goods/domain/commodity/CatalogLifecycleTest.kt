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

import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogLifecycleTest {
    @Test
    fun `published catalog product can be archived without store sale semantics`() {
        val product =
            SpuImpl(
                id = SpuId(1),
                merchantId = MerchantId(7),
                name = "咖啡",
                _status = CommodityStatus.DRAFT,
                _skus = mutableListOf(SkuImpl(SkuId(11), "250g", emptyList())),
            )

        assertIs<Success<Unit>>(product.publish())
        assertEquals(CommodityStatus.PUBLISHED, product.status)
        assertIs<Success<Unit>>(product.archive())
        assertEquals(CommodityStatus.ARCHIVED, product.status)
    }
}
