package com.jstore.goods.stock

import com.jstore.common.framework.Repository
import com.jstore.goods.spu.SkuId

interface StockRepository : Repository<SkuId, Stock> {
}