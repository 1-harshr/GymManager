package com.harsh.gymmanager

import androidx.compose.runtime.staticCompositionLocalOf
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.ObserveMemberDetailUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases

/** What the screens need from the app; the Android app's AppContainer provides it. */
interface AppDependencies {
    val memberUseCases: MemberUseCases
    val planUseCases: PlanUseCases
    val membershipUseCases: MembershipUseCases
    val paymentUseCases: PaymentUseCases
    val observeDashboard: ObserveDashboardUseCase
    val observeMemberDetail: ObserveMemberDetailUseCase
}

val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> { error("AppDependencies not provided") }
