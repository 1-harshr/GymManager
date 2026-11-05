package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.Plan
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observeActivePlans(): Flow<List<Plan>>

    /** Active plans first, then archived ones. */
    fun observeAllPlans(): Flow<List<Plan>>

    suspend fun getPlan(id: Long): Plan?

    suspend fun addPlan(plan: Plan): Long

    suspend fun updatePlan(plan: Plan)

    suspend fun archivePlan(id: Long)

    suspend fun restorePlan(id: Long)
}
