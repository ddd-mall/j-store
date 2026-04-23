package com.jstore.order.domain.order

import com.jstore.common.errors.CommonErrors
import com.jstore.common.utils.string.StringUtils

/**
 * TODO: move to common module
 */
data class
GeoAddressInfo(
    val districtCode: String,
    val province: String,
    val city: String,
    val county: String,
    var detailAddress: String? = null
) {
    companion object {
        fun getProvinceCode(districtCode: String): String {
            return commonDecoding(districtCode, DistrictLevel.PROVINCE)
        }

        fun getCityCode(districtCode: String): String {
            return commonDecoding(districtCode, DistrictLevel.CITY)
        }

        fun getCountyCode(districtCode: String): String {
            return commonDecoding(districtCode, DistrictLevel.COUNTY)
        }

        private fun commonDecoding(districtCode: String, level: DistrictLevel): String {
            if (districtCode.length < level.getCodeLen()) {
                throw CommonErrors.INVALID_PARAM.msg("无法从${districtCode}中解析出${level.name}级行政编码")
            }
            return districtCode.substring(0, level.getCodeLen()) + "0".repeat(districtCode.length - level.getCodeLen())
        }
    }

    val level: DistrictLevel = if (StringUtils.isNotEmpty(this.county)) {
        DistrictLevel.COUNTY
    } else if (StringUtils.isNotEmpty(this.city)) {
        DistrictLevel.CITY
    } else {
        DistrictLevel.PROVINCE
    }

    fun getProvinceCode(): String {
        return getProvinceCode(this.districtCode)
    }

    fun getCityCode(): String {
        return getCityCode(this.districtCode)
    }

    fun getCountyCode(): String {
        return getCountyCode(this.districtCode)
    }

}

enum class DistrictLevel {
    PROVINCE {
        override fun getCodeLen(): Int {
            return 2
        }

    },
    CITY {
        override fun getCodeLen(): Int {
            return 4
        }
    },
    COUNTY {
        override fun getCodeLen(): Int {
            return 6
        }
    },
    ;

    abstract fun getCodeLen(): Int
}
