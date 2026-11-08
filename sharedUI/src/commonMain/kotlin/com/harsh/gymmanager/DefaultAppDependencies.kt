package com.harsh.gymmanager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.repository.AttendanceRepositoryImpl
import com.harsh.gymmanager.data.repository.InboxRepositoryImpl
import com.harsh.gymmanager.data.repository.UsageRepositoryImpl
import com.harsh.gymmanager.domain.suggestions.SuggestionsUseCase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.MessageLogRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.data.settings.DataStoreSettingsRepository
import com.harsh.gymmanager.domain.repository.AttendanceRepository
import com.harsh.gymmanager.domain.repository.SettingsRepository
import com.harsh.gymmanager.data.remote.RemoteGymData
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import com.harsh.gymmanager.domain.repository.PaymentRepository
import com.harsh.gymmanager.domain.repository.PlanRepository
import com.harsh.gymmanager.domain.assistant.GymAssistant
import com.harsh.gymmanager.domain.digest.MorningDigestUseCase
import com.harsh.gymmanager.domain.insights.NoOnDeviceModel
import com.harsh.gymmanager.domain.insights.ObserveInsightsUseCase
import com.harsh.gymmanager.domain.insights.OnDeviceModel
import com.harsh.gymmanager.domain.insights.WriteBriefUseCase
import com.harsh.gymmanager.domain.usecase.AttendanceUseCases
import com.harsh.gymmanager.domain.usecase.InboxUseCases
import com.harsh.gymmanager.domain.messaging.MessagingUseCases
import com.harsh.gymmanager.domain.usecase.BulkRenewUseCase
import com.harsh.gymmanager.domain.usecase.ImportMembersUseCase
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.ObserveMemberDetailUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.domain.usecase.SettingsUseCases
import com.harsh.gymmanager.ui.notifications.MorningUpdateScheduler
import com.harsh.gymmanager.data.repository.OutboxRepositoryImpl
import com.harsh.gymmanager.data.whatsapp.DataStoreWhatsAppSettings
import com.harsh.gymmanager.data.whatsapp.KtorMessageWebhook
import com.harsh.gymmanager.domain.whatsapp.NoWhatsAppScheduler
import com.harsh.gymmanager.domain.whatsapp.WhatsAppMessaging
import com.harsh.gymmanager.domain.whatsapp.WhatsAppScheduler
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * App-wide singletons for both platforms, created lazily on first use. Each platform supplies
 * how to open its database and settings file, how it schedules the morning update and WhatsApp
 * sending, and its on-device language model, if any. [sendsWhatsApp] false turns WhatsApp
 * sending off (demo mode).
 */
class DefaultAppDependencies(
    openDatabase: () -> GymDatabase,
    openSettings: () -> DataStore<Preferences>,
    private val createMorningUpdates: (MorningDigestUseCase) -> MorningUpdateScheduler,
    private val onDeviceModel: OnDeviceModel = NoOnDeviceModel,
    private val sendsWhatsApp: Boolean = true,
    private val createWhatsAppScheduler: (messaging: () -> WhatsAppMessaging) -> WhatsAppScheduler = { NoWhatsAppScheduler },
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /** Lets the real data take some settings from elsewhere, such as the gym's details from the gym online. */
    private val wrapSettings: (SettingsRepository) -> SettingsRepository = { it },
    /** The gym's records online; null keeps them in [openDatabase], as demo mode does. */
    private val remote: RemoteGymData? = null,
) : AppDependencies {
    private var databaseOpened = false
    private val database by lazy { openDatabase().also { databaseOpened = true } }

    private val memberRepository: MemberRepository by lazy { remote?.members ?: MemberRepositoryImpl(database.memberDao(), clock, timeZone) }
    private val planRepository: PlanRepository by lazy { remote?.plans ?: PlanRepositoryImpl(database.planDao(), clock) }
    private val membershipRepository: MembershipRepository by lazy { remote?.memberships ?: MembershipRepositoryImpl(database.membershipDao(), clock) }
    private val paymentRepository: PaymentRepository by lazy { remote?.payments ?: PaymentRepositoryImpl(database.paymentDao(), clock) }
    private val attendanceRepository: AttendanceRepository by lazy { remote?.attendance ?: AttendanceRepositoryImpl(database.checkInDao(), clock, timeZone) }
    private val messageLogRepository by lazy { MessageLogRepositoryImpl(database.messageLogDao(), clock) }
    // DataStore allows one instance per file.
    private val settingsStore by lazy { openSettings() }
    private val settingsRepository by lazy { wrapSettings(DataStoreSettingsRepository(settingsStore)) }

    override val memberUseCases by lazy { MemberUseCases(memberRepository, clock, timeZone) }
    override val planUseCases by lazy { PlanUseCases(planRepository) }
    override val membershipUseCases by lazy { MembershipUseCases(membershipRepository, clock, timeZone) }
    override val paymentUseCases by lazy { PaymentUseCases(paymentRepository, clock, timeZone) }
    override val attendance by lazy {
        AttendanceUseCases(attendanceRepository, memberRepository, settingsRepository, messageLogRepository, clock, timeZone)
    }
    override val observeMemberDetail by lazy {
        ObserveMemberDetailUseCase(memberRepository, membershipRepository, clock, timeZone)
    }
    override val bulkRenew by lazy { BulkRenewUseCase(memberRepository, membershipRepository, clock, timeZone) }
    override val importMembers by lazy { ImportMembersUseCase(memberRepository, clock, timeZone) }
    override val settingsUseCases by lazy { SettingsUseCases(settingsRepository) }
    override val observeDashboard by lazy { ObserveDashboardUseCase(memberRepository, paymentRepository, clock, timeZone) }
    override val messaging by lazy {
        MessagingUseCases(memberRepository, membershipRepository, settingsRepository, messageLogRepository, clock, timeZone)
    }
    override val assistant by lazy {
        GymAssistant(memberUseCases, planUseCases, membershipUseCases, paymentUseCases, observeDashboard)
    }
    override val morningDigest by lazy { MorningDigestUseCase(memberRepository, paymentRepository) }
    override val morningUpdates by lazy { createMorningUpdates(morningDigest) }
    override val insights by lazy {
        ObserveInsightsUseCase(memberRepository, membershipRepository, paymentRepository, clock, timeZone)
    }
    override val brief by lazy { WriteBriefUseCase(onDeviceModel) }
    override val inbox by lazy {
        InboxUseCases(InboxRepositoryImpl(database.inboxDao()), memberRepository, membershipRepository, clock, timeZone)
    }
    override val suggestions by lazy { SuggestionsUseCase(remote?.usage ?: UsageRepositoryImpl(database.usageDao())) }
    override val whatsAppScheduler: WhatsAppScheduler by lazy { createWhatsAppScheduler { whatsApp } }
    override val whatsApp: WhatsAppMessaging by lazy {
        WhatsAppMessaging(
            webhook = if (sendsWhatsApp) KtorMessageWebhook(HttpClient { install(HttpTimeout) { requestTimeoutMillis = 30_000 } }) else null,
            settings = DataStoreWhatsAppSettings(settingsStore),
            outbox = OutboxRepositoryImpl(database.outboxDao()),
            messaging = messaging,
            messageLog = messageLogRepository,
            scheduler = { whatsAppScheduler },
            clock = clock,
            timeZone = timeZone,
        )
    }

    /** Closes the database, if it was opened; these dependencies can't be used after. */
    fun close() {
        if (databaseOpened) database.close()
    }
}
