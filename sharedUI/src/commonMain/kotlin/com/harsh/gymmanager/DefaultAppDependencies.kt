package com.harsh.gymmanager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.repository.BackupRepositoryImpl
import com.harsh.gymmanager.data.repository.InboxRepositoryImpl
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.MessageLogRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.data.settings.DataStoreSettingsRepository
import com.harsh.gymmanager.domain.assistant.GymAssistant
import com.harsh.gymmanager.domain.backup.BackupUseCases
import com.harsh.gymmanager.domain.backup.StoredFiles
import com.harsh.gymmanager.domain.digest.MorningDigestUseCase
import com.harsh.gymmanager.domain.insights.NoOnDeviceModel
import com.harsh.gymmanager.domain.insights.ObserveInsightsUseCase
import com.harsh.gymmanager.domain.insights.OnDeviceModel
import com.harsh.gymmanager.domain.insights.WriteBriefUseCase
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
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * App-wide singletons for both platforms, created lazily on first use. Each platform supplies
 * how to open its database and settings file, how it schedules the morning update, and its
 * on-device language model, if any.
 */
class DefaultAppDependencies(
    openDatabase: () -> GymDatabase,
    openSettings: () -> DataStore<Preferences>,
    private val storedFiles: StoredFiles,
    private val createMorningUpdates: (MorningDigestUseCase) -> MorningUpdateScheduler,
    private val onDeviceModel: OnDeviceModel = NoOnDeviceModel,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : AppDependencies {
    private val database by lazy(openDatabase)

    private val memberRepository by lazy { MemberRepositoryImpl(database.memberDao(), clock, timeZone) }
    private val planRepository by lazy { PlanRepositoryImpl(database.planDao(), clock) }
    private val membershipRepository by lazy { MembershipRepositoryImpl(database.membershipDao(), clock) }
    private val paymentRepository by lazy { PaymentRepositoryImpl(database.paymentDao(), clock) }
    private val messageLogRepository by lazy { MessageLogRepositoryImpl(database.messageLogDao(), clock) }
    // DataStore allows one instance per file.
    private val settingsRepository by lazy { DataStoreSettingsRepository(openSettings()) }

    override val memberUseCases by lazy { MemberUseCases(memberRepository, clock, timeZone) }
    override val planUseCases by lazy { PlanUseCases(planRepository) }
    override val membershipUseCases by lazy { MembershipUseCases(membershipRepository, clock, timeZone) }
    override val paymentUseCases by lazy { PaymentUseCases(paymentRepository, clock, timeZone) }
    override val observeMemberDetail by lazy {
        ObserveMemberDetailUseCase(memberRepository, membershipRepository, clock, timeZone)
    }
    override val bulkRenew by lazy { BulkRenewUseCase(memberRepository, membershipRepository, clock, timeZone) }
    override val importMembers by lazy { ImportMembersUseCase(memberRepository, clock, timeZone) }
    override val settingsUseCases by lazy { SettingsUseCases(settingsRepository) }
    override val observeDashboard by lazy { ObserveDashboardUseCase(memberRepository, paymentRepository, clock, timeZone) }
    override val backup by lazy {
        BackupUseCases(BackupRepositoryImpl(database.backupDao()), settingsRepository, storedFiles, clock, timeZone)
    }
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
}
