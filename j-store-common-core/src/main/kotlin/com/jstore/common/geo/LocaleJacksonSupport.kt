package com.jstore.common.geo

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.util.Locale

/**
 * Locale 作为 Map key 的序列化器：Locale → "zh-CN"
 */
class LocaleKeySerializer : JsonSerializer<Locale>() {
    override fun serialize(value: Locale, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeFieldName(value.toLanguageTag())
    }
}

/**
 * Locale 作为 Map key 的反序列化器："zh-CN" → Locale
 */
class LocaleKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Locale {
        return Locale.forLanguageTag(key)
    }
}

/**
 * Locale 作为值的序列化器：Locale → "zh-CN"
 */
class LocaleSerializer : JsonSerializer<Locale>() {
    override fun serialize(value: Locale, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toLanguageTag())
    }
}

/**
 * Locale 作为值的反序列化器："zh-CN" → Locale
 */
class LocaleDeserializer : JsonDeserializer<Locale>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Locale {
        return Locale.forLanguageTag(p.valueAsString)
    }
}
