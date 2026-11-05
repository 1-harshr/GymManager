package com.harsh.gymmanager.domain.insights

import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.membershipStatus
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GymInsightsTest {
    private val today = LocalDate(2026, 11, 15)
    private var nextId = 1L

    private fun membership(memberId: Long, start: LocalDate, end: LocalDate, price: Long = 1500, paid: Long = price) =
        Membership(
            id = nextId++, memberId = memberId, planId = 1, planName = "Monthly", startDate = start, endDate = end, price = price,
            payments = if (paid > 0) listOf(Payment(membershipId = 0, amount = paid, paidOn = start)) else emptyList(),
        )

    private fun summaries(names: Map<Long, String>, memberships: List<Membership>) = names.map { (id, name) ->
        val end = memberships.filter { it.memberId == id }.maxOfOrNull { it.endDate }
        MemberSummary(
            memberId = id, name = name, phone = null, photoPath = null, isArchived = false, latestEndDate = end,
            amountDue = memberships.filter { it.memberId == id }.sumOf { it.amountDue },
            status = membershipStatus(end, today, EXPIRING_SOON_DAYS),
        )
    }

    @Test
    fun paceComparesWithLastMonthUpToTheSameDay() {
        val daily = mapOf(
            LocalDate(2026, 10, 3) to 1000L,
            LocalDate(2026, 10, 20) to 4000L, // after the 15th, so not in "to date"
            LocalDate(2026, 11, 2) to 600L,
            LocalDate(2026, 11, 15) to 600L,
        )

        val pace = computeInsights(today, emptyList(), emptyList(), daily).pace

        assertEquals(1200, pace.thisMonthToDate)
        assertEquals(1000, pace.lastMonthToDate)
        assertEquals(5000, pace.lastMonthTotal)
        assertEquals(20, pace.changePercent)
        assertEquals(15, pace.thisMonthCumulative.size)
        assertEquals(31, pace.lastMonthCumulative.size)
        assertEquals(listOf(0L, 600, 600), pace.thisMonthCumulative.take(3))
    }

    @Test
    fun noComparisonWhenLastMonthHadNothing() {
        assertNull(computeInsights(today, emptyList(), emptyList(), emptyMap()).pace.changePercent)
    }

    @Test
    fun renewalsDuesAndWinBacks() {
        val memberships = listOf(
            // Ravi: ends in 3 days, 500 still owed since 19 Oct.
            membership(1, LocalDate(2026, 10, 19), LocalDate(2026, 11, 18), paid = 1000),
            // Priya: ended 5 days ago, renewed.
            membership(2, LocalDate(2026, 10, 11), LocalDate(2026, 11, 10)),
            membership(2, LocalDate(2026, 11, 11), LocalDate(2026, 12, 10), paid = 0),
            // Amit: ended 2 days ago, hasn't renewed.
            membership(3, LocalDate(2026, 10, 14), LocalDate(2026, 11, 13), price = 2000),
            // Neha: ended long ago; outside the window.
            membership(4, LocalDate(2026, 8, 1), LocalDate(2026, 8, 31)),
        )
        val members = summaries(mapOf(1L to "Ravi", 2L to "Priya", 3L to "Amit", 4L to "Neha"), memberships)

        val insights = computeInsights(today, members, memberships, emptyMap())

        assertEquals(listOf("Ravi"), insights.renewals.endingSoon.map { it.name })
        assertEquals(1500, insights.renewals.atStake)
        assertEquals(2, insights.renewals.endedRecently)
        assertEquals(1, insights.renewals.renewedRecently)
        assertEquals(50, insights.renewals.renewalPercent)

        // Priya's 1500 is only 4 days old, so only Ravi's 27-day-old due needs attention.
        assertEquals(listOf("Ravi"), insights.oldestDues.map { it.name })
        assertEquals(27, insights.oldestDues.single().days)
        assertEquals(1, insights.overdueCount)

        assertEquals(listOf("Amit"), insights.winBack.map { it.name })
        assertEquals(2, insights.winBack.single().daysAgo)
        assertEquals(1, insights.winBackCount)

        // Ravi ends this month (renewal worth 1500); dues are Ravi's 500 and Priya's 1500.
        assertEquals(1, insights.forecast.renewalsCount)
        assertEquals(1500, insights.forecast.renewalsValue)
        assertEquals(2000, insights.forecast.outstandingDues)
    }
}
