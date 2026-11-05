package com.harsh.gymmanager

import androidx.compose.runtime.staticCompositionLocalOf
import com.harsh.gymmanager.domain.backup.BackupUseCases
import com.harsh.gymmanager.domain.digest.MorningDigestUseCase
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

/** What the screens need from the app; the Android app's AppContainer provides it. */
interface AppDependencies {
    val memberUseCases: MemberUseCases
    val planUseCases: PlanUseCases
    val membershipUseCases: MembershipUseCases
    val paymentUseCases: PaymentUseCases
    val observeDashboard: ObserveDashboardUseCase
    val observeMemberDetail: ObserveMemberDetailUseCase
    val bulkRenew: BulkRenewUseCase
    val importMembers: ImportMembersUseCase
    val settingsUseCases: SettingsUseCases
    val messaging: MessagingUseCases
    val backup: BackupUseCases
    val morningDigest: MorningDigestUseCase
    val morningUpdates: MorningUpdateScheduler
}

val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> { error("AppDependencies not provided") }
