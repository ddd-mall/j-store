package com.jstore.warehouse.domain

import com.jstore.common.framework.AggregateRepository

interface PhysicalStockRepository : AggregateRepository<PhysicalStockId, PhysicalStock>
