package com.harsh.gymmanager.domain.messaging

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.MessageLogRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.repository.SettingsRepository
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class MessagingUseCasesTest {
    private var now = Instant.parse("2026-11-05T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@MessagingUseCasesTest.now
    }
    private val tz = TimeZone.UTC

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings(gym = GymProfile("Iron House")))
        override fun observe() = state
        override suspend fun setGym(profile: GymProfile) { state.value = state.value.copy(gym = profile) }
        override suspend fun setTheme(theme: ThemeMode) {}
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setLastBackupAt(at: Instant) {}
        override suspend fun setMorningUpdate(update: MorningUpdate) {}
        override suspend fun setHomeFeatures(features: HomeFeatures) {}
        override suspend fun setMembersAsGrid(grid: Boolean) {}
    }

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var payments: PaymentUseCases
    private lateinit var messaging: MessagingUseCases

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val membershipRepo = MembershipRepositoryImpl(db.membershipDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(membershipRepo, clock, tz)
        payments = PaymentUseCases(PaymentRepositoryImpl(db.paymentDao(), clock), clock, tz)
        messaging = MessagingUseCases(memberRepo, membershipRepo, FakeSettings(), MessageLogRepositoryImpl(db.messageLogDao(), clock), clock, tz)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun sell(memberId: Long, start: LocalDate, paid: Long? = 1500) {
        val planId = plans.observePlans().first().firstOrNull()?.id
            ?: plans.addPlan("Monthly", 1, DurationUnit.MONTHS, defaultPrice = 1500).getOrThrow()
        val plan = plans.observePlans().first().single { it.id == planId }
        memberships.sellMembership(memberId, plan, startDate = start, firstPaymentAmount = paid).getOrThrow()
    }

    @Test
    fun welcomeNeedsAMobileNumber() = runTest {
        val withPhone = members.addMember("Ravi Kumar", phone = "98765 43210").getOrThrow()
        val withoutPhone = members.addMember("Sita").getOrThrow()

        val welcome = assertNotNull(messaging.welcome(withPhone))
        assertEquals("919876543210", welcome.whatsAppNumber)
        assertEquals("Iron House", welcome.gymName)
        assertNull(messaging.welcome(withoutPhone))
    }

    @Test
    fun receiptCoversTheSaleOrOnePayment() = runTest {
        val id = members.addMember("Ravi", phone = "9876543210").getOrThrow()
        sell(id, LocalDate(2026, 11, 5), paid = 1000)
        val membership = memberships.observeMemberships(id).first().single()

        val sale = assertNotNull(messaging.receipt(id, membership.id))
        assertEquals(1000, sale.amountPaid)
        assertEquals(500, sale.amountDue)
        assertNull(sale.payment)

        val paymentId = payments.recordPayment(membership.id, 500, PaymentMode.UPI).getOrThrow()
        val receipt = assertNotNull(messaging.receipt(id, membership.id, paymentId))
        assertEquals(500, receipt.payment?.amount)
        assertEquals(0, receipt.amountDue)
        assertEquals("${membership.id}-$paymentId", receipt.receiptNumber)
    }

    @Test
    fun remindersCoverEndingRecentlyEndedAndOwing() = runTest {
        val ending = members.addMember("Ending", phone = "9000000001").getOrThrow()
        sell(ending, LocalDate(2026, 10, 9)) // ends 8 Nov
        val recentlyEnded = members.addMember("Ended", phone = "9000000002").getOrThrow()
        sell(recentlyEnded, LocalDate(2026, 9, 20)) // ended 19 Oct
        val longGone = members.addMember("Gone", phone = "9000000003").getOrThrow()
        sell(longGone, LocalDate(2026, 6, 1)) // ended in June
        val owing = members.addMember("Owing").getOrThrow()
        sell(owing, LocalDate(2026, 11, 1), paid = 500)
        val fine = members.addMember("Fine", phone = "9000000005").getOrThrow()
        sell(fine, LocalDate(2026, 11, 1))

        val items = messaging.observeReminders().first()
        assertEquals(listOf(ending, recentlyEnded, owing), items.map { it.member.memberId })
        assertEquals(MembershipStatus.EXPIRING_SOON, items[0].message?.status)
        assertNull(items[2].message, "no phone, so nothing to send")
        assertEquals(2, messaging.observeRemindersToSend().first())
    }

    @Test
    fun aSentReminderRestsForAFewDays() = runTest {
        val id = members.addMember("Ravi", phone = "9876543210").getOrThrow()
        sell(id, LocalDate(2026, 10, 9))
        val reminder = assertNotNull(messaging.reminder(id))

        messaging.markSent(reminder).getOrThrow()
        val sent = messaging.observeReminders().first().single()
        assertTrue(sent.sentRecently)
        assertEquals(now, sent.lastSentAt)
        assertEquals(0, messaging.observeRemindersToSend().first())

        now += REMINDER_COOLDOWN_DAYS.days
        assertFalse(messaging.observeReminders().first().single().sentRecently)
    }
}
