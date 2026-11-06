package com.harsh.gymmanager.domain.insights

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BriefTest {
    private val today = LocalDate(2026, 11, 15)
    private val insights = computeInsights(
        today,
        emptyList(),
        emptyList(),
        mapOf(LocalDate(2026, 10, 3) to 10_000L, LocalDate(2026, 11, 2) to 11_800L),
    )

    private class FakeModel(private val reply: String?) : OnDeviceModel {
        var calls = 0
        override fun generate(instructions: String, prompt: String, onResult: (String?) -> Unit) {
            calls++
            onResult(reply)
        }
    }

    @Test
    fun factsCarryTheWorkedOutNumbers() {
        val facts = briefFacts(insights, "My Gym")
        assertTrue("₹11,800" in facts, facts)
        assertTrue("18% ahead of last month" in facts, facts)
    }

    @Test
    fun repliesMayRegroupNumbersButNotInventThem() {
        val facts = briefFacts(insights, "My Gym")
        assertTrue(isFaithful("You've collected ₹11800 so far, 18% ahead of last month.", facts))
        assertFalse(isFaithful("You've collected ₹11,800 and should hit ₹25,000.", facts))
    }

    @Test
    fun keepsAFaithfulReplyAndAsksOnlyOncePerFacts() = runTest {
        val model = FakeModel("Collections are ₹11,800, 18% ahead of last month. A strong start.")
        val brief = WriteBriefUseCase(model)

        assertEquals("Collections are ₹11,800, 18% ahead of last month. A strong start.", brief.write(insights, "My Gym"))
        brief.write(insights, "My Gym")
        assertEquals(1, model.calls)
    }

    @Test
    fun fallsBackWhenTheModelIsMissingOrMakesNumbersUp() = runTest {
        assertNull(WriteBriefUseCase(NoOnDeviceModel).write(insights, "My Gym"))
        assertNull(WriteBriefUseCase(FakeModel("You'll make ₹90,000 this month!")).write(insights, "My Gym"))
    }
}
