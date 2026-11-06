package com.harsh.gymmanager.domain.whatsapp

import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.messaging.MessagingUseCases
import com.harsh.gymmanager.domain.messaging.gymTemplate
import com.harsh.gymmanager.domain.repository.MessageLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** How a [WhatsAppMessaging.flush] ended. */
enum class FlushResult {
    /** Nothing left to send. */
    DONE,

    /** Some messages are waiting for the internet or for Make; try again later. */
    RETRY_LATER,
}

/**
 * Sends members' messages with no tap, through the owner's Make.com scenario, which sends them
 * from the gym's WhatsApp Business number: automatically after a new member or a payment, every
 * day for reminders, or when the owner sends one by hand.
 *
 * Messages go into an outbox first and are posted from there, so nothing is lost offline.
 * [webhook] is null in demo mode; then nothing is ever sent.
 */
class WhatsAppMessaging(
    private val webhook: MessageWebhook?,
    private val settings: WhatsAppSettingsRepository,
    private val outbox: OutboxRepository,
    private val messaging: MessagingUseCases,
    private val messageLog: MessageLogRepository,
    private val scheduler: () -> WhatsAppScheduler,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val newClientId: () -> String = ::randomClientId,
) {
    /** False in demo mode, where sample data must never message real people. */
    val isAvailable: Boolean get() = webhook != null

    private val flushing = Mutex()

    fun observeSettings(): Flow<WhatsAppSettings> = if (webhook == null) flowOf(WhatsAppSettings()) else settings.observe()

    fun observeOutbox(limit: Int = RECENT_LIMIT): Flow<List<OutboxMessage>> = if (webhook == null) flowOf(emptyList()) else outbox.observeRecent(limit)

    /** Saves the Make webhook. Only an https address from Make is accepted. */
    suspend fun setWebhookUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!isMakeWebhook(trimmed)) return false
        settings.setWebhookUrl(trimmed)
        return true
    }

    /** Stops sending through Make; anything still queued waits until a webhook is set again. */
    suspend fun removeWebhook() = settings.setWebhookUrl(null)

    /**
     * Saves the switches. Turning reminders on (or moving their time) after today's time has
     * passed starts them tomorrow, rather than sending a batch the moment the app next opens.
     */
    suspend fun setAutoSend(autoSend: AutoSend) {
        val before = settings.observe().first().autoSend
        settings.setAutoSend(autoSend)
        val changed = autoSend.reminders && (!before.reminders || before.reminderMinuteOfDay != autoSend.reminderMinuteOfDay)
        val now = clock.now().toLocalDateTime(timeZone)
        if (changed && now.hour * 60 + now.minute >= autoSend.reminderMinuteOfDay) settings.setLastReminderRun(now.date)
    }

    /** Whether messages can go through Make right now. */
    suspend fun canSend(): Boolean = webhook != null && settings.observe().first().isSetUp

    /**
     * Queues [message] if its kind is set to go out automatically. Returns false when it isn't, so
     * the caller offers it by hand instead.
     */
    suspend fun sendAutomatically(message: MemberMessage): Boolean {
        if (!settings.observe().first().autoSend.isOn(message.kind) || !canSend()) return false
        queue(message)
        messageLog.record(message.memberId, message.kind)
        return true
    }

    /**
     * Queues a message the owner chose to send. Returns false when Make isn't set up, so the
     * caller can open WhatsApp instead. The caller records it in the message log, as for links.
     */
    suspend fun sendNow(message: MemberMessage): Boolean {
        if (!canSend()) return false
        queue(message)
        return true
    }

    /**
     * Posts a welcome addressed to [number] (with the country code, e.g. 919876543210) straight
     * away, so the owner can check the Make scenario works. It doesn't go through the outbox.
     */
    suspend fun sendTest(number: String, gymName: String): WebhookResult {
        val webhook = webhook ?: return WebhookResult.Refused("Not available")
        val url = settings.observe().first().webhookUrl ?: return WebhookResult.Refused("No webhook")
        val template = gymTemplate(MemberMessage.Welcome("", "Test", number, gymName))
        val now = clock.now()
        val test = OutboxMessage(
            clientId = newClientId(),
            memberId = "",
            memberName = "Test",
            kind = MessageKind.WELCOME,
            recipient = number,
            templateName = template.name,
            languageCode = template.languageCode,
            parameters = template.bodyParameters,
            createdAt = now,
            updatedAt = now,
        )
        return webhook.post(url, test)
    }

    private suspend fun queue(message: MemberMessage) {
        val template = gymTemplate(message)
        val now = clock.now()
        outbox.add(
            OutboxMessage(
                clientId = newClientId(),
                memberId = message.memberId,
                memberName = message.memberName,
                kind = message.kind,
                recipient = message.whatsAppNumber,
                templateName = template.name,
                languageCode = template.languageCode,
                parameters = template.bodyParameters,
                createdAt = now,
                updatedAt = now,
            )
        )
        scheduler().outboxChanged()
    }

    /**
     * Posts everything queued to Make. Stops at the first network or Make trouble and leaves the
     * rest for later; a message Make refuses is marked failed and not retried.
     */
    suspend fun flush(): FlushResult = flushing.withLock {
        val webhook = webhook ?: return FlushResult.DONE
        val url = settings.observe().first().webhookUrl ?: return FlushResult.RETRY_LATER
        for (message in outbox.pending()) {
            val now = clock.now()
            val attempts = message.attempts + 1
            when (val result = webhook.post(url, message)) {
                WebhookResult.Accepted -> outbox.update(message.copy(status = OutboxStatus.SENT, attempts = attempts, errorMessage = null, updatedAt = now))
                is WebhookResult.Refused -> outbox.update(message.copy(status = OutboxStatus.FAILED, attempts = attempts, errorMessage = result.reason, updatedAt = now))
                is WebhookResult.TryLater -> {
                    val giveUp = attempts >= MAX_ATTEMPTS
                    outbox.update(
                        message.copy(
                            status = if (giveUp) OutboxStatus.FAILED else OutboxStatus.PENDING,
                            attempts = attempts,
                            errorMessage = result.reason,
                            updatedAt = now,
                        )
                    )
                    if (!giveUp) return FlushResult.RETRY_LATER
                }
            }
        }
        FlushResult.DONE
    }

    /** True once today's reminder time has passed and today's reminders haven't gone out. */
    suspend fun remindersDue(): Boolean {
        val current = settings.observe().first()
        if (!current.autoSend.reminders || !current.isSetUp) return false
        val now = clock.now().toLocalDateTime(timeZone)
        val minuteNow = now.hour * 60 + now.minute
        return current.lastReminderRun != now.date && minuteNow >= current.autoSend.reminderMinuteOfDay
    }

    /**
     * Queues today's reminders if they're due: members ending this week, recently ended, or owing
     * money, except those reminded in the last few days. Returns how many were queued.
     */
    suspend fun sendDueReminders(): Int {
        if (webhook == null || !remindersDue()) return 0
        // Mark the day first, so a second caller (worker and app at once) doesn't send twice.
        settings.setLastReminderRun(clock.now().toLocalDateTime(timeZone).date)
        val due = messaging.observeReminders().first().filter { !it.sentRecently }.mapNotNull { it.message }
        for (message in due) {
            queue(message)
            messageLog.record(message.memberId, MessageKind.REMINDER)
        }
        return due.size
    }

    /** Everything that should happen when the app opens or a background run fires. */
    suspend fun catchUp(): FlushResult {
        if (webhook == null) return FlushResult.DONE
        sendDueReminders()
        return flush()
    }

    companion object {
        const val RECENT_LIMIT = 30

        /** A message still failing after this many tries (e.g. Make keeps erroring) is given up. */
        const val MAX_ATTEMPTS = 10

        /** Make's webhook addresses look like https://hook.eu2.make.com/abc123…. */
        fun isMakeWebhook(url: String): Boolean = Regex("""https://hook\.[a-z0-9-]+\.make\.com/[A-Za-z0-9]+""").matches(url)
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomClientId(): String = Uuid.random().toString()
