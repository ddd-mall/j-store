package com.jstore.warehouse.domain

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.jstore.warehouse.domain.event.PhysicalStockChangedEvent

class PhysicalStockTest {
    @Test
    fun `physical adjustment publishes a monotonically versioned warehouse fact`() {
        val stock = PhysicalStock(PhysicalStockId("11@CN-NORTH-1"), 11, "CN-NORTH-1", 10, 3)

        assertIs<Success<Unit>>(stock.adjustTo(12, "cycle-count"))
        assertEquals(4, stock.sourceVersion)
        assertEquals(12, stock.onHand)
        assertEquals(
            4,
            (stock.pendingDomainEvents().single() as PhysicalStockChangedEvent).sourceVersion,
        )
        assertIs<Failure<*>>(stock.adjustTo(-1, "invalid"))
    }
}
