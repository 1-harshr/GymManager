package com.harsh.gymmanager.domain

import com.harsh.gymmanager.data.local.entity.DurationUnit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MembershipRulesTest {
    private val today = LocalDate(2026, 11, 5)

    @Test
    fun monthPlanEndsTheDayBeforeTheSameDateNextMonth() {
        assertEquals(LocalDate(2026, 2, 14), membershipEndDate(LocalDate(2026, 1, 15), 1, DurationUnit.MONTHS))
        assertEquals(LocalDate(2027, 1, 14), membershipEndDate(LocalDate(2026, 1, 15), 12, DurationUnit.MONTHS))
    }

    @Test
    fun dayPlanIncludesTheStartDay() {
        assertEquals(LocalDate(2026, 11, 5), membershipEndDate(today, 1, DurationUnit.DAYS))
        assertEquals(LocalDate(2026, 12, 4), membershipEndDate(today, 30, DurationUnit.DAYS))
    }

    @Test
    fun renewalContinuesFromRunningMembership() {
        assertEquals(LocalDate(2026, 11, 21), renewalStartDate(LocalDate(2026, 11, 20), today))
        assertEquals(LocalDate(2026, 11, 6), renewalStartDate(today, today))
    }

    @Test
    fun renewalStartsTodayWhenExpiredOrNew() {
        assertEquals(today, renewalStartDate(LocalDate(2026, 10, 1), today))
        assertEquals(today, renewalStartDate(null, today))
    }

    @Test
    fun statusFromLatestEndDate() {
        assertEquals(MembershipStatus.NONE, membershipStatus(null, today, 7))
        assertEquals(MembershipStatus.EXPIRED, membershipStatus(LocalDate(2026, 11, 4), today, 7))
        assertEquals(MembershipStatus.EXPIRING_SOON, membershipStatus(today, today, 7))
        assertEquals(MembershipStatus.EXPIRING_SOON, membershipStatus(LocalDate(2026, 11, 12), today, 7))
        assertEquals(MembershipStatus.ACTIVE, membershipStatus(LocalDate(2026, 11, 13), today, 7))
    }
}
