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
package com.jstore.goods.config

import com.jstore.goods.service.BrandSaveCommand
import com.jstore.goods.service.BrandStatusCommand
import com.jstore.goods.service.BrandUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalBrandUseCase(
    private val delegate: BrandUseCase,
    transactionManager: PlatformTransactionManager,
) : BrandUseCase {
    private val write = TransactionTemplate(transactionManager)

    override fun save(command: BrandSaveCommand) =
        requireNotNull(write.execute { delegate.save(command) })

    override fun activate(command: BrandStatusCommand) =
        requireNotNull(write.execute { delegate.activate(command) })

    override fun deactivate(command: BrandStatusCommand) =
        requireNotNull(write.execute { delegate.deactivate(command) })
}
