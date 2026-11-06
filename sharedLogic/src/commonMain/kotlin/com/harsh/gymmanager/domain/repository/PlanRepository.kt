package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.Plan
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observeActivePlans(): Flow<List<Plan>>

    /** Active plans first, then archived ones. */
    fun observeAllPlans(): Flow<List<Plan>>

    suspend fun getPlan(id: String): Plan?

    suspend fun addPlan(plan: Plan): String

    suspend fun updatePlan(plan: Plan)

    suspend fun archivePlan(id: String)

    suspend fun restorePlan(id: String)

    /** Returns how many plans changed. */
    suspend fun archivePlans(ids: List<String>): Int

    suspend fun restorePlans(ids: List<String>): Int
}
