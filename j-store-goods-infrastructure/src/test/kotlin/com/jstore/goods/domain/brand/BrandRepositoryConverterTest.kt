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

import com.jstore.goods.domain.brand.persistence.BrandPO
import kotlin.test.Test
import kotlin.test.assertEquals

class BrandRepositoryConverterTest {
    @Test
    fun `brand persistence preserves merchant localized name and status`() {
        val po =
            BrandPO(
                id = 9,
                merchantId = 7,
                name = """{"en-US":"Mountain","zh-CN":"山野"}""",
                normalizedName = "mountain",
                status = BrandStatus.INACTIVE,
            )

        val brand = BrandRepositoryImpl.Converter.toDomain(po)

        assertEquals(BrandId(9), brand.id)
        assertEquals(7, brand.merchantId.value)
        assertEquals("山野", brand.name["zh-CN"])
        assertEquals("mountain", brand.normalizedName)
        assertEquals(BrandStatus.INACTIVE, brand.status)
    }
}
