package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.PlanDao
import com.harsh.gymmanager.data.mapper.toDomain
import com.harsh.gymmanager.data.mapper.toEntity
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class PlanRepositoryImpl(
    private val dao: PlanDao,
    private val clock: Clock = Clock.System,
) : PlanRepository {

    override fun observeActivePlans(): Flow<List<Plan>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeAllPlans(): Flow<List<Plan>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPlan(id: String): Plan? = dao.getById(id.toLong())?.toDomain()

    override suspend fun addPlan(plan: Plan): String = dao.insert(plan.copy(id = "").toEntity(createdAt = clock.now())).toString()

    override suspend fun updatePlan(plan: Plan) {
        val existing = requireNotNull(dao.getById(plan.id.toLong())) { "Plan ${plan.id} does not exist" }
        dao.update(plan.toEntity(createdAt = existing.createdAt))
    }

    override suspend fun archivePlan(id: String) = dao.setArchived(id.toLong(), archived = true)

    override suspend fun restorePlan(id: String) = dao.setArchived(id.toLong(), archived = false)

    override suspend fun archivePlans(ids: List<String>): Int = dao.setArchived(ids.map(String::toLong), archived = true)

    override suspend fun restorePlans(ids: List<String>): Int = dao.setArchived(ids.map(String::toLong), archived = false)
}
