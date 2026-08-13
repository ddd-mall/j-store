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
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.brand.Brand
import com.jstore.goods.domain.brand.BrandErrors
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.brand.BrandRepository
import com.jstore.goods.domain.brand.BrandStatus
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.content.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrandServiceTest {
    private val repository = mock<BrandRepository>()
    private val sequence = mock<SnowFlakSequence>()
    private val service = BrandService(sequence, repository)

    @Test
    fun `save creates an active merchant brand`() {
        whenever(sequence.nextId()).thenReturn(1)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as Brand }

        val result =
            service.save(
                BrandSaveCommand(
                    merchantId = MerchantId(7),
                    name = LocalizedText.of("zh-CN" to "示例品牌"),
                )
            )

        val saved = assertIs<Success<Brand>>(result).value
        assertEquals(MerchantId(7), saved.merchantId)
        assertEquals(BrandStatus.ACTIVE, saved.status)
        verify(repository).save(saved)
    }

    @Test
    fun `merchant cannot update another merchant brand`() {
        whenever(repository.findById(BrandId(9)))
            .thenReturn(
                Brand(
                    BrandId(9),
                    MerchantId(8),
                    LocalizedText.of("zh-CN" to "其他品牌"),
                )
            )

        val result =
            service.save(
                BrandSaveCommand(
                    id = BrandId(9),
                    merchantId = MerchantId(7),
                    name = LocalizedText.of("zh-CN" to "越权修改"),
                )
            )

        assertEquals(BrandErrors.MERCHANT_MISMATCH, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `merchant can deactivate its brand`() {
        val brand =
            Brand(
                BrandId(9),
                MerchantId(7),
                LocalizedText.of("zh-CN" to "示例品牌"),
            )
        whenever(repository.findById(brand.id)).thenReturn(brand)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as Brand }

        val result = service.deactivate(BrandStatusCommand(brand.id, brand.merchantId))

        assertEquals(BrandStatus.INACTIVE, assertIs<Success<Brand>>(result).value.status)
    }

    @Test
    fun `merchant cannot create duplicate normalized brand name`() {
        whenever(repository.findByMerchantIdAndNormalizedName(MerchantId(7), "example"))
            .thenReturn(
                Brand(
                    BrandId(3),
                    MerchantId(7),
                    LocalizedText.of("en-US" to "Example"),
                )
            )

        val result =
            service.save(
                BrandSaveCommand(
                    merchantId = MerchantId(7),
                    name = LocalizedText.of("en-US" to "  EXAMPLE  "),
                )
            )

        assertEquals(BrandErrors.NAME_DUPLICATE, assertIs<Failure<*>>(result).error)
    }
}
