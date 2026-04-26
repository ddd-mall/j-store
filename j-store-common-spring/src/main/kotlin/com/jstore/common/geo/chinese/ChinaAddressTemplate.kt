package com.jstore.common.geo.chinese

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.AddressTemplate

import java.util.Locale

/**
 * 中国地址格式化模板
 * 中国地址：从大到小排列（省 → 市 → 区/县 → 详细地址），无分隔符直接拼接
 */
class ChinaAddressTemplate : AddressTemplate {
    override fun format(
        components: List<AddressComponent>,
        locale: Locale
    ): String {
        val sorted = components.sortedBy { it.level.depth }
        val parts = sorted.map { it.getName(locale) }
        return parts.filter { it.isNotBlank() }.joinToString("")
    }
}
