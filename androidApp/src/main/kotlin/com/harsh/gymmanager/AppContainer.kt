package com.harsh.gymmanager

import android.content.Context
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.data.settings.DataStoreSettingsRepository
import com.harsh.gymmanager.data.settings.SETTINGS_FILE_NAME
import com.harsh.gymmanager.data.settings.createSettingsDataStore
import com.harsh.gymmanager.domain.usecase.BulkRenewUseCase
import com.harsh.gymmanager.domain.usecase.ImportMembersUseCase
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.ObserveMemberDetailUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.domain.usecase.SettingsUseCases
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** App-wide singletons, created lazily on first use. ViewModels depend on the use cases only. */
class AppContainer(context: Context) : AppDependencies {
    private val appContext = context.applicationContext
    private val clock: Clock = Clock.System
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()

    private val database: GymDatabase by lazy { buildGymDatabase(gymDatabaseBuilder(appContext)) }

    private val memberRepository by lazy { MemberRepositoryImpl(database.memberDao(), clock, timeZone) }
    private val planRepository by lazy { PlanRepositoryImpl(database.planDao(), clock) }
    private val membershipRepository by lazy { MembershipRepositoryImpl(database.membershipDao(), clock) }
    private val paymentRepository by lazy { PaymentRepositoryImpl(database.paymentDao(), clock) }

    override val memberUseCases by lazy { MemberUseCases(memberRepository, clock, timeZone) }
    override val planUseCases by lazy { PlanUseCases(planRepository) }
    override val membershipUseCases by lazy { MembershipUseCases(membershipRepository, clock, timeZone) }
    override val paymentUseCases by lazy { PaymentUseCases(paymentRepository, clock, timeZone) }
    override val observeMemberDetail by lazy {
        ObserveMemberDetailUseCase(memberRepository, membershipRepository, clock, timeZone)
    }
    override val settingsUseCases by lazy {
        val file = appContext.filesDir.resolve(SETTINGS_FILE_NAME)
        SettingsUseCases(DataStoreSettingsRepository(createSettingsDataStore(file.absolutePath)))
    }
    override val bulkRenew by lazy { BulkRenewUseCase(memberRepository, membershipRepository, clock, timeZone) }
    override val importMembers by lazy { ImportMembersUseCase(memberRepository, clock, timeZone) }
    override val observeDashboard by lazy { ObserveDashboardUseCase(memberRepository, paymentRepository, clock, timeZone) }
}
