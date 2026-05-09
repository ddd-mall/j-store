package com.jstore.common.geo

import java.util.Locale

/**
 * 地址格式化工具
 */
object AddressFormatter {
    fun format(address: I18nGeoAddress, template: AddressTemplate, locale: Locale): String {
        if (address.components.isEmpty()) return ""
        return template.format(address.components, locale)
    }
}
