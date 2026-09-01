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
package com.jstore.cart.domain

import com.jstore.common.framework.AggregateRepository

interface CartRepository : AggregateRepository<CartId, Cart> {
    fun findActiveByBuyerId(buyerId: BuyerId): Cart?
}

interface CartAssessmentRepository : AggregateRepository<CartAssessmentId, CartAssessment> {
    fun findByCartAndVersion(cartId: CartId, version: Long): CartAssessment?

    fun findLatestByCart(cartId: CartId): CartAssessment?
}

data class CartRequestReceipt(
    override val id: CartRequestReceiptId,
    val buyerId: BuyerId,
    val requestId: String,
    val requestDigest: String,
    val cartId: CartId,
    val cartVersion: Long,
) : com.jstore.common.framework.AggregateRoot<CartRequestReceiptId>

interface CartRequestReceiptRepository :
    AggregateRepository<CartRequestReceiptId, CartRequestReceipt> {
    fun findByBuyerAndRequest(buyerId: BuyerId, requestId: String): CartRequestReceipt?
}
