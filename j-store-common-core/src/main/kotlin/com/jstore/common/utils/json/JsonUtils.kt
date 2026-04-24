package com.jstore.common.utils.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory

object JsonUtils {
    val JSON: ObjectMapper = jacksonObjectMapper()
    val log = LoggerFactory.getLogger(JsonUtils::class)

    init {
        try {
            //配置序列化级别
            JSON.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            /****配置普通属性****/
            //有属性不能映射的时候不报错,主要用于：json串传了这个字段，但是类对象没有这个字段，这种不需要报错
            JSON.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // 当你遇到这个错误时，通常是因为你正在尝试序列化一个 Java 对象，而这个对象的某些字段是空的（null），而 Jackson 默认情况下不允许这种情况
            JSON.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        } catch (e: Exception) {
            log.error("jackson初始化config异常", e)
            throw RuntimeException(e)
        }
    }


    fun <T> deserialize(jsonStr: String, clazz: Class<T>): T {
        try {
            return JSON.readValue(jsonStr, clazz)
        } catch (e: Exception) {
            val errorMsg = "Jackson反序列化简单对象异常,json:${jsonStr},valueType:${clazz},error:${e.message}"
            log.error(errorMsg, e)
            throw CommonErrors.INTERNAL_ERROR.msgAndCause(errorMsg, e)
        }
    }

    fun <T> deserialize(jsonStr: String, typeReference: TypeReference<T>): T {
        try {
            return JSON.readValue(jsonStr, typeReference)
        } catch (e: Exception) {
            val errorMsg = "Jackson反序列化异常，jsonStr: ${jsonStr}, error: ${e.message?:""}"
            log.error(errorMsg, e)
            throw CommonErrors.INTERNAL_ERROR.msgAndCause(errorMsg, e)
        }
    }


    inline fun <reified T> deserialize(jsonStr: String): T {
        return try {
            JSON.readValue<T>(jsonStr)
        } catch (e: Exception) {
            val errorMsg = "Jackson反序列化异常，jsonStr: ${jsonStr}, error: ${e.message?:""}"
            log.error(errorMsg, e)
            throw CommonErrors.INTERNAL_ERROR.msgAndCause(errorMsg, e)
        }
    }

    fun toJsonString(obj: Any): String {
        try {
            return JSON.writeValueAsString(obj)
        } catch (e: Exception) {
            val errorMsg = "Jackson 序列化对象异常"
            log.error(errorMsg, e)
            throw CommonErrors.INTERNAL_ERROR.msgAndCause(errorMsg, e)
        }
    }

}