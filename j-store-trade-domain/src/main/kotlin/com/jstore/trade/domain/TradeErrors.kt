/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.trade.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError

object TradeErrors {
    val CHECKOUT_REQUEST_INVALID =
        BusinessError("Checkout request is invalid", "Trade.CheckoutRequestInvalid", 400)
    val CHECKOUT_OFFER_INVALID =
        BusinessError(
            "Checkout offer is missing, stale, or spans merchants",
            "Trade.CheckoutOfferInvalid",
            409,
        )
    val CHECKOUT_BUYER_INVALID =
        BusinessError("Checkout buyer is unavailable", "Trade.CheckoutBuyerInvalid", 409)
    val PAYMENT_UNAVAILABLE =
        BusinessError(
            "Prepared payment is temporarily unavailable",
            "Trade.PaymentUnavailable",
            503,
        )
    val ILLEGAL_STATE: BusinessError = CommonBusinessError.ILLEGAL_STATE
    val INVALID_AUTHORIZATION =
        BusinessError(
            "Sale authorization does not cover the trade",
            "Trade.InvalidAuthorization",
            409,
        )
    val INVALID_RESERVATION =
        BusinessError("Inventory reservation is invalid", "Trade.InvalidReservation", 409)
    val RESERVATION_WINDOW_INSUFFICIENT =
        BusinessError(
            "Inventory reservation cannot cover the payment window and safety margin",
            "Trade.ReservationWindowInsufficient",
            409,
        )
    val INVALID_REASON = BusinessError("Reason must not be blank", "Trade.InvalidReason", 400)
    val ORDER_MISMATCH =
        BusinessError("Order does not match trade plan", "Trade.OrderMismatch", 409)
    val NOT_FOUND = BusinessError("Trade process not found", "Trade.NotFound", 404)
    val START_CONFLICT =
        BusinessError("Trade start conflicts with persisted snapshot", "Trade.StartConflict", 409)
}
