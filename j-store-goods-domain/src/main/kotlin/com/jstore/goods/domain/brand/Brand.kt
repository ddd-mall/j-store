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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Id
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.content.LocalizedText
import java.util.Locale

data class BrandId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0L) { "brand id must be positive" }
    }
}

enum class BrandStatus {
    ACTIVE,
    INACTIVE,
}

class Brand(
    override val id: BrandId,
    val merchantId: MerchantId,
    name: LocalizedText,
    status: BrandStatus = BrandStatus.ACTIVE,
) : AggregateRoot<BrandId> {
    private var _name: LocalizedText = name
    private var _status: BrandStatus = status

    val name: LocalizedText
        get() = _name

    val status: BrandStatus
        get() = _status

    val normalizedName: String
        get() = normalizeName(_name)

    fun rename(name: LocalizedText) {
        _name = name
    }

    fun activate() {
        _status = BrandStatus.ACTIVE
    }

    fun deactivate() {
        _status = BrandStatus.INACTIVE
    }

    companion object {
        fun normalizeName(name: LocalizedText): String =
            name.values.toSortedMap().values.first().trim().lowercase(Locale.ROOT)
    }
}

object BrandErrors {
    val NOT_FOUND = BusinessError("品牌不存在", "Catalog.Brand.NotFound", 404)
    val MERCHANT_MISMATCH = BusinessError("品牌不属于当前商户", "Catalog.Brand.MerchantMismatch", 400)
    val INACTIVE = BusinessError("品牌已停用", "Catalog.Brand.Inactive", 400)
    val NAME_DUPLICATE = BusinessError("商户下已存在同名品牌", "Catalog.Brand.NameDuplicate", 409)
}
