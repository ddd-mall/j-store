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
