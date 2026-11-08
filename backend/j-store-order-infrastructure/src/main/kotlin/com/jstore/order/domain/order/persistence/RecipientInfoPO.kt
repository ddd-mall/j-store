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
package com.jstore.order.domain.order.persistence

import com.jstore.common.geo.I18nGeoAddress

/**
 * 收货人信息持久化数据结构 仅用于 consignee_info jsonb 列的序列化/反序列化，非领域对象 所有字段可空 + 默认 null，确保 Jackson
 * 反序列化历史数据时缺失字段不报错
 */
data class RecipientInfoPO(
    val consigneeName: String? = null,
    val consigneePhone: String? = null,
    val consigneeEmail: String? = null,
    val countryCode: String? = null,
    val districtCode: String? = null,
    val shippingAddress: I18nGeoAddress? = null,
    val detailAddress: String? = null,
    val postalCode: String? = null,
    val customsFields: Map<String, String>? = null,
)
