package com.harsh.gymmanager.domain.demo

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.insights.ObserveInsightsUseCase
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Seeds an in-memory database, as demo mode does its own file. */
class DemoDataTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-06T10:00:00Z")
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
    fun seedsAGymWithSomethingToShowEverywhere() = runTest {
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val membershipRepo = MembershipRepositoryImpl(db.membershipDao(), clock)
        val paymentRepo = PaymentRepositoryImpl(db.paymentDao(), clock)
        val members = MemberUseCases(memberRepo, clock, tz)
        val plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        val demo = DemoDataUseCase(members, plans, MembershipUseCases(membershipRepo, clock, tz), PaymentUseCases(paymentRepo, clock, tz), clock, tz)

        assertEquals(18, demo.seed().getOrThrow())

        assertEquals(5, plans.observePlans().first().size)
        assertTrue(members.observeMembers().first().none { it.phone != null }, "no number a message could reach")
        val insights = ObserveInsightsUseCase(memberRepo, membershipRepo, paymentRepo, clock, tz)().first()
        assertTrue(insights.pace.thisMonthToDate > 0 && insights.pace.lastMonthTotal > 0, "both months have collections")
        assertTrue(insights.renewals.endingSoon.size >= 3, "renewals due this week")
        assertTrue(insights.renewals.renewedRecently >= 1, "a recent renewal")
        assertTrue(insights.oldestDues.isNotEmpty(), "an old due")
        assertTrue(insights.winBack.size >= 2, "recent lapses")
    }
}
