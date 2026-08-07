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
package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotFactory
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import org.mockito.kotlin.*

// Feature: commodity-draft-copy-on-write, Property 3: createOrUpdate 状态守卫

/**
 * Property 3: createOrUpdate 状态守卫
 *
 * For any SPU 和任意有效的 CommodityCreateCmd，当 SPU 状态为 ON_SALE 时， CommodityService.createOrUpdate 应返回
 * Failure（ON_SALE_DIRECT_EDIT_REJECTED）； 当 SPU 状态为 DRAFT 或 OFF_SALE 时，应正常执行更新。
 *
 * **Validates: Requirements 5.1, 5.2**
 */
class CreateOrUpdateStatusGuardPropertyTest :
    FunSpec({
        lateinit var spuFactory: SpuFactory
        lateinit var spuRepository: SpuRepository
        lateinit var domainEventPublisher: DomainEventPublisher
        lateinit var snapshotFactory: SpuSnapshotFactory
        lateinit var snapshotRepository: SpuSnapshotRepository
        lateinit var goodsStyleRepository: GoodsStyleRepository
        lateinit var goodsStyleFactory: GoodsStyleFactory
        lateinit var service: CommodityService

        beforeEach {
            spuFactory = mock()
            spuRepository = mock()
            domainEventPublisher = mock()
            snapshotFactory = mock()
            snapshotRepository = mock()
            goodsStyleRepository = mock()
            goodsStyleFactory = mock()
            service =
                CommodityService(
                    spuFactory = spuFactory,
                    spuRepository = spuRepository,
                    domainEventPublisher = domainEventPublisher,
                    snapshotFactory = snapshotFactory,
                    snapshotRepository = snapshotRepository,
                    goodsStyleRepository = goodsStyleRepository,
                    goodsStyleFactory = goodsStyleFactory,
                )
        }

        // Generator for a non-blank spuName
        val spuNameArb: Arb<String> = Arb.string(1..50).filter { it.isNotBlank() }

        // Generator for CommodityCreateCmd with a non-null spuId (update scenario)
        val cmdArb: Arb<CommodityCreateCmd> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE),
                spuNameArb,
                Arb.string(0..100),
            ) { spuIdVal, name, description ->
                CommodityCreateCmd(
                    spuId = SpuId(spuIdVal),
                    merchantId = 1,
                    spuName = name,
                    description = description,
                )
            }

        // Generator for a non-empty list of SKUs
        val skuListArb: Arb<List<Sku>> =
            Arb.list(
                Arb.bind(
                    Arb.long(1L..Long.MAX_VALUE),
                    Arb.string(1..20),
                    Arb.long(1L..999999L),
                    Arb.string(1..10),
                ) { skuIdVal, skuName, priceFen, attrValue ->
                    SkuImpl(
                        id = SkuId(skuIdVal),
                        skuName = skuName,
                        attributes = listOf(Attribute("variant", attrValue)),
                        price = Price.ofFen(priceFen),
                    )
                },
                1..5,
            )

        test("createOrUpdate should return Failure for ON_SALE SPU") {
            checkAll(100, cmdArb, skuListArb) { cmd, skus ->
                val spuId = cmd.spuId!!
                val onSaleSpu: Spu =
                    SpuImpl(
                        id = spuId,
                        name = "existing",
                        description = "desc",
                        _status = CommodityStatus.ON_SALE,
                        _skus = skus.toMutableList(),
                    )
                whenever(spuRepository.findById(spuId)).thenReturn(onSaleSpu)

                val result = service.createOrUpdate(cmd)

                result.shouldBeInstanceOf<Failure<BusinessError>>()
                result.error shouldBe CommodityErrors.ON_SALE_DIRECT_EDIT_REJECTED
            }
        }

        // Editable statuses: DRAFT and OFF_SALE
        val editableStatusArb: Arb<CommodityStatus> =
            Arb.of(
                CommodityStatus.DRAFT,
                CommodityStatus.OFF_SALE,
            )

        test("createOrUpdate should succeed for DRAFT or OFF_SALE SPU") {
            checkAll(100, cmdArb, skuListArb, editableStatusArb) { cmd, skus, status ->
                val spuId = cmd.spuId!!
                val existingSpu: Spu =
                    SpuImpl(
                        id = spuId,
                        name = "existing",
                        description = "desc",
                        _status = status,
                        _skus = skus.toMutableList(),
                    )
                val updatedSpu: Spu =
                    SpuImpl(
                        id = spuId,
                        name = cmd.spuName,
                        description = cmd.description,
                        _status = status,
                        _skus = skus.toMutableList(),
                    )
                whenever(spuRepository.findById(spuId)).thenReturn(existingSpu)
                whenever(spuFactory.update(cmd, existingSpu)).thenReturn(updatedSpu)
                whenever(spuRepository.save(updatedSpu)).thenReturn(updatedSpu)

                val result = service.createOrUpdate(cmd)

                result.shouldBeInstanceOf<Success<Spu>>()
            }
        }
    })
