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
import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Feature: order-recipient-info, Property 3: OrderCreateCMD 验证错误传播
 *
 * For any 会导致 RecipientInfoCMD.validate() 失败的输入组合， OrderCreateCMD.validate() 也应返回 Failure，且错误信息与
 * RecipientInfoCMD.validate() 返回的一致。
 *
 * **Validates: Requirements 3.4**
 */
class OrderCreateCMDValidationPropertyTest :
    FunSpec({
        val validPhone = PhoneNumber("+8613800138000")
        /**
         * Generator for RecipientInfoCMD instances that will fail validation. Covers three failure
         * modes:
         * 1. blank consigneeName
         * 2. blank shippingDistrictCode
         * 3. both phoneNumber and emailAddress are null (CONTRACT_INFO_INVALID)
         */
        val failingRecipientInfoArb: Arb<OrderCreateCMD.RecipientInfoCMD> =
            Arb.element(1, 2, 3).map { mode ->
                when (mode) {
                    1 ->
                        OrderCreateCMD.RecipientInfoCMD(
                            consigneeName = "  ",
                            countryCode = "CN",
                            consigneeContractInfo =
                                OrderCreateCMD.ContractInfoCMD(phoneNumber = validPhone),
                            shippingDistrictCode = "110105",
                            shippingDetailAddress = "详细地址",
                        )
                    2 ->
                        OrderCreateCMD.RecipientInfoCMD(
                            consigneeName = "张三",
                            countryCode = "CN",
                            consigneeContractInfo =
                                OrderCreateCMD.ContractInfoCMD(phoneNumber = validPhone),
                            shippingDistrictCode = "  ",
                            shippingDetailAddress = "详细地址",
                        )
                    else ->
                        OrderCreateCMD.RecipientInfoCMD(
                            consigneeName = "张三",
                            countryCode = "CN",
                            consigneeContractInfo =
                                OrderCreateCMD.ContractInfoCMD(
                                    phoneNumber = null,
                                    emailAddress = null,
                                ),
                            shippingDistrictCode = "110105",
                            shippingDetailAddress = "详细地址",
                        )
                }
            }

        test(
            "OrderCreateCMD.validate() propagates the same Failure as RecipientInfoCMD.validate()"
        ) {
            checkAll(100, failingRecipientInfoArb) { failingRecipientInfo ->
                // First, confirm RecipientInfoCMD.validate() fails
                val recipientResult = failingRecipientInfo.validate()
                recipientResult.shouldBeInstanceOf<Failure<*>>()
                val expectedError = (recipientResult as Failure).error

                // Build a valid OrderCreateCMD (valid items, valid buyerUid) with the failing
                // recipientInfo
                val orderCmd =
                    OrderCreateCMD(
                        buyerUid = 1L,
                        merchantId = 7,
                        recipientInfo = failingRecipientInfo,
                        items =
                            listOf(
                                OrderCreateCMD.OrderItemCMD(
                                    spuId = 1,
                                    skuId = 1,
                                    quantity = 1,
                                    snapshotVersion = 1L,
                                )
                            ),
                    )

                val orderResult = orderCmd.validate()

                orderResult.shouldBeInstanceOf<Failure<*>>()
                (orderResult as Failure).error.errorCode shouldBe expectedError.errorCode
                orderResult.error.message shouldBe expectedError.message
            }
        }
    })
