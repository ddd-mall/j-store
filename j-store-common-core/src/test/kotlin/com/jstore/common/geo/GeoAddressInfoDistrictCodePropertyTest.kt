package com.jstore.common.geo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Feature: geo-address-service-migration, Property 1: 行政区划编码解析格式不变量
 *
 * For any 有效的行政区划编码（长度 6-12 的数字字符串），
 * `getProvinceCode` 应返回前 2 位 + (len-2) 个 "0"，
 * `getCityCode` 应返回前 4 位 + (len-4) 个 "0"，
 * `getCountyCode` 应返回原编码前 6 位 + (len-6) 个 "0"。
 * 且三个函数的返回值长度均等于输入编码长度。
 *
 * **Validates: Requirements 1.3**
 */
class GeoAddressInfoDistrictCodePropertyTest : FunSpec({

    // Generator: random digit strings of length 6..12
    val arbDistrictCode = Arb.int(6..12).map { len ->
        (1..len).map { ('0'.code + (0..9).random()).toChar() }.joinToString("")
    }

    test("getProvinceCode returns first 2 digits padded with zeros, same length as input") {
        checkAll(100, arbDistrictCode) { code ->
            val result = GeoAddressInfo.getProvinceCode(code)
            result.length shouldBe code.length
            result shouldBe code.substring(0, 2) + "0".repeat(code.length - 2)
        }
    }

    test("getCityCode returns first 4 digits padded with zeros, same length as input") {
        checkAll(100, arbDistrictCode) { code ->
            val result = GeoAddressInfo.getCityCode(code)
            result.length shouldBe code.length
            result shouldBe code.substring(0, 4) + "0".repeat(code.length - 4)
        }
    }

    test("getCountyCode returns first 6 digits padded with zeros, same length as input") {
        checkAll(100, arbDistrictCode) { code ->
            val result = GeoAddressInfo.getCountyCode(code)
            result.length shouldBe code.length
            result shouldBe code.substring(0, 6) + "0".repeat(code.length - 6)
        }
    }
})
