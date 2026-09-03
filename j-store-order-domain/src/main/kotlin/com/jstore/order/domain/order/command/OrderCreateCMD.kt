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
package com.jstore.order.domain.order.command

import com.jstore.common.properties.PhoneNumber
import java.io.Serializable

/** 创建订单命令 */
data class OrderCreateCMD(
    val buyerUid: Long,
    val merchantId: Long,
    val recipientInfo: RecipientInfoCMD,
    val items: List<OrderItemCMD>,
) : Serializable {
    data class OrderItemCMD(
        val spuId: Long,
        val skuId: Long,
        val quantity: Int,
        val snapshotVersion: Long,
        val offerId: Long,
        val offerVersion: Long,
    )

    data class ContractInfoCMD(
        val phoneNumber: PhoneNumber? = null,
        val emailAddress: String? = null,
    )

    data class RecipientInfoCMD(
        val consigneeName: String,
        val countryCode: String,
        val consigneeContractInfo: ContractInfoCMD,
        val shippingDistrictCode: String,
        val shippingDetailAddress: String,
        val postalCode: String? = null,
        val customsFields: Map<String, String> = emptyMap(),
    )
}
