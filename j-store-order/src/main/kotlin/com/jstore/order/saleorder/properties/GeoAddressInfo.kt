package com.jstore.com.jstore.order.saleorder.properties

data class
GeoAddressInfo(
    val districtCode: String,
    val countryName: String,
    val provinceName: String,
    val cityName: String,
    var detailAddress: String? = null
) {
    companion object {
        fun getSuffix(len: Int): String {
            return "0".repeat(len)
        }
    }

    fun getProvinceCode(): String {
        val prefix = this.districtCode.substring(0, 2)
        return prefix + getSuffix(this.districtCode.length - 2)
    }

    fun getCityCode(): String {
        val prefix = this.districtCode.substring(0, 4)
        return prefix + getSuffix(this.districtCode.length - 4)
    }
}
