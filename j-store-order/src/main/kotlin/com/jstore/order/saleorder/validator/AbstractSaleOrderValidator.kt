package com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.SaleOrder
import com.jstore.common.utils.ChainedConsumer

abstract class AbstractSaleOrderValidator: ChainedConsumer<SaleOrder>()