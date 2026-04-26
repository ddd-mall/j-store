package com.jstore.common.geo

import com.jstore.common.utils.string.StringUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Feature: geo-address-service-migration, Property 2: level 属性推导正确性
 *
 * For any GeoAddressInfo 实例，level 属性应满足：
 * 若 county 非空（含非空白字符）则 level == COUNTY；
 * 否则若 city 非空则 level == CITY；
 * 否则 level == PROVINCE。
 *
 * **Validates: Requirements 1.4**
 */
class GeoAddressInfoLevelPropertyTest : FunSpec({

    // Generator that produces a mix of empty-ish and non-empty strings.
    // "Empty" in StringUtils terms: null, "", or whitespace-only.
    val arbMaybeEmpty: Arb<String> = Arb.element(
        "",           // empty string
        " ",          // single space
        "  ",         // multiple spaces
        "\t",         // tab
        " \t\n",      // mixed whitespace
        "北京",        // non-empty Chinese
        "上海市",      // non-empty Chinese
        "朝阳区",      // non-empty Chinese
        "a",          // non-empty ASCII
        "test county" // non-empty ASCII
    )

    // Use a fixed valid districtCode (6 digits) since level derivation doesn't depend on it
    val fixedDistrictCode = "110000"

    test("level is derived correctly based on county and city emptiness") {
        checkAll(100, arbMaybeEmpty, arbMaybeEmpty, arbMaybeEmpty) { province, city, county ->
            val info = GeoAddressInfo(
                districtCode = fixedDistrictCode,
                province = province,
                city = city,
                county = county
            )

            val expectedLevel = when {
                StringUtils.isNotEmpty(county) -> DistrictLevel.COUNTY
                StringUtils.isNotEmpty(city) -> DistrictLevel.CITY
                else -> DistrictLevel.PROVINCE
            }

            info.level shouldBe expectedLevel
        }
    }
})
