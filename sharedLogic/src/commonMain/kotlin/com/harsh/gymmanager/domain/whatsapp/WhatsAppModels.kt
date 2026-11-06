package com.harsh.gymmanager.domain.whatsapp

import com.harsh.gymmanager.domain.messaging.MessageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Which messages go out on their own once WhatsApp is set up. All off until the owner turns them on. */
data class AutoSend(
    val welcome: Boolean = false,
    val receipts: Boolean = false,
    val reminders: Boolean = false,
    /** Local time for the daily reminders, e.g. 540 for 9:00 AM. */
    val reminderMinuteOfDay: Int = DEFAULT_REMINDER_MINUTE,
) {
    init {
        require(reminderMinuteOfDay in 0 until 24 * 60) { "reminderMinuteOfDay out of range: $reminderMinuteOfDay" }
    }

    fun isOn(kind: MessageKind): Boolean = when (kind) {
        MessageKind.WELCOME -> welcome
        MessageKind.RECEIPT -> receipts
        MessageKind.REMINDER -> reminders
    }

    companion object {
        const val DEFAULT_REMINDER_MINUTE = 9 * 60
    }
}

data class WhatsAppSettings(
    /**
     * The Make.com scenario's webhook, which sends each message with the gym's WhatsApp Business
     * account. Null until the owner sets it up; anyone with it can send as the gym, so it's kept private.
     */
    val webhookUrl: String? = null,
    val autoSend: AutoSend = AutoSend(),
    /** The day the daily reminders last went out, so they run once a day. */
    val lastReminderRun: LocalDate? = null,
) {
    val isSetUp: Boolean get() = webhookUrl != null
}

interface WhatsAppSettingsRepository {
    fun observe(): Flow<WhatsAppSettings>

    suspend fun setWebhookUrl(url: String?)

    suspend fun setAutoSend(autoSend: AutoSend)

    suspend fun setLastReminderRun(day: LocalDate)
}

/** Where a queued message has got to. */
enum class OutboxStatus {
    /** Waiting for the internet, or for a retry. */
    PENDING,

    /** Make accepted it. Make doesn't report whether WhatsApp then delivered it. */
    SENT,

    /** Refused, or still failing after many tries; not retried. */
    FAILED,
}

/** One WhatsApp message waiting to go to Make, or already handed over. */
data class OutboxMessage(
    val id: Long = 0,
    /** Sent along so the Make scenario can spot a repeat. */
    val clientId: String,
    val memberId: String,
    val memberName: String,
    val kind: MessageKind,
    val recipient: String,
    val templateName: String,
    val languageCode: String,
    val parameters: List<String>,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    /** Why the last attempt failed, as the webhook or the network said it. */
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

interface OutboxRepository {
    suspend fun add(message: OutboxMessage)

    /** Messages not yet handed to Make, oldest first. */
    suspend fun pending(): List<OutboxMessage>

    suspend fun update(message: OutboxMessage)

    fun observeRecent(limit: Int): Flow<List<OutboxMessage>>
}

/** What the webhook said about one message. */
sealed interface WebhookResult {
    data object Accepted : WebhookResult

    /** Worth trying again later: no internet, a timeout, or Make busy or down. */
    data class TryLater(val reason: String) : WebhookResult

    /** Make (or WhatsApp behind it) refused the message; retrying won't help. */
    data class Refused(val reason: String) : WebhookResult
}

/** Posts a message to the owner's Make.com webhook. */
interface MessageWebhook {
    suspend fun post(url: String, message: OutboxMessage): WebhookResult
}

/** Tells the platform to send queued messages and when to run the daily reminders. */
interface WhatsAppScheduler {
    /** Something was queued; send it as soon as there's a connection. */
    fun outboxChanged()

    /** Called when WhatsApp settings change, to (un)schedule the daily reminders. */
    suspend fun sync(settings: WhatsAppSettings)
}

object NoWhatsAppScheduler : WhatsAppScheduler {
    override fun outboxChanged() = Unit

    override suspend fun sync(settings: WhatsAppSettings) = Unit
}

/**
 * Sends queued messages straight away from inside the app. Used where the platform can't run
 * work in the background at a set time (iOS); daily reminders then go out when the app opens.
 */
class InAppWhatsAppScheduler(
    private val scope: CoroutineScope,
    private val flush: suspend () -> Unit,
) : WhatsAppScheduler {
    override fun outboxChanged() {
        scope.launch { flush() }
    }

    override suspend fun sync(settings: WhatsAppSettings) = Unit
}
