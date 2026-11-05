package com.harsh.gymmanager.domain.usecase

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.OverpaymentException
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class UseCaseTest {
    private val today = LocalDate(2026, 11, 5)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var payments: PaymentUseCases
    private lateinit var dashboard: ObserveDashboardUseCase

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val paymentRepo = PaymentRepositoryImpl(db.paymentDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(MembershipRepositoryImpl(db.membershipDao(), clock), clock, tz)
        payments = PaymentUseCases(paymentRepo, clock, tz)
        dashboard = ObserveDashboardUseCase(memberRepo, paymentRepo, clock, tz)
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
    fun addMemberDefaultsJoinDateAndRejectsBlankName() = runTest {
        val id = members.addMember("Ravi").getOrThrow()
        assertEquals(today, members.observeMember(id).first()!!.joinedOn)
        assertIs<IllegalArgumentException>(members.addMember("  ").exceptionOrNull())
    }

    @Test
    fun sellThenRenewContinuesFromCurrentEnd() = runTest {
        val plan = monthlyPlan()
        val memberId = members.addMember("Ravi").getOrThrow()

        memberships.sellMembership(memberId, plan, discount = 100, firstPaymentAmount = 1000).getOrThrow()
        val quote = memberships.quoteMembership(memberId, plan)
        assertEquals(LocalDate(2026, 12, 5), quote.startDate)
        assertEquals(LocalDate(2027, 1, 4), quote.endDate)

        memberships.sellMembership(memberId, plan, firstPaymentMode = PaymentMode.UPI).getOrThrow()
        val history = memberships.observeMemberships(memberId).first()
        assertEquals(listOf(LocalDate(2026, 12, 5), today), history.map { it.startDate })
        assertEquals(400, history.last().amountDue)
        assertEquals(today, history.last().payments.single().paidOn)
        assertTrue(history.first().payments.isEmpty())
    }

    @Test
    fun sellRejectsFirstPaymentAboveChargeAndSavesNothing() = runTest {
        val plan = monthlyPlan()
        val memberId = members.addMember("Asha").getOrThrow()

        val result = memberships.sellMembership(memberId, plan, discount = 500, firstPaymentAmount = 1001)
        assertEquals(1000, assertIs<OverpaymentException>(result.exceptionOrNull()).amountDue)
        assertTrue(memberships.observeMemberships(memberId).first().isEmpty())
    }

    @Test
    fun recordPaymentDefaultsToTodayAndReportsOverpayment() = runTest {
        val plan = monthlyPlan()
        val memberId = members.addMember("Ravi").getOrThrow()
        val membershipId = memberships.sellMembership(memberId, plan).getOrThrow()

        payments.recordPayment(membershipId, 1000).getOrThrow()
        assertIs<OverpaymentException>(payments.recordPayment(membershipId, 501).exceptionOrNull())
        assertEquals(today, memberships.observeMemberships(memberId).first().single().payments.single().paidOn)
    }

    @Test
    fun filtersAndDashboard() = runTest {
        val plan = monthlyPlan()
        val paidUp = members.addMember("Paid up").getOrThrow()
        memberships.sellMembership(paidUp, plan, firstPaymentAmount = 1500).getOrThrow()
        val expiring = members.addMember("Expiring").getOrThrow()
        memberships.sellMembership(expiring, plan, startDate = LocalDate(2026, 10, 10)).getOrThrow()
        val expired = members.addMember("Expired").getOrThrow()
        memberships.sellMembership(
            expired, plan, startDate = LocalDate(2026, 9, 1), firstPaymentAmount = 1500,
        ).getOrThrow()
        members.addMember("New")

        suspend fun names(filter: MemberFilter) = members.observeMembers(filter = filter).first().map { it.name }
        assertEquals(listOf("Paid up"), names(MemberFilter.ACTIVE))
        assertEquals(listOf("Expiring"), names(MemberFilter.EXPIRING_SOON))
        assertEquals(listOf("Expired"), names(MemberFilter.EXPIRED))
        assertEquals(listOf("New"), names(MemberFilter.NO_MEMBERSHIP))
        assertEquals(listOf("Expiring"), names(MemberFilter.WITH_DUES))

        val board = dashboard().first()
        assertEquals(2, board.activeCount)
        assertEquals(listOf("Expiring"), board.expiringSoon.map { it.name })
        assertEquals(1, board.expiredCount)
        assertEquals(1, board.membersWithDuesCount)
        assertEquals(1500, board.totalDue)
        assertEquals(3000, board.revenueThisMonth)
    }
}
