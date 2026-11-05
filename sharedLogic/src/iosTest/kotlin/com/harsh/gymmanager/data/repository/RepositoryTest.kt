package com.harsh.gymmanager.data.repository

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.OverpaymentException
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class RepositoryTest {
    private val today = LocalDate(2026, 11, 5)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }

    private lateinit var db: GymDatabase
    private lateinit var members: MemberRepositoryImpl
    private lateinit var plans: PlanRepositoryImpl
    private lateinit var memberships: MembershipRepositoryImpl
    private lateinit var payments: PaymentRepositoryImpl

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        members = MemberRepositoryImpl(db.memberDao(), clock, TimeZone.UTC)
        plans = PlanRepositoryImpl(db.planDao(), clock)
        memberships = MembershipRepositoryImpl(db.membershipDao(), clock)
        payments = PaymentRepositoryImpl(db.paymentDao(), clock)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun addMember(name: String, phone: String? = null) =
        members.addMember(Member(name = name, phone = phone, joinedOn = today))

    private suspend fun addMembership(memberId: Long, start: LocalDate, end: LocalDate, price: Long, discount: Long = 0) =
        memberships.addMembership(
            Membership(
                memberId = memberId, planId = null, planName = "Monthly",
                startDate = start, endDate = end, price = price, discount = discount,
            )
        )

    @Test
    fun partialPaymentsReduceAmountDue() = runTest {
        val memberId = addMember("Ravi", "9876543210")
        val membershipId = addMembership(memberId, today, LocalDate(2026, 12, 4), price = 1500, discount = 200)
        payments.addPayment(Payment(membershipId = membershipId, amount = 500, paidOn = today))
        payments.addPayment(Payment(membershipId = membershipId, amount = 300, mode = PaymentMode.UPI, paidOn = today))

        val summary = members.observeMembers().first().single()
        assertEquals(500, summary.amountDue)
        assertEquals(MembershipStatus.ACTIVE, summary.status)

        val membership = memberships.observeMemberships(memberId).first().single()
        assertEquals(1300, membership.amountCharged)
        assertEquals(800, membership.amountPaid)
        assertEquals(500, membership.amountDue)
        assertEquals(listOf(PaymentMode.CASH, PaymentMode.UPI), membership.payments.map { it.mode })
    }

    @Test
    fun overpaymentIsRejected() = runTest {
        val membershipId = addMembership(addMember("Asha"), today, today, price = 1000)
        payments.addPayment(Payment(membershipId = membershipId, amount = 600, paidOn = today))

        val error = assertFailsWith<OverpaymentException> {
            payments.addPayment(Payment(membershipId = membershipId, amount = 401, paidOn = today))
        }
        assertEquals(400, error.amountDue)
        payments.addPayment(Payment(membershipId = membershipId, amount = 400, paidOn = today))
        assertEquals(0, payments.getAmountDue(membershipId))
    }

    @Test
    fun expiringExpiredAndDueListsUseToday() = runTest {
        val expiring = addMember("Expiring")
        addMembership(expiring, LocalDate(2026, 10, 10), LocalDate(2026, 11, 9), price = 1000)
        val expired = addMember("Expired")
        val expiredMembership = addMembership(expired, LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), price = 1000)
        payments.addPayment(Payment(membershipId = expiredMembership, amount = 1000, paidOn = today))
        addMember("No membership")

        val expiringList = members.observeExpiring().first()
        assertEquals(listOf("Expiring"), expiringList.map { it.name })
        assertEquals(MembershipStatus.EXPIRING_SOON, expiringList.single().status)
        assertEquals(listOf("Expired"), members.observeExpired().first().map { it.name })
        assertEquals(listOf("Expiring"), members.observeWithDues().first().map { it.name })
        assertEquals(
            MembershipStatus.NONE,
            members.observeMembers("No membership").first().single().status,
        )
    }

    @Test
    fun searchArchiveAndRestore() = runTest {
        val ravi = addMember("Ravi Kumar", "9876543210")
        addMember("Asha", "9123456780")

        assertEquals(listOf("Ravi Kumar"), members.observeMembers("ravi").first().map { it.name })
        assertEquals(listOf("Asha"), members.observeMembers(" 91234 ").first().map { it.name })

        members.archiveMember(ravi)
        assertEquals(listOf("Asha"), members.observeMembers().first().map { it.name })
        assertEquals(listOf("Ravi Kumar"), members.observeMembers(archived = true).first().map { it.name })

        members.restoreMember(ravi)
        assertEquals(2, members.observeMembers().first().size)
    }

    @Test
    fun updateMemberKeepsCreatedAtAndCleansBlankFields() = runTest {
        val id = members.addMember(Member(name = "  Ravi  ", phone = "  ", joinedOn = today))
        val saved = members.observeMember(id).first()!!
        assertEquals("Ravi", saved.name)
        assertNull(saved.phone)

        members.updateMember(saved.copy(name = "Ravi Kumar", phone = "9876543210"))
        val updated = members.observeMember(id).first()!!
        assertEquals("Ravi Kumar", updated.name)
        assertEquals(clock.now(), db.memberDao().getById(id)!!.createdAt)
    }

    @Test
    fun permanentDeleteRemovesHistoryAndRevenue() = runTest {
        val memberId = addMember("Temp")
        val membershipId = addMembership(memberId, today, today, price = 2000)
        payments.addPayment(Payment(membershipId = membershipId, amount = 700, paidOn = today))
        payments.addPayment(Payment(membershipId = membershipId, amount = 300, paidOn = LocalDate(2026, 12, 1)))
        assertEquals(700, payments.observeRevenue(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first())

        members.deleteMemberPermanently(memberId)
        assertTrue(memberships.observeMemberships(memberId).first().isEmpty())
        assertEquals(0, payments.observeRevenue(today, LocalDate(2026, 12, 31)).first())
    }

    @Test
    fun archivedPlanIsHiddenButMembershipKeepsIt() = runTest {
        val plan = Plan(name = "Monthly", durationValue = 1, durationUnit = DurationUnit.MONTHS, defaultPrice = 1000)
        val planId = plans.addPlan(plan)
        val memberId = addMember("Ravi")
        memberships.addMembership(
            Membership(
                memberId = memberId, planId = planId, planName = plan.name,
                startDate = today, endDate = plan.endDateFor(today), price = plan.defaultPrice,
            )
        )

        plans.archivePlan(planId)
        assertTrue(plans.observeActivePlans().first().isEmpty())
        assertEquals(1, plans.observeAllPlans().first().size)

        val membership = memberships.observeMemberships(memberId).first().single()
        assertEquals(planId, membership.planId)
        assertEquals(LocalDate(2026, 12, 4), membership.endDate)
        assertEquals(LocalDate(2026, 12, 4), memberships.getLatestEndDate(memberId))
    }
}
