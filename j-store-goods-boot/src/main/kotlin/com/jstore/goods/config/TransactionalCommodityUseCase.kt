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

import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd
import com.jstore.goods.domain.commodity.comand.SkuRemoveCmd
import com.jstore.goods.domain.commodity.comand.SkuUpdateCmd
import com.jstore.goods.service.CommodityUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalCommodityUseCase(
    private val delegate: CommodityUseCase,
    private val snapshotQueries: GoodsSnapshotQueryService,
    transactionManager: PlatformTransactionManager,
) : CommodityUseCase, GoodsSnapshotQueryService {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun createOrUpdate(cmd: CommodityCreateCmd) = tx { delegate.createOrUpdate(cmd) }

    override fun addSku(cmd: SkuCreateCmd) = tx { delegate.addSku(cmd) }

    override fun updateSku(cmd: SkuUpdateCmd) = tx { delegate.updateSku(cmd) }

    override fun removeSku(cmd: SkuRemoveCmd) = tx { delegate.removeSku(cmd) }

    override fun publish(spuId: SpuId) = tx { delegate.publish(spuId) }

    override fun archive(spuId: SpuId) = tx { delegate.archive(spuId) }

    override fun getDraft(spuId: SpuId) = tx { delegate.getDraft(spuId) }

    override fun publishDraft(draftSpuId: SpuId) = tx { delegate.publishDraft(draftSpuId) }

    override fun discardDraft(draftSpuId: SpuId) = tx { delegate.discardDraft(draftSpuId) }

    override fun saveGoodsStyle(cmd: GoodsStyleSaveCmd) = tx { delegate.saveGoodsStyle(cmd) }

    override fun queryLatestSnapshots(spuIds: List<Long>) = query {
        snapshotQueries.queryLatestSnapshots(spuIds)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
