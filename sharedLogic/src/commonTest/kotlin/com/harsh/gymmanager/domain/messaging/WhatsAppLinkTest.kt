package com.harsh.gymmanager.domain.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppLinkTest {
    @Test
    fun numbersGetTheIndianCountryCode() {
        assertEquals("919876543210", whatsAppNumber("98765 43210"))
        assertEquals("919876543210", whatsAppNumber("+91-98765-43210"))
        assertEquals("919876543210", whatsAppNumber("09876543210"))
        assertNull(whatsAppNumber("12345"))
        assertNull(whatsAppNumber(null))
    }

    @Test
    fun linkEncodesTextIncludingHindiAndNewlines() {
        assertEquals(
            "https://wa.me/919876543210?text=Hi%20Ravi%2C%0A%E2%82%B9500%20%2A%E0%A4%B0%2A",
            whatsAppLink("919876543210", "Hi Ravi,\n₹500 *र*"),
        )
    }
}
