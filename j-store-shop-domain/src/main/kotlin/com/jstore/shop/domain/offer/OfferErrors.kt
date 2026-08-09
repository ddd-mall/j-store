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
package com.jstore.shop.domain.offer

import com.jstore.common.errors.BusinessError

object OfferErrors {
    val NOT_FOUND = BusinessError("销售要约不存在", "Store.Offer.NotFound", 404)
    val NOT_ACTIVE = BusinessError("销售要约当前不可用", "Store.Offer.NotActive", 409)
    val OUTSIDE_EFFECTIVE_PERIOD =
        BusinessError("销售要约不在有效销售周期内", "Store.Offer.OutsideEffectivePeriod", 409)
    val VERSION_MISMATCH = BusinessError("销售要约版本已变化", "Store.Offer.VersionMismatch", 409)
    val PRICE_MISMATCH = BusinessError("销售价格已变化", "Store.Offer.PriceMismatch", 409)
    val PURCHASE_LIMIT_EXCEEDED = BusinessError("购买数量超过限购", "Store.Offer.PurchaseLimit", 409)
    val ILLEGAL_STATE = BusinessError("销售要约状态不允许该操作", "Store.Offer.IllegalState", 409)
    val AUTHORIZATION_NOT_FOUND = BusinessError("销售授权不存在", "Store.SaleAuthorization.NotFound", 404)
    val AUTHORIZATION_EXPIRED = BusinessError("销售授权已失效", "Store.SaleAuthorization.Expired", 409)
}
