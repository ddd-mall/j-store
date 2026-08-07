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
package com.jstore.accounting.domain.account

import com.jstore.common.errors.BusinessError

object AccountingAccountErrors {
    val ACCOUNT_NOT_FOUND = BusinessError("账务账户不存在", "Accounting.Account.NotFound", 404)
    val LEDGER_ACCOUNT_INACTIVE = BusinessError("账务账户已停用", "Accounting.Account.Inactive", 400)
    val LEDGER_ACCOUNT_CODE_DUPLICATED =
        BusinessError("账务账户编码重复", "Accounting.Account.CodeDuplicated", 409)
}
