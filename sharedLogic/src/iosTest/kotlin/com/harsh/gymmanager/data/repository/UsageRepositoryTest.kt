package com.harsh.gymmanager.data.repository

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class UsageRepositoryTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC
    private lateinit var db: GymDatabase

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun countsPlansAmountsAndDiscounts() = runTest {
        val members = MemberUseCases(MemberRepositoryImpl(db.memberDao(), clock, tz), clock, tz)
        val plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        val memberships = MembershipUseCases(MembershipRepositoryImpl(db.membershipDao(), clock), clock, tz)
        val monthly = plans.getPlan(plans.addPlan("Monthly", 1, DurationUnit.MONTHS, 1500).getOrThrow())!!
        val trial = plans.getPlan(plans.addPlan("Trial", 1, DurationUnit.DAYS, 100).getOrThrow())!!
        repeat(2) {
            val id = members.addMember("Member $it").getOrThrow()
            memberships.sellMembership(id, monthly, LocalDate(2026, 11, 1), LocalDate(2026, 11, 30), discount = 200, firstPaymentAmount = 1300).getOrThrow()
        }
        memberships.sellMembership(members.addMember("Guest").getOrThrow(), trial, firstPaymentAmount = 100).getOrThrow()

        val stats = UsageRepositoryImpl(db.usageDao()).stats()

        assertEquals(mapOf(monthly.id to 2, trial.id to 1), stats.planUses)
        assertEquals(listOf(1300L, 100L), stats.paymentAmounts)
        assertEquals(listOf(200L), stats.discounts)
    }
}
