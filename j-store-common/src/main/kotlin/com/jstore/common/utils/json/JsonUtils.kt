package com.jstore.common.utils.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory

object JsonUtils {
    private val OBJECT_MAPPER: ObjectMapper
    private val log = LoggerFactory.getLogger(JsonUtils::class)

    init {
        try {
            //初始化
            OBJECT_MAPPER = ObjectMapper()
            //配置序列化级别
            OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL)

            /****配置普通属性****/
            //有属性不能映射的时候不报错,主要用于：json串传了这个字段，但是类对象没有这个字段，这种不需要报错
            OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // 当你遇到这个错误时，通常是因为你正在尝试序列化一个 Java 对象，而这个对象的某些字段是空的（null），而 Jackson 默认情况下不允许这种情况
            OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        } catch (e: Exception) {
            log.error("jackson初始化config异常", e)
            throw RuntimeException(e)
        }
    }


    fun <T> deserialize(jsonStr: String, clazz: Class<T>): T {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, clazz)
        } catch (e: Exception) {
            val errorMsg = "jackson反序列化简单对象异常,json:${jsonStr},valueType:${clazz},error:${e.message}"
            log.error("{}", arrayOf(errorMsg, e))
            throw CommonErrors.INTERNAL_ERROR.withMsg(errorMsg)
        }
    }
}