package com.harsh.gymmanager.domain.suggestions

import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.suggestions.AmountSuggestion.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestionsTest {
    private val stats = UsageStats(
        planUses = mapOf(3L to 9, 1L to 2),
        paymentAmounts = listOf(1000, 1500, 300),
        discounts = listOf(150, 100),
        saleLengths = listOf(31, 30),
    )

    @Test
    fun amountsStartWithFullThenWhatsUsualThenHalf() {
        val chips = amountSuggestions(due = 1500, stats = stats)
        assertEquals(listOf(1500L to Kind.FULL, 1000L to Kind.USUAL, 300L to Kind.USUAL, 750L to Kind.HALF), chips.map { it.amount to it.kind })
    }

    @Test
    fun newMembershipsCanBePaidLater() {
        val chips = amountSuggestions(due = 600, stats = UsageStats(), payLater = true)
        assertEquals(listOf(600L, 300L, 500L, 0L), chips.map { it.amount })
        assertEquals(Kind.PAY_LATER, chips.last().kind)
    }

    @Test
    fun nothingToSuggestWhenNothingIsDue() {
        assertTrue(amountSuggestions(due = 0, stats = stats).isEmpty())
    }

    @Test
    fun discountsAndLengthsPutTheOwnersHabitsFirst() {
        assertEquals(listOf(150L, 100L, 200L, 500L), discountSuggestions(price = 1500, stats = stats))
        assertEquals(listOf(150L, 100L, 200L), discountSuggestions(price = 300, stats = stats), "never the whole price")
        assertEquals(listOf(31, 30, 45, 90), lengthSuggestions(stats))
    }

    @Test
    fun plansMostSoldFirst() {
        val plans = listOf(1L, 2L, 3L).map { Plan(id = it, name = "Plan $it", durationValue = 1, durationUnit = DurationUnit.MONTHS, defaultPrice = 1000) }
        assertEquals(listOf(3L, 1L, 2L), plans.mostUsedFirst(stats).map { it.id })
    }
}
