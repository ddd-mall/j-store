package com.jstore.order.domain.order.persistence

import com.jstore.common.utils.json.JsonUtils
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter: RecipientInfoPO ↔ JSON 字符串
 * 使用 JsonUtils 中的共享 ObjectMapper，已注册 KotlinModule
 */
@Converter(autoApply = false)
class RecipientInfoPOConverter : AttributeConverter<RecipientInfoPO, String> {

    override fun convertToDatabaseColumn(attribute: RecipientInfoPO?): String {
        return attribute?.let { JsonUtils.toJsonString(it) } ?: "{}"
    }

    override fun convertToEntityAttribute(dbData: String?): RecipientInfoPO? {
        if (dbData.isNullOrBlank() || dbData == "{}") return null
        return JsonUtils.deserialize(dbData, RecipientInfoPO::class.java)
    }
}
