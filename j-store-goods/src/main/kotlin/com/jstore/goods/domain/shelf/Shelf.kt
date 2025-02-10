package com.jstore.goods.domain.shelf

import com.jstore.common.properties.Id

/**
 * 货架
 */
class Shelf(
    val id: ShelfId,
    val name: String,
    val status: ShelfStatus = ShelfStatus.OFF_LINE
) {
}

class ShelfId(override val value: Long) : Id<Long>(value)

enum class ShelfStatus {
    OFF_LINE,
    ON_LINE,
}
