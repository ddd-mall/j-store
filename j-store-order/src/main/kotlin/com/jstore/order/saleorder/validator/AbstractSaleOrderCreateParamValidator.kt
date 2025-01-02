package com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.service.SaleOrderCreateCMD
import com.jstore.common.utils.ChainedConsumer

abstract class AbstractSaleOrderCreateParamValidator: ChainedConsumer<SaleOrderCreateCMD>()