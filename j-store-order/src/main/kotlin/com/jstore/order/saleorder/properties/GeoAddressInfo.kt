package com.jstore.com.jstore.order.saleorder.properties

data class
GeoAddressInfo(
    val countryCOde: String,
    val countryName: String,
    val provinceCode: String,
    val provinceName: String,
    val cityCode: String,
    val cityName: String,
    var detailDesc: String? = null
) {
    fun districtCode(): String {
        TODO()
    }
}