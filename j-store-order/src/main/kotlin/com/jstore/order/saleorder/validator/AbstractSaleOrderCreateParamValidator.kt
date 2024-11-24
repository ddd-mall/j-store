package com.jstore.com.jstore.order.saleorder.validator

import com.jstore.com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.util.ChainedConsumer

abstract class AbstractSaleOrderCreateParamValidator: ChainedConsumer<SaleOrderCreateParam>() {
}