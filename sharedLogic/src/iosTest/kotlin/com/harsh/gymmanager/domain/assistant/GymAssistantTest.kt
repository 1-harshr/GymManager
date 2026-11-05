package com.harsh.gymmanager.domain.assistant

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class GymAssistantTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var assistant: GymAssistant

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val paymentRepo = PaymentRepositoryImpl(db.paymentDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(MembershipRepositoryImpl(db.membershipDao(), clock), clock, tz)
        assistant = GymAssistant(
            members,
            plans,
            memberships,
            PaymentUseCases(paymentRepo, clock, tz),
            ObserveDashboardUseCase(memberRepo, paymentRepo, clock, tz),
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun monthlyPlan(): Plan {
        val id = plans.addPlan("Monthly", 1, DurationUnit.MONTHS, defaultPrice = 1500).getOrThrow()
        return plans.observePlans().first().single { it.id == id }
    }

    @Test
    fun paymentSettlesOldestMembershipFirst() = runTest {
        val plan = monthlyPlan()
        val memberId = members.addMember("Ravi").getOrThrow()
        memberships.sellMembership(memberId, plan, firstPaymentAmount = 1000).getOrThrow()
        memberships.sellMembership(memberId, plan).getOrThrow()

        val outcome = assistant.recordPayment(memberId, 800, PaymentMode.UPI)

        assertEquals(PaymentOutcome.Recorded("Ravi", amount = 800, remainingDue = 1200), outcome)
        val (newer, older) = memberships.observeMemberships(memberId).first()
        assertEquals(0, older.amountDue)
        assertEquals(1200, newer.amountDue)
        assertEquals(PaymentMode.UPI, newer.payments.single().mode)
    }

    @Test
    fun paymentAboveDuesOrNotPositiveSavesNothing() = runTest {
        val plan = monthlyPlan()
        val memberId = members.addMember("Ravi").getOrThrow()
        memberships.sellMembership(memberId, plan, firstPaymentAmount = 1000).getOrThrow()

        assertEquals(PaymentOutcome.MoreThanDue("Ravi", amountDue = 500), assistant.recordPayment(memberId, 501))
        assertEquals(PaymentOutcome.InvalidAmount, assistant.recordPayment(memberId, 0))
        assertEquals(PaymentOutcome.MemberNotFound, assistant.recordPayment(memberId + 1, 100))
        assertEquals(500, assistant.summary().totalDue)
    }

    @Test
    fun membersWithDuesComeLargestFirstAndAreCapped() = runTest {
        val plan = monthlyPlan()
        listOf(1000L, 0L, 200L).forEachIndexed { i, paid ->
            val id = members.addMember("Member $i").getOrThrow()
            memberships.sellMembership(id, plan, firstPaymentAmount = paid.takeIf { it > 0 }).getOrThrow()
        }

        val list = assistant.members(MemberFilter.WITH_DUES, limit = 2)

        assertEquals(3, list.totalCount)
        assertEquals(listOf("Member 1", "Member 2"), list.members.map { it.name })
    }

    @Test
    fun findMembersMatchesNameOrPhone() = runTest {
        members.addMember("Ravi Kumar", phone = "9876543210").getOrThrow()
        members.addMember("Priya").getOrThrow()

        assertEquals(listOf("Ravi Kumar"), assistant.findMembers(" ravi ").members.map { it.name })
        assertEquals(listOf("Ravi Kumar"), assistant.findMembers("98765").members.map { it.name })
    }
}
