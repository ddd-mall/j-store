package com.jstore.com.jstore.order.saleorder.properties

data class
GeoAddressInfo(
    val districtCode: String,
    val countryName: String,
    val provinceName: String,
    val cityName: String,
    var detailAddress: String? = null
) {
    fun districtCode(): String {
        TODO()
    }
}