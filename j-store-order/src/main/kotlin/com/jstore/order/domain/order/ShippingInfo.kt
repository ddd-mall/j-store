package com.jstore.order.domain.order

import com.jstore.common.geo.I18nGeoAddress

data class ShippingInfo(
    /**
     * 收件人姓名
     */
    val consigneeName: String,
    /**
     * 收获人联系方式
     */
    val consigneeContractInfo: ContractInfo,
    /**
     * 收货地址
     */
    val shippingAddress: I18nGeoAddress,
    /**
     * 详细收货地址
     */
    val shippingDetailAddress: String?,
    )
