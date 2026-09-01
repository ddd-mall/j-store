package com.jstore.trade.config

import com.jstore.cart.api.*
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.trade.domain.TradeErrors
import com.jstore.trade.service.CheckoutItem
import com.jstore.trade.service.CheckoutSourceGateway
import com.jstore.trade.service.CreateCheckoutCommand

class CartCheckoutSourceAdapter(private val carts: CartCheckoutSourceQueryService) : CheckoutSourceGateway {
    override fun resolve(command: CreateCheckoutCommand): Result<CreateCheckoutCommand, com.jstore.common.errors.BusinessError> {
        val cartId = command.cartId ?: return if (command.items.isNotEmpty()) Success(command) else Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        val expectedVersion = command.expectedCartVersion ?: return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        if (command.items.isNotEmpty()) return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        return when (val result = carts.prepare(CartCheckoutSourceQuery(command.buyerId, cartId, expectedVersion))) {
            is CartCheckoutSourceResult.Found -> Success(command.copy(items = result.source.eligibleLines.map { CheckoutItem(it.offerId, it.offerVersion, it.spuId, it.skuId, it.quantity, it.catalogSnapshotVersion) }, cartDigest = result.source.cartDigest))
            CartCheckoutSourceResult.NotFound -> Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
            CartCheckoutSourceResult.VersionConflict -> Failure(TradeErrors.START_CONFLICT)
            CartCheckoutSourceResult.NoEligibleLines -> Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
            CartCheckoutSourceResult.Unavailable -> Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
        }
    }
}
