package com.jstore.common.properties

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * 国际化电话号码值对象
 *
 * 规范存储为 E.164 格式（如 +8613800138000、+14155552671），使用 libphonenumber 按号码所属国家/地区校验有效性。
 * 构造入参必须是不含空格与分隔符的规范 E.164 字符串；区号与国内号码通过派生属性访问。
 */
data class PhoneNumber
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(@get:JsonValue val value: String) {

    /** 国家区号（不含 +），如 86、1、81 */
    val countryCallingCode: Int

    /** 去除区号后的国内有效号码部分 */
    val nationalNumber: String

    init {
        require(value.startsWith("+")) {
            "Phone number must be in E.164 format starting with '+': $value"
        }
        val parsed =
            try {
                UTIL.parse(value, null)
            } catch (e: NumberParseException) {
                throw IllegalArgumentException("Cannot parse phone number: $value", e)
            }
        require(UTIL.isValidNumber(parsed)) { "Phone number is not valid for its region: $value" }
        require(value == UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)) {
            "Phone number must be canonical E.164 without spaces or separators: $value"
        }
        countryCallingCode = parsed.countryCode
        nationalNumber = UTIL.getNationalSignificantNumber(parsed)
    }

    companion object {
        private val UTIL: PhoneNumberUtil = PhoneNumberUtil.getInstance()

        /** 由国家区号与国内号码构造，如 of(86, "13800138000") */
        fun of(countryCallingCode: Int, nationalNumber: String): PhoneNumber =
            PhoneNumber("+$countryCallingCode$nationalNumber")
    }
}
