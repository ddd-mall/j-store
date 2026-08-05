package com.jstore.common.framework

import com.jstore.common.properties.Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentifierTest {
    @Test
    fun `non data identifier subclasses use value equality`() {
        assertEquals(TestId(42), TestId(42))
        assertEquals(TestId(42).hashCode(), TestId(42).hashCode())
    }

    @Test
    fun `different identifier types are never equal`() {
        assertNotEquals<Any>(TestId(42), OtherId(42))
    }

    private class TestId(override val value: Long) : Id<Long>(value)

    private class OtherId(override val value: Long) : Id<Long>(value)
}
