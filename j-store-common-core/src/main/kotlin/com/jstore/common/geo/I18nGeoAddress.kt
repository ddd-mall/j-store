package com.jstore.common.geo

import com.fasterxml.jackson.annotation.JsonIgnore

/** 通用 i18n 地址值对象 不可变，表达任意国家的行政区划地址 */
data class I18nGeoAddress(
    val countryCode: CountryCode,
    val components: List<AddressComponent>,
) {
    init {
        require(components.isNotEmpty()) { "Address components must not be empty" }
    }

    /** 获取指定层级的组件 */
    fun getComponentAtLevel(depth: Int): AddressComponent? = components.find {
        it.level.depth == depth
    }

    /** 获取最末层级组件的编码（通常用作地址主编码） */
    @JsonIgnore fun getLeafCode(): String = components.last().code
}
