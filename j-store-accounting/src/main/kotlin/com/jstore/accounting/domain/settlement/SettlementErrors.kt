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
package com.jstore.accounting.domain.settlement

import com.jstore.common.errors.BusinessError

object SettlementErrors {
    val SETTLEMENT_STATEMENT_INVALID_STATE =
        BusinessError("结算单状态不合法", "Accounting.Settlement.InvalidState", 400)
    val SETTLEMENT_AMOUNT_MISMATCH =
        BusinessError("结算金额不一致", "Accounting.Settlement.AmountMismatch", 400)
    val SETTLEMENT_STATEMENT_DUPLICATED =
        BusinessError("结算单已存在", "Accounting.Settlement.Duplicated", 409)
    val SETTLEMENT_STATEMENT_NOT_FOUND =
        BusinessError("结算单不存在", "Accounting.Settlement.NotFound", 404)
}
