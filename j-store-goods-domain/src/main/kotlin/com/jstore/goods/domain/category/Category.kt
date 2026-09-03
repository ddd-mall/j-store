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
package com.jstore.goods.domain.category

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.goods.domain.content.LocalizedText

data class CategoryId(override val value: Long) : Id<Long>(value) {
    init {
        require(value != 0L) { "category id must not be zero" }
    }
}

data class Category(
    override val id: CategoryId,
    val name: LocalizedText,
    val parentId: CategoryId? = null,
) : Entity<CategoryId> {
    init {
        require(parentId != id) { "category cannot be its own parent" }
    }
}
