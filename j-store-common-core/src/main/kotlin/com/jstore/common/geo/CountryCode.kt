package com.jstore.common.geo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * ISO 3166-1 alpha-2 国家编码值对象
 * 不可变，封装国家编码验证逻辑
 *
 * Jackson: 序列化为纯字符串 "CN"，而非 {"value":"CN"}
 */
data class CountryCode @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @get:JsonValue val value: String
) {
    init {
        require(value.length == 2 && value.all { it.isUpperCase() }) {
            "CountryCode must be ISO 3166-1 alpha-2 format: $value"
        }
    }

    companion object {
        val CN = CountryCode("CN")
        val US = CountryCode("US")
        val JP = CountryCode("JP")
        val SG = CountryCode("SG")
    }
}
