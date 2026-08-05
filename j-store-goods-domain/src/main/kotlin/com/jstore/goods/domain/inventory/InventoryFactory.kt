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
package com.jstore.goods.domain.inventory

/** 库存工厂 领域层接口，不依赖任何框架注解 Bean 注册由 boot 模块的配置类负责 */
interface InventoryFactory {
    fun create(createCMD: StorageCreateCMD): Inventory {
        return InventoryImpl(
            id = createCMD.commodityCode,
            availableQuantity = createCMD.quantity,
        )
    }
}
