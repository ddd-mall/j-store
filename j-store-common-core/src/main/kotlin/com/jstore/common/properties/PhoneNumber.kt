package com.jstore.common.properties

import com.jstore.common.framework.Properties

data class PhoneNumber(val value: String) : Properties {
    companion object {
        val PATTERN =
            Regex(
                """^(?:\+?86)?1(?:3\d{3}|5[^4\D]\d{2}|8\d{3}|7(?:[235-8]\d{2}|4(?:0\d|1[0-2]|9\d))|9[0-35-9]\d{2}|66\d{2})\d{6}$"""
            )
    }

    init {
        require(value.length == 11) { "Phone number must be 11 digits" }
        require(value.matches(PATTERN)) { "Phone number must be valid" }
    }
}
