package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.repository.PlanRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The gym's plans online. Only the owner can change them; the database refuses anyone else. */
internal class SupabasePlanRepository(
    private val supabase: SupabaseClient,
    private val refresher: Refresher,
    private val gym: GymGate,
) : PlanRepository {
    private val plans get() = supabase.postgrest.from("plans")

    override fun observeActivePlans(): Flow<List<Plan>> =
        refresher.observe { fetch().filterNot { it.isArchived } }

    override fun observeAllPlans(): Flow<List<Plan>> = refresher.observe { fetch() }

    /** Active plans first, then by name ignoring case, as the phone's own list orders them. */
    private suspend fun fetch(): List<Plan> =
        plans.select().decodeList<PlanRow>()
            .map { it.toDomain() }
            .sortedWith(compareBy<Plan> { it.isArchived }.thenBy { it.name.lowercase() })

    override suspend fun getPlan(id: String): Plan? = remote {
        plans.select { filter { eq("id", id) } }.decodeSingleOrNull<PlanRow>()?.toDomain()
    }

    override suspend fun addPlan(plan: Plan): String = write {
        plans.insert(
            buildJsonObject {
                put("gym_id", gym.gymId())
                put("name", plan.name.trim())
                put("duration_value", plan.durationValue)
                put("duration_unit", plan.durationUnit.name)
                put("default_price", plan.defaultPrice)
                put("is_archived", plan.isArchived)
            },
        ) { select(Columns.raw("id")) }.decodeSingle<IdRow>().id
    }

    override suspend fun updatePlan(plan: Plan) = write {
        plans.update(
            buildJsonObject {
                put("name", plan.name.trim())
                put("duration_value", plan.durationValue)
                put("duration_unit", plan.durationUnit.name)
                put("default_price", plan.defaultPrice)
                put("is_archived", plan.isArchived)
            },
        ) { filter { eq("id", plan.id) } }
        Unit
    }

    override suspend fun archivePlan(id: String) {
        setArchived(listOf(id), true)
    }

    override suspend fun restorePlan(id: String) {
        setArchived(listOf(id), false)
    }

    override suspend fun archivePlans(ids: List<String>): Int = setArchived(ids, true)

    override suspend fun restorePlans(ids: List<String>): Int = setArchived(ids, false)

    private suspend fun setArchived(ids: List<String>, archived: Boolean): Int = write {
        plans.update(buildJsonObject { put("is_archived", archived) }) {
            filter { isIn("id", ids) }
            select(Columns.raw("id"))
        }.decodeList<IdRow>().size
    }

    private suspend fun <T> write(block: suspend () -> T): T = remote(block).also { refresher.changed() }
}
