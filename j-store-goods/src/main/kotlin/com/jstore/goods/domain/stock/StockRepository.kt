package com.jstore.goods.domain.stock

import com.jstore.common.framework.Repository
import com.jstore.goods.domain.sku.SkuId


interface StockRepository : Repository<SkuId, Stock> {
}