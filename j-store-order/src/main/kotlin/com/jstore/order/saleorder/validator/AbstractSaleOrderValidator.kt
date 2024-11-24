package com.jstore.com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.SaleOrder
import com.jstore.util.ChainedConsumer

abstract class AbstractSaleOrderValidator: ChainedConsumer<SaleOrder>()