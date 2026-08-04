package com.jstore.fulfillment.domain.persistence

import com.jstore.fulfillment.domain.FulfillmentOrderStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "fulfillment_orders")
class FulfillmentOrderPO(
    @Id var id: Long = 0,
    @Column(name = "order_id", nullable = false, unique = true) var orderId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) var status: FulfillmentOrderStatus = FulfillmentOrderStatus.PENDING,
    @Column(name = "recipient_name", nullable = false, length = 128) var recipientName: String = "",
    @Column(name = "recipient_phone", length = 32) var recipientPhone: String? = null,
    @Column(name = "recipient_email", length = 256) var recipientEmail: String? = null,
    @Column(name = "country_code", nullable = false, length = 2) var countryCode: String = "CN",
    @Column(name = "district_code", nullable = false, length = 32) var districtCode: String = "",
    @Column(name = "detail_address", length = 512) var detailAddress: String? = null,
    @Column(name = "carrier_code", length = 64) var carrierCode: String? = null,
    @Column(name = "tracking_number", length = 128) var trackingNumber: String? = null,
    @Version var version: Long = 0,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "fulfillment_order_id")
    var items: MutableList<FulfillmentItemPO> = mutableListOf(),
)

@Entity
@Table(name = "fulfillment_items")
class FulfillmentItemPO(
    @Id var id: Long = 0,
    @Column(name = "fulfillment_order_id", insertable = false, updatable = false) var fulfillmentOrderId: Long = 0,
    @Column(name = "order_item_id", nullable = false) var orderItemId: Long = 0,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
)
