package com.jstore.common.geo

import java.util.Locale

/**
 * 地址格式化模板接口
 * 每个国家提供自己的实现，定义排列顺序和分隔符
 */
interface AddressTemplate {
    fun format(components: List<AddressComponent>, locale: Locale): String
}
