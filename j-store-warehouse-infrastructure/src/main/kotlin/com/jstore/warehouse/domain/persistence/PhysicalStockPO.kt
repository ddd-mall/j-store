package com.jstore.warehouse.domain.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "warehouse_physical_stock")
class PhysicalStockPO(
    @Id @Column(length = 192) var id: String = "",
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "on_hand", nullable = false) var onHand: Int = 0,
    @Column(name = "source_version", nullable = false) var sourceVersion: Long = 0,
    @Version var persistenceVersion: Long = 0,
)
