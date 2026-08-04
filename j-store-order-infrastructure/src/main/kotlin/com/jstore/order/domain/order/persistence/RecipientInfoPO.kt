package com.jstore.order.domain.order.persistence

import com.jstore.common.geo.I18nGeoAddress

/**
 * 收货人信息持久化数据结构 仅用于 consignee_info jsonb 列的序列化/反序列化，非领域对象 所有字段可空 + 默认 null，确保 Jackson
 * 反序列化历史数据时缺失字段不报错
 */
data class RecipientInfoPO(
    val consigneeName: String? = null,
    val consigneePhone: String? = null,
    val consigneeEmail: String? = null,
    val countryCode: String? = null,
    val districtCode: String? = null,
    val shippingAddress: I18nGeoAddress? = null,
    val detailAddress: String? = null,
)
