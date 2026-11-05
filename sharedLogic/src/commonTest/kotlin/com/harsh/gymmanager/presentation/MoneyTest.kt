package com.harsh.gymmanager.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test
    fun formatsWithIndianGrouping() {
        assertEquals("₹0", formatRupees(0))
        assertEquals("₹999", formatRupees(999))
        assertEquals("₹1,500", formatRupees(1500))
        assertEquals("₹1,23,456", formatRupees(123456))
        assertEquals("₹12,34,567", formatRupees(1234567))
        assertEquals("-₹2,000", formatRupees(-2000))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(1500, parseRupees("1500"))
        assertEquals(1500, parseRupees(" ₹ 1,500 "))
        assertNull(parseRupees(""))
        assertNull(parseRupees("15.50"))
        assertNull(parseRupees("-200"))
        assertNull(parseRupees("abc"))
    }
}
