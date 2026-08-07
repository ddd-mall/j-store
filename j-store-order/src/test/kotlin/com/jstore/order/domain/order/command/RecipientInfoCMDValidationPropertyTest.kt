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
import com.jstore.order.domain.order.OrderErrors
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Feature: order-consignee-info, Property 2: RecipientInfoCMD 空白字段验证
 *
 * For any 仅由空白字符组成的字符串，当用作 RecipientInfoCMD 的 consigneeName 时， validate() 应返回 Failure；当用作
 * shippingDistrictCode 时，validate() 同样应返回 Failure。
 *
 * **Validates: Requirements 3.1, 3.2**
 */
class RecipientInfoCMDValidationPropertyTest :
    FunSpec({
        val validContractInfo =
            OrderCreateCMD.ContractInfoCMD(
                phoneNumber = PhoneNumber("13800138000"),
                emailAddress = null,
            )

        val whitespaceStrings = Arb.string(0..20).filter { it.isBlank() }

        test(
            "blank consigneeName should cause validate() to return Failure with CONSIGNEE_NAME_BLANK"
        ) {
            checkAll(100, whitespaceStrings) { blankName ->
                val cmd =
                    OrderCreateCMD.RecipientInfoCMD(
                        consigneeName = blankName,
                        countryCode = "CN",
                        consigneeContractInfo = validContractInfo,
                        shippingDistrictCode = "110105",
                        shippingDetailAddress = "三里屯街道xx号",
                    )

                val result = cmd.validate()

                result.shouldBeInstanceOf<Failure<*>>()
                (result as Failure).error.errorCode shouldBe
                    OrderErrors.CONSIGNEE_NAME_BLANK.errorCode
            }
        }

        test(
            "blank shippingDistrictCode should cause validate() to return Failure with DISTRICT_CODE_BLANK"
        ) {
            checkAll(100, whitespaceStrings) { blankCode ->
                val cmd =
                    OrderCreateCMD.RecipientInfoCMD(
                        consigneeName = "张三",
                        countryCode = "CN",
                        consigneeContractInfo = validContractInfo,
                        shippingDistrictCode = blankCode,
                        shippingDetailAddress = "三里屯街道xx号",
                    )

                val result = cmd.validate()

                result.shouldBeInstanceOf<Failure<*>>()
                (result as Failure).error.errorCode shouldBe
                    OrderErrors.DISTRICT_CODE_BLANK.errorCode
            }
        }
    })
