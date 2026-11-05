package com.harsh.gymmanager

import android.content.Context
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** App-wide singletons, created lazily on first use. ViewModels depend on the use cases only. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: Clock = Clock.System
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()

    private val database: GymDatabase by lazy { buildGymDatabase(gymDatabaseBuilder(appContext)) }

    private val memberRepository by lazy { MemberRepositoryImpl(database.memberDao(), clock, timeZone) }
    private val planRepository by lazy { PlanRepositoryImpl(database.planDao(), clock) }
    private val membershipRepository by lazy { MembershipRepositoryImpl(database.membershipDao(), clock) }
    private val paymentRepository by lazy { PaymentRepositoryImpl(database.paymentDao(), clock) }

    val memberUseCases by lazy { MemberUseCases(memberRepository, clock, timeZone) }
    val planUseCases by lazy { PlanUseCases(planRepository) }
    val membershipUseCases by lazy { MembershipUseCases(membershipRepository, clock, timeZone) }
    val paymentUseCases by lazy { PaymentUseCases(paymentRepository, clock, timeZone) }
    val observeDashboard by lazy { ObserveDashboardUseCase(memberRepository, paymentRepository, clock, timeZone) }
}
