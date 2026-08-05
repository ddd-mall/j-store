package com.jstore.goods.domain.inventory

import com.jstore.common.framework.AggregateRepository

interface InventoryRepository : AggregateRepository<CommodityCode, Inventory>
