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

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.brand.Brand
import com.jstore.goods.domain.brand.BrandErrors
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.brand.BrandRepository

class BrandService(
    private val sequence: SnowFlakSequence,
    private val repository: BrandRepository,
) : BrandUseCase {
    override fun save(command: BrandSaveCommand): Result<Brand, BusinessError> {
        val duplicate =
            repository.findByMerchantIdAndNormalizedName(
                command.merchantId,
                Brand.normalizeName(command.name),
            )
        if (duplicate != null && duplicate.id != command.id) {
            return Failure(BrandErrors.NAME_DUPLICATE)
        }
        val brand =
            command.id?.let { id ->
                val existing = repository.findById(id) ?: return Failure(BrandErrors.NOT_FOUND)
                if (existing.merchantId != command.merchantId) {
                    return Failure(BrandErrors.MERCHANT_MISMATCH)
                }
                existing.rename(command.name)
                existing
            }
                ?: Brand(
                    id = BrandId(sequence.nextId()),
                    merchantId = command.merchantId,
                    name = command.name,
                )
        return Success(repository.save(brand))
    }

    override fun activate(command: BrandStatusCommand): Result<Brand, BusinessError> =
        changeStatus(command, Brand::activate)

    override fun deactivate(command: BrandStatusCommand): Result<Brand, BusinessError> =
        changeStatus(command, Brand::deactivate)

    private fun changeStatus(
        command: BrandStatusCommand,
        change: Brand.() -> Unit,
    ): Result<Brand, BusinessError> {
        val brand = repository.findById(command.id) ?: return Failure(BrandErrors.NOT_FOUND)
        if (brand.merchantId != command.merchantId) {
            return Failure(BrandErrors.MERCHANT_MISMATCH)
        }
        brand.change()
        return Success(repository.save(brand))
    }
}
