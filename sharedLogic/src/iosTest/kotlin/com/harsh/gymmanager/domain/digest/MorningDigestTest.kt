package com.harsh.gymmanager.domain.digest

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class MorningDigestTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var digests: MorningDigestUseCase

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(MembershipRepositoryImpl(db.membershipDao(), clock), clock, tz)
        digests = MorningDigestUseCase(memberRepo, PaymentRepositoryImpl(db.paymentDao(), clock))
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    /** Sells a 1500 plan ending on [end], with [paid] paid today. */
    private suspend fun memberEnding(name: String, end: LocalDate, paid: Long = 1500) {
        val planId = plans.addPlan("Monthly", 1, DurationUnit.MONTHS, defaultPrice = 1500).getOrThrow()
        val plan = plans.observePlans().first().single { it.id == planId }
        val id = members.addMember(name).getOrThrow()
        memberships.sellMembership(
            id, plan, startDate = LocalDate(2026, 10, 1), endDate = end, firstPaymentAmount = paid.takeIf { it > 0 },
        ).getOrThrow()
    }

    @Test
    fun sortsMembersByWhenTheirMembershipEnds() = runTest {
        memberEnding("Ravi", LocalDate(2026, 11, 6))
        memberEnding("Priya", LocalDate(2026, 11, 5))
        memberEnding("Amit", LocalDate(2026, 11, 10))
        memberEnding("Neha", LocalDate(2026, 12, 20))

        val digest = digests.digest(LocalDate(2026, 11, 6))

        assertEquals(listOf("Ravi"), digest.endingToday)
        assertEquals(listOf("Priya"), digest.endedYesterday)
        assertEquals(1, digest.endingSoonCount)
    }

    @Test
    fun laterMorningsMoveAlongButKeepDuesAndResetCollectionsEachMonth() = runTest {
        memberEnding("Ravi", LocalDate(2026, 12, 1), paid = 1000)

        val days = digests.digests(LocalDate(2026, 11, 29), days = 4)

        assertEquals(listOf(emptyList(), emptyList(), listOf("Ravi"), emptyList()), days.map { it.endingToday })
        assertEquals(listOf("Ravi"), days[3].endedYesterday)
        assertEquals(listOf(500L, 500, 500, 500), days.map { it.totalDue })
        assertEquals(listOf(1000L, 1000, 0, 0), days.map { it.collectedThisMonth })
    }
}
