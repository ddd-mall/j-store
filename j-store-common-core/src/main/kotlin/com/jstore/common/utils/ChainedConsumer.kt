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
package com.jstore.common.utils

abstract class ChainedConsumer<T> {
    private var index: Int? = null
    private var chain: ConsumerChain<T>? = null

    abstract fun accept(t: T)

    private fun execute(t: T) {
        this.accept(t)
        this.chain?.let { it.getNext(this)?.execute(t) }
    }

    open class ConsumerChain<T> {
        private var consumerList: MutableList<ChainedConsumer<T>> = ArrayList()

        fun getNext(current: ChainedConsumer<T>?): ChainedConsumer<T>? {
            current?.let { it ->
                it.index?.let {
                    val nextIndex = it.plus(1)
                    if (nextIndex < this.consumerList.size) {
                        return this.consumerList[nextIndex]
                    }
                }
            }
            return null
        }

        fun append(next: ChainedConsumer<T>): ConsumerChain<T> {
            next.chain = this
            next.index = this.consumerList.size
            this.consumerList.add(next)
            return this
        }

        open fun accept(t: T) {
            if (this.consumerList.isNotEmpty()) {
                this.consumerList.first().execute(t)
            }
        }
    }
}
