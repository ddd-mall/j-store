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

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd

object CommodityCommandValidator {
    fun validate(command: CommodityCreateCmd): Result<Boolean, BusinessError> {
        if (command.merchantId <= 0)
            return Failure(CommonBusinessError.INVALID_PARAM.msg("商户ID必须为正数"))
        if (command.spuName.isBlank())
            return Failure(CommonBusinessError.INVALID_PARAM.msg("商品名称不能为空"))
        return Success(true)
    }

    fun validate(command: GoodsStyleSaveCmd): Result<Boolean, BusinessError> {
        if (command.mainImages.size != command.mainImages.distinct().size)
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        if (command.skuImages.values.any { it.size != it.distinct().size })
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        return Success(true)
    }
}
