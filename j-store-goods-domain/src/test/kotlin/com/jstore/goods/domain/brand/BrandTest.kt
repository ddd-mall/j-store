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
package com.jstore.goods.domain.brand

import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.content.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BrandTest {
    @Test
    fun `brand id must be positive`() {
        assertFailsWith<IllegalArgumentException> { BrandId(0) }
        assertFailsWith<IllegalArgumentException> { BrandId(-1) }
    }

    @Test
    fun `brand owns its localized name and activation lifecycle`() {
        val brand =
            Brand(
                id = BrandId(1),
                merchantId = MerchantId(7),
                name = LocalizedText.of("zh-CN" to "旧名称"),
            )

        brand.rename(LocalizedText.of("zh-CN" to "新名称", "en-US" to "New Name"))
        brand.deactivate()

        assertEquals("新名称", brand.name["zh-CN"])
        assertEquals("new name", brand.normalizedName)
        assertEquals(BrandStatus.INACTIVE, brand.status)

        brand.activate()
        assertEquals(BrandStatus.ACTIVE, brand.status)
    }
}
