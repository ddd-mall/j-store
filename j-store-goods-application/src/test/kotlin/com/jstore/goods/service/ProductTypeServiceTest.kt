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

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.ProductType
import com.jstore.goods.domain.producttype.ProductTypeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProductTypeServiceTest {
    @Test
    fun `save creates and persists a merchant product type`() {
        val repository = mock<ProductTypeRepository>()
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as ProductType }
        val service = ProductTypeService(SnowFlakSequence(), repository)

        val result =
            service.save(
                ProductTypeSaveCommand(
                    merchantId = MerchantId(7),
                    name = LocalizedText.of("zh-CN" to "服装"),
                    definitions = emptyList(),
                )
            )

        val saved = assertIs<Success<ProductType>>(result).value
        assertEquals(MerchantId(7), saved.merchantId)
        verify(repository).save(saved)
    }
}
