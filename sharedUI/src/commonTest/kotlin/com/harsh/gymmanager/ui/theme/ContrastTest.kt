package com.harsh.gymmanager.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every text colour must keep 4.5:1 on the grounds the design system says it sits on, in both themes. */
class ContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (max(la, lb) / min(la, lb)).toDouble()
    }

    private fun pairs(c: AkhadaColors) = listOf(
        "ink/bg" to (c.ink to c.bg), "ink/surface" to (c.ink to c.surface), "ink/sunken" to (c.ink to c.surfaceSunken),
        "muted/bg" to (c.inkMuted to c.bg), "muted/surface" to (c.inkMuted to c.surface),
        "muted/sunken" to (c.inkMuted to c.surfaceSunken), "onInk/ink" to (c.onInk to c.ink),
        "onAccent/accent" to (c.onAccent to c.accent), "accent/surface" to (c.accent to c.surface),
        "onAccentSoft/accentSoft" to (c.onAccentSoft to c.accentSoft),
        "active" to (c.statusActive to c.statusActiveSoft), "expiring" to (c.statusExpiring to c.statusExpiringSoft),
        "expired" to (c.statusExpired to c.statusExpiredSoft), "due" to (c.statusDue to c.statusDueSoft),
        "due/surface" to (c.statusDue to c.surface),
    )

    @Test
    fun textPairsMeetAa() {
        for ((theme, colors) in listOf("light" to AkhadaLightColors, "dark" to AkhadaDarkColors)) {
            for ((name, pair) in pairs(colors)) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5, "$theme $name is ${ratio}:1")
            }
        }
    }
}
