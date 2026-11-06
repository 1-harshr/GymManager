package com.harsh.gymmanager.domain.whatsapp

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.MessageLogRepositoryImpl
import com.harsh.gymmanager.data.repository.OutboxRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.messaging.MessagingUseCases
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.repository.SettingsRepository
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class WhatsAppMessagingTest {
    private var now = Instant.parse("2026-11-05T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@WhatsAppMessagingTest.now
    }
    private val tz = TimeZone.UTC
    private val hook = "https://hook.eu2.make.com/abc123"

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings(gym = GymProfile("Iron House")))
        override fun observe() = state
        override suspend fun setGym(profile: GymProfile) {}
        override suspend fun setTheme(theme: ThemeMode) {}
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setLastBackupAt(at: Instant) {}
        override suspend fun setMorningUpdate(update: MorningUpdate) {}
        override suspend fun setHomeFeatures(features: HomeFeatures) {}
        override suspend fun setMembersAsGrid(grid: Boolean) {}
    }

    private class FakeWhatsAppSettings(initial: WhatsAppSettings) : WhatsAppSettingsRepository {
        val state = MutableStateFlow(initial)
        override fun observe() = state
        override suspend fun setWebhookUrl(url: String?) { state.value = state.value.copy(webhookUrl = url) }
        override suspend fun setAutoSend(autoSend: AutoSend) { state.value = state.value.copy(autoSend = autoSend) }
        override suspend fun setLastReminderRun(day: LocalDate) { state.value = state.value.copy(lastReminderRun = day) }
    }

    /** Answers posts with [results] in order (accepting once they run out) and records them. */
    private class FakeWebhook : MessageWebhook {
        val posted = mutableListOf<Pair<String, OutboxMessage>>()
        val results = ArrayDeque<WebhookResult>()
        override suspend fun post(url: String, message: OutboxMessage): WebhookResult {
            posted += url to message
            return results.removeFirstOrNull() ?: WebhookResult.Accepted
        }
    }

    private class CountingScheduler : WhatsAppScheduler {
        var changes = 0
        override fun outboxChanged() { changes++ }
        override suspend fun sync(settings: WhatsAppSettings) {}
    }

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var messaging: MessagingUseCases
    private lateinit var log: MessageLogRepositoryImpl
    private lateinit var outbox: OutboxRepositoryImpl
    private val webhook = FakeWebhook()
    private val scheduler = CountingScheduler()
    private val waSettings = FakeWhatsAppSettings(WhatsAppSettings(webhookUrl = hook))
    private var ids = 0

    private fun whatsApp(webhook: MessageWebhook? = this.webhook) = WhatsAppMessaging(
        webhook, waSettings, outbox, messaging, log, { scheduler }, clock, tz, newClientId = { "c${++ids}" },
    )

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val membershipRepo = MembershipRepositoryImpl(db.membershipDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(membershipRepo, clock, tz)
        log = MessageLogRepositoryImpl(db.messageLogDao(), clock)
        outbox = OutboxRepositoryImpl(db.outboxDao())
        messaging = MessagingUseCases(memberRepo, membershipRepo, FakeSettings(), log, clock, tz)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun welcome(name: String = "Ravi Kumar", phone: String = "98765 43210"): MemberMessage.Welcome =
        messaging.welcome(members.addMember(name, phone = phone).getOrThrow())!!

    private suspend fun sell(memberId: String, start: LocalDate, paid: Long = 1500) {
        val planId = plans.observePlans().first().firstOrNull()?.id
            ?: plans.addPlan("Monthly", 1, DurationUnit.MONTHS, defaultPrice = 1500).getOrThrow()
        memberships.sellMembership(memberId, plans.observePlans().first().single { it.id == planId }, startDate = start, firstPaymentAmount = paid).getOrThrow()
    }

    @Test
    fun automaticSendNeedsTheSwitchAndAWebhook() = runTest {
        val whatsApp = whatsApp()
        val message = welcome()
        assertFalse(whatsApp.sendAutomatically(message), "switch is off")

        waSettings.setAutoSend(AutoSend(welcome = true))
        waSettings.setWebhookUrl(null)
        assertFalse(whatsApp.sendAutomatically(message), "Make isn't set up")

        waSettings.setWebhookUrl(hook)
        assertTrue(whatsApp.sendAutomatically(message))
        val queued = outbox.pending().single()
        assertEquals("gym_welcome", queued.templateName)
        assertEquals(listOf("Ravi", "Iron House"), queued.parameters)
        assertEquals("919876543210", queued.recipient)
        assertEquals(1, scheduler.changes)
        assertTrue(log.observeLastSent().first()[message.memberId]?.containsKey(MessageKind.WELCOME) == true, "logged for the cooldown")
    }

    @Test
    fun flushPostsToTheWebhookAndStopsAtNetworkTrouble() = runTest {
        val whatsApp = whatsApp()
        repeat(3) { i -> whatsApp.sendNow(welcome("Member$i", "90000000${10 + i}")) }
        webhook.results += WebhookResult.Accepted
        webhook.results += WebhookResult.TryLater("offline")

        assertEquals(FlushResult.RETRY_LATER, whatsApp.flush())
        val rows = outbox.observeRecent(10).first().sortedBy { it.id }
        assertEquals(listOf(OutboxStatus.SENT, OutboxStatus.PENDING, OutboxStatus.PENDING), rows.map { it.status })
        assertEquals(1, rows[1].attempts)
        assertEquals("offline", rows[1].errorMessage)
        assertEquals(listOf(hook, hook), webhook.posted.map { it.first })

        assertEquals(FlushResult.DONE, whatsApp.flush())
        assertTrue(outbox.pending().isEmpty())
        assertEquals("c2", webhook.posted[2].second.clientId, "the retry keeps its id, so the scenario can spot a repeat")
    }

    @Test
    fun refusedMessageFailsAndTheRestStillGo() = runTest {
        val whatsApp = whatsApp()
        whatsApp.sendNow(welcome("Gone", "9000000001"))
        whatsApp.sendNow(welcome("Fine", "9000000002"))
        webhook.results += WebhookResult.Refused("HTTP 400: Template not found")

        assertEquals(FlushResult.DONE, whatsApp.flush())
        val rows = outbox.observeRecent(10).first().sortedBy { it.id }
        assertEquals(OutboxStatus.FAILED, rows[0].status)
        assertEquals("HTTP 400: Template not found", rows[0].errorMessage)
        assertEquals(OutboxStatus.SENT, rows[1].status)
    }

    @Test
    fun keepsFailingUntilItGivesUp() = runTest {
        val whatsApp = whatsApp()
        whatsApp.sendNow(welcome())
        repeat(WhatsAppMessaging.MAX_ATTEMPTS) { webhook.results += WebhookResult.TryLater("HTTP 500") }

        repeat(WhatsAppMessaging.MAX_ATTEMPTS) { whatsApp.flush() }
        assertEquals(OutboxStatus.FAILED, outbox.observeRecent(1).first().single().status)
    }

    @Test
    fun withoutAWebhookMessagesWait() = runTest {
        val whatsApp = whatsApp()
        whatsApp.sendNow(welcome())
        waSettings.setWebhookUrl(null)

        assertEquals(FlushResult.RETRY_LATER, whatsApp.flush())
        assertTrue(webhook.posted.isEmpty())
        assertEquals(1, outbox.pending().size)
    }

    @Test
    fun remindersGoOutOnceADayAfterTheirTime() = runTest {
        val whatsApp = whatsApp()
        val ending = members.addMember("Ending", phone = "9000000001").getOrThrow()
        sell(ending, LocalDate(2026, 10, 9)) // ends 8 Nov
        val owing = members.addMember("Owing", phone = "9000000002").getOrThrow()
        sell(owing, LocalDate(2026, 11, 1), paid = 500)
        val fine = members.addMember("Fine", phone = "9000000003").getOrThrow()
        sell(fine, LocalDate(2026, 11, 1))
        waSettings.setAutoSend(AutoSend(reminders = true, reminderMinuteOfDay = 11 * 60))

        assertEquals(0, whatsApp.sendDueReminders(), "10:00 is before the 11:00 reminder time")
        now = Instant.parse("2026-11-05T11:30:00Z")
        assertEquals(2, whatsApp.sendDueReminders())
        assertEquals(setOf("gym_membership_ending", "gym_payment_due"), outbox.pending().map { it.templateName }.toSet())
        assertEquals(0, whatsApp.sendDueReminders(), "once a day")

        // Next day: both were reminded within the cooldown, so nobody is sent again yet.
        now = Instant.parse("2026-11-06T11:30:00Z")
        assertEquals(0, whatsApp.sendDueReminders())
        assertEquals(LocalDate(2026, 11, 6), waSettings.state.value.lastReminderRun)
    }

    @Test
    fun turningRemindersOnAfterTheirTimeStartsTomorrow() = runTest {
        val whatsApp = whatsApp()
        val owing = members.addMember("Owing", phone = "9000000002").getOrThrow()
        sell(owing, LocalDate(2026, 11, 1), paid = 500)

        whatsApp.setAutoSend(AutoSend(reminders = true, reminderMinuteOfDay = 9 * 60)) // it's 10:00
        assertEquals(0, whatsApp.sendDueReminders())
        now = Instant.parse("2026-11-06T09:05:00Z")
        assertEquals(1, whatsApp.sendDueReminders())
    }

    @Test
    fun turningRemindersOnBeforeTheirTimeSendsToday() = runTest {
        val whatsApp = whatsApp()
        val owing = members.addMember("Owing", phone = "9000000002").getOrThrow()
        sell(owing, LocalDate(2026, 11, 1), paid = 500)

        whatsApp.setAutoSend(AutoSend(reminders = true, reminderMinuteOfDay = 11 * 60))
        now = Instant.parse("2026-11-05T11:01:00Z")
        assertEquals(1, whatsApp.sendDueReminders())
    }

    @Test
    fun onlyMakeWebhookAddressesAreSaved() = runTest {
        val whatsApp = whatsApp()
        waSettings.setWebhookUrl(null)

        assertFalse(whatsApp.setWebhookUrl("http://hook.eu2.make.com/abc"), "not https")
        assertFalse(whatsApp.setWebhookUrl("https://example.com/hook"))
        assertTrue(whatsApp.setWebhookUrl("  https://hook.us1.make.com/n4m8xq2k  "))
        assertEquals("https://hook.us1.make.com/n4m8xq2k", waSettings.state.value.webhookUrl)
    }

    @Test
    fun testMessageGoesStraightToTheWebhook() = runTest {
        val result = whatsApp().sendTest("919000011111", "Iron House")

        assertEquals(WebhookResult.Accepted, result)
        val (url, message) = webhook.posted.single()
        assertEquals(hook, url)
        assertEquals("gym_welcome", message.templateName)
        assertEquals(listOf("Test", "Iron House"), message.parameters)
        assertTrue(outbox.pending().isEmpty(), "a test isn't queued")
    }

    @Test
    fun demoModeNeverSends() = runTest {
        val whatsApp = whatsApp(webhook = null)
        waSettings.setAutoSend(AutoSend(welcome = true))

        assertFalse(whatsApp.isAvailable)
        assertFalse(whatsApp.sendAutomatically(welcome()))
        assertTrue(outbox.pending().isEmpty())
    }
}
