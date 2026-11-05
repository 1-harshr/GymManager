package com.harsh.gymmanager

import android.content.Context
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import com.harsh.gymmanager.domain.repository.PaymentRepository
import com.harsh.gymmanager.domain.repository.PlanRepository

/** App-wide singletons, created lazily on first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: GymDatabase by lazy { buildGymDatabase(gymDatabaseBuilder(appContext)) }

    val memberRepository: MemberRepository by lazy { MemberRepositoryImpl(database.memberDao()) }
    val planRepository: PlanRepository by lazy { PlanRepositoryImpl(database.planDao()) }
    val membershipRepository: MembershipRepository by lazy { MembershipRepositoryImpl(database.membershipDao()) }
    val paymentRepository: PaymentRepository by lazy { PaymentRepositoryImpl(database.paymentDao()) }
}
