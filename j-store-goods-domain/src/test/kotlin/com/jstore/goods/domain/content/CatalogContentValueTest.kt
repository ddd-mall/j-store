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
package com.jstore.goods.domain.content

import com.jstore.goods.domain.category.Category
import com.jstore.goods.domain.category.CategoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogContentValueTest {
    @Test
    fun `localized text normalizes locale keys and provides deterministic fallback`() {
        val text = LocalizedText.of("ZH_cn" to "咖啡", "en-US" to "Coffee")

        assertEquals("咖啡", text["zh-CN"])
        assertEquals("Coffee", text.resolve("en-US"))
        assertEquals("Coffee", text.resolve("fr-FR"))
    }

    @Test
    fun `localized text rejects empty content`() {
        assertFailsWith<IllegalArgumentException> { LocalizedText(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { LocalizedText.of("zh-CN" to " ") }
    }

    @Test
    fun `media asset validates stable key and position`() {
        assertFailsWith<IllegalArgumentException> {
            MediaAsset("", MediaType.IMAGE, MediaRole.PRIMARY, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaAsset("oss-key", MediaType.IMAGE, MediaRole.GALLERY, -1)
        }
    }

    @Test
    fun `category references parent by id`() {
        val category =
            Category(
                id = CategoryId(2),
                name = LocalizedText.of("zh-CN" to "咖啡豆"),
                parentId = CategoryId(1),
            )

        assertEquals(CategoryId(1), category.parentId)
    }
}
