package com.jstore.inventory.domain

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StockPositionTest {
    @Test
    fun `ATP subtracts reservation safety stock and isolated supply`() {
        val position = position(onHand = 20, reserved = 3, safety = 2, isolated = 4)
        assertEquals(11, position.availableToPromise)
    }

    @Test
    fun `reservation is atomic and cannot exceed ATP`() {
        val position = position(onHand = 10, reserved = 2, safety = 3, isolated = 1)
        assertIs<Success<Unit>>(position.reserve(4))
        assertEquals(0, position.availableToPromise)
        assertIs<Failure<*>>(position.reserve(1))
    }

    @Test
    fun `old WMS source versions do not change physical stock mirror`() {
        val position = position(onHand = 10, sourceVersion = 5)
        assertEquals(false, position.applyPhysicalStock(99, 4))
        assertEquals(10, position.onHand)
        assertEquals(true, position.applyPhysicalStock(12, 6))
        assertEquals(12, position.onHand)
    }

    private fun position(
        onHand: Int,
        reserved: Int = 0,
        safety: Int = 0,
        isolated: Int = 0,
        sourceVersion: Long = 1,
    ) =
        StockPosition(
            StockPositionId("11@CN-NORTH-1"),
            SkuId(11),
            FulfillmentNodeId("CN-NORTH-1"),
            onHand,
            reserved,
            safety,
            isolated,
            sourceVersion,
        )
}
