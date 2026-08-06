package com.jstore.order.domain.order

import com.jstore.common.geo.I18nGeoAddress

/** 收件信息 */
data class RecipientInfo(
    /** 收件人姓名 */
    val name: String,
    /** 收获人联系方式 */
    val contractInfo: ContractInfo,
    /** 收货地址 */
    val shippingAddress: I18nGeoAddress,
    /** 详细收货地址 */
    val shippingDetailAddress: String?,
    /** 邮政编码（跨境派送/清关需要，部分国家可空） */
    val postalCode: String? = null,
    /** 按国附加的清关字段（税号等），键如 "CPF"、"PCCC"、"IOSS" */
    val customsFields: Map<String, String> = emptyMap(),
)
