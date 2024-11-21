package com.jstore.util

abstract class ChainedConsumer<T> {
    private var index: Int? = null
    private var chain: ConsumerChain<T>? = null

    abstract fun accept(t: T)

    private fun execute(t: T) {
        this.accept(t)
        this.chain?.let { it.getNext(this)?.execute(t) }
    }

    class ConsumerChain<T> {
        private var consumerList: MutableList<ChainedConsumer<T>> = ArrayList()

        fun getNext(current: ChainedConsumer<T>?): ChainedConsumer<T>? {
            current?.let {
                it.index?.let {
                    val nextIndex = it.plus(1)
                    if (nextIndex < this.consumerList.size) {
                        return this.consumerList.get(nextIndex)
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

        fun accept(t: T) {
            this.consumerList.first().execute(t)
        }
    }

}

