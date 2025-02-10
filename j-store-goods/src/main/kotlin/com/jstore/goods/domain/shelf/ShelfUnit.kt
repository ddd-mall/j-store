package com.jstore.goods.domain.shelf

import com.jstore.common.framework.Identify

/**
 * 货架单元
 */
interface ShelfUnit<T: ShelfUnitType> {
    /**
     * 货架单元ID
     */
    fun id(): ShelfUnitId
    /**
     * 货架ID
     */
    fun shelfId(): ShelfId

    /**
     * 单元类型
     */
    fun unitType(): T

    fun detail(): Array<Byte>
}

enum class ShelfUnitType {
    POSTER,
    SWIPER,
}




class ShelfUnitId(val value: Long): Identify