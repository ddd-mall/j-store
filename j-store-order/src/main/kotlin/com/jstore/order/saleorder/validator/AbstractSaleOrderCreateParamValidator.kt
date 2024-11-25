package com.jstore.com.jstore.order.saleorder.validator

import com.jstore.com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.common.utils.ChainedConsumer

abstract class AbstractSaleOrderCreateParamValidator: ChainedConsumer<SaleOrderCreateParam>() {
}