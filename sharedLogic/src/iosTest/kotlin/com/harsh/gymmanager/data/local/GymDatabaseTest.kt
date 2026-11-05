package com.harsh.gymmanager.data.local

import androidx.room.Room
import com.harsh.gymmanager.data.local.dao.OverpaymentException
import com.harsh.gymmanager.data.local.entity.DurationUnit
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.data.local.entity.PaymentMode
import com.harsh.gymmanager.data.local.entity.PlanEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GymDatabaseTest {
    private lateinit var db: GymDatabase
    private val today = LocalDate(2026, 11, 5)

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun addMember(name: String, phone: String? = null) =
        db.memberDao().insert(MemberEntity(name = name, phone = phone, joinedOn = today))

    private suspend fun addMembership(memberId: Long, start: LocalDate, end: LocalDate, price: Long, discount: Long = 0) =
        db.membershipDao().insert(
            MembershipEntity(
                memberId = memberId, planId = null, planName = "Monthly",
                startDate = start, endDate = end, price = price, discount = discount,
            )
        )

    @Test
    fun summaryComputesDueAcrossPartialPayments() = runTest {
        val memberId = addMember("Ravi", "9876543210")
        val membershipId = addMembership(memberId, today, LocalDate(2026, 12, 4), price = 1500, discount = 200)
        db.paymentDao().addPayment(PaymentEntity(membershipId = membershipId, amount = 500, paidOn = today))
        db.paymentDao().addPayment(
            PaymentEntity(membershipId = membershipId, amount = 300, mode = PaymentMode.UPI, paidOn = today)
        )

        val summary = db.memberDao().observeSummaries().first().single()
        assertEquals(500, summary.amountDue)
        assertEquals(LocalDate(2026, 12, 4), summary.latestEndDate)
        assertEquals(500, db.paymentDao().getAmountDue(membershipId))

        val history = db.membershipDao().observeForMember(memberId).first().single()
        assertEquals(800, history.amountPaid)
        assertEquals(2, history.payments.size)
    }

    @Test
    fun overpaymentIsRejected() = runTest {
        val membershipId = addMembership(addMember("Asha"), today, today, price = 1000)
        db.paymentDao().addPayment(PaymentEntity(membershipId = membershipId, amount = 600, paidOn = today))

        val error = assertFailsWith<OverpaymentException> {
            db.paymentDao().addPayment(PaymentEntity(membershipId = membershipId, amount = 401, paidOn = today))
        }
        assertEquals(400, error.amountDue)
        db.paymentDao().addPayment(PaymentEntity(membershipId = membershipId, amount = 400, paidOn = today))
        assertEquals(0, db.paymentDao().getAmountDue(membershipId))
    }

    @Test
    fun expiringExpiredAndDueFilters() = runTest {
        val expiring = addMember("Expiring")
        addMembership(expiring, LocalDate(2026, 10, 10), LocalDate(2026, 11, 9), price = 1000)
        val expired = addMember("Expired")
        val expiredMembership = addMembership(expired, LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), price = 1000)
        db.paymentDao().addPayment(PaymentEntity(membershipId = expiredMembership, amount = 1000, paidOn = today))
        addMember("No membership")

        val dao = db.memberDao()
        assertEquals(listOf("Expiring"), dao.observeExpiring(today, LocalDate(2026, 11, 12)).first().map { it.name })
        assertEquals(listOf("Expired"), dao.observeExpired(today).first().map { it.name })
        assertEquals(listOf("Expiring"), dao.observeWithDues().first().map { it.name })
    }

    @Test
    fun searchAndArchive() = runTest {
        val ravi = addMember("Ravi Kumar", "9876543210")
        addMember("Asha", "9123456780")
        val dao = db.memberDao()

        assertEquals(listOf("Ravi Kumar"), dao.observeSummaries("ravi").first().map { it.name })
        assertEquals(listOf("Asha"), dao.observeSummaries("91234").first().map { it.name })

        dao.setArchived(ravi, archived = true, updatedAt = kotlin.time.Clock.System.now())
        assertEquals(listOf("Asha"), dao.observeSummaries().first().map { it.name })
        assertEquals(listOf("Ravi Kumar"), dao.observeSummaries(archived = true).first().map { it.name })
    }

    @Test
    fun deletingMemberCascadesAndRevenueSumsByDate() = runTest {
        val memberId = addMember("Temp")
        val membershipId = addMembership(memberId, today, today, price = 2000)
        db.paymentDao().addPayment(PaymentEntity(membershipId = membershipId, amount = 700, paidOn = today))
        db.paymentDao().addPayment(
            PaymentEntity(membershipId = membershipId, amount = 300, paidOn = LocalDate(2026, 12, 1))
        )
        assertEquals(700, db.paymentDao().observeRevenue(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first())

        val member = db.memberDao().observeById(memberId).first()!!
        db.memberDao().delete(member)
        assertTrue(db.membershipDao().observeForMember(memberId).first().isEmpty())
        assertEquals(0, db.paymentDao().observeRevenue(today, LocalDate(2026, 12, 31)).first())
    }

    @Test
    fun archivedPlanIsHiddenButMembershipHistoryRemains() = runTest {
        val planId = db.planDao().insert(
            PlanEntity(name = "Monthly", durationValue = 1, durationUnit = DurationUnit.MONTHS, defaultPrice = 1000)
        )
        val memberId = addMember("Ravi")
        db.membershipDao().insert(
            MembershipEntity(
                memberId = memberId, planId = planId, planName = "Monthly",
                startDate = today, endDate = today, price = 1000,
            )
        )

        db.planDao().setArchived(planId, archived = true)
        assertTrue(db.planDao().observeActive().first().isEmpty())
        assertEquals(1, db.planDao().observeAll().first().size)
        assertEquals(planId, db.membershipDao().observeForMember(memberId).first().single().membership.planId)
    }
}
