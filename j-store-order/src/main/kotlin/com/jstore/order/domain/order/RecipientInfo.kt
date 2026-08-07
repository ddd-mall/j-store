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
package com.jstore.order.domain.order

import com.jstore.common.geo.I18nGeoAddress

/** 收件信息 */
data class RecipientInfo(
    /** 收件人姓名 */
    val name: String,
    /** 收获人联系方式 */
    val contractInfo: ContractInfo,
    /** 收货地址 */
    val shippingAddress: I18nGeoAddress,
    /** 详细收货地址 */
    val shippingDetailAddress: String?,
)
