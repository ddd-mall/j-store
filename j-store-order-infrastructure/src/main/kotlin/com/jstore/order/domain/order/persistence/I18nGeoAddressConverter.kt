package com.jstore.order.domain.order.persistence

import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.utils.json.JsonUtils
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter: I18nGeoAddress ↔ JSON 字符串
 * 使用 JsonUtils 中的共享 ObjectMapper，已注册 KotlinModule 和自定义 Locale 序列化器
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
