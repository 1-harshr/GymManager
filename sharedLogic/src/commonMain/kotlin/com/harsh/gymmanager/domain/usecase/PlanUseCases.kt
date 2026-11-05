package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow

class ObservePlansUseCase(private val repository: PlanRepository) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<Plan>> =
        if (includeArchived) repository.observeAllPlans() else repository.observeActivePlans()
}

class GetPlanUseCase(private val repository: PlanRepository) {
    suspend operator fun invoke(id: Long): Plan? = repository.getPlan(id)
}

class AddPlanUseCase(private val repository: PlanRepository) {
    suspend operator fun invoke(
        name: String,
        durationValue: Int,
        durationUnit: DurationUnit,
        defaultPrice: Long,
    ): Result<Long> = resultOf {
        repository.addPlan(
            Plan(name = name, durationValue = durationValue, durationUnit = durationUnit, defaultPrice = defaultPrice)
        )
    }
}

/** Only affects future memberships; ones already sold keep their own name, dates and price. */
class UpdatePlanUseCase(private val repository: PlanRepository) {
    suspend operator fun invoke(plan: Plan): Result<Unit> = resultOf { repository.updatePlan(plan) }
}

class ArchivePlanUseCase(private val repository: PlanRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.archivePlan(id) }
}

class RestorePlanUseCase(private val repository: PlanRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.restorePlan(id) }
}

class PlanUseCases(repository: PlanRepository) {
    val observePlans = ObservePlansUseCase(repository)
    val getPlan = GetPlanUseCase(repository)
    val addPlan = AddPlanUseCase(repository)
    val updatePlan = UpdatePlanUseCase(repository)
    val archivePlan = ArchivePlanUseCase(repository)
    val restorePlan = RestorePlanUseCase(repository)
}
