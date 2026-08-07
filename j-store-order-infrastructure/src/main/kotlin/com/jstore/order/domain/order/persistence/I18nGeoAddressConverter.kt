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
import com.jstore.common.utils.json.JsonUtils
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter: I18nGeoAddress ↔ JSON 字符串 使用 JsonUtils 中的共享 ObjectMapper，已注册 KotlinModule
 * 和自定义 Locale 序列化器
 */
@Converter(autoApply = false)
class I18nGeoAddressConverter : AttributeConverter<I18nGeoAddress, String> {

    override fun convertToDatabaseColumn(attribute: I18nGeoAddress?): String {
        return attribute?.let { JsonUtils.toJsonString(it) } ?: "{}"
    }

    override fun convertToEntityAttribute(dbData: String?): I18nGeoAddress? {
        if (dbData.isNullOrBlank() || dbData == "{}") return null
        return JsonUtils.deserialize(dbData, I18nGeoAddress::class.java)
    }
}
