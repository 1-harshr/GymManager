package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.repository.MembershipRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The gym's memberships online, each with its payments. New ones go through add_memberships, so a
 * membership and its first payment are saved together and the overpayment check guards the payment.
 */
internal class SupabaseMembershipRepository(
    private val supabase: SupabaseClient,
    private val refresher: Refresher,
) : MembershipRepository {
    private val memberships get() = supabase.postgrest.from("memberships")

    /** Newest first, as the phone's own list orders them. */
    override fun observeMemberships(memberId: String): Flow<List<Membership>> = refresher.observe {
        memberships.select(Columns.raw("*, payments(*)")) {
            filter { eq("member_id", memberId) }
            order("start_date", Order.DESCENDING)
        }.decodeList<MembershipRow>().map { it.toDomain() }
    }

    /** Every membership of members who aren't archived. */
    override fun observeAllMemberships(): Flow<List<Membership>> = refresher.observe {
        memberships.select(Columns.raw("*, payments(*), members!inner(is_archived)")) {
            filter { eq("members.is_archived", false) }
        }.decodeList<MembershipRow>().map { it.toDomain() }
    }

    override suspend fun getLatestEndDate(memberId: String): LocalDate? = remote {
        memberships.select(Columns.raw("end_date")) {
            filter { eq("member_id", memberId) }
            order("end_date", Order.DESCENDING)
            limit(1)
        }.decodeList<EndDateRow>().firstOrNull()?.endDate
    }

    override suspend fun addMembership(membership: Membership, firstPayment: Payment?): String =
        addMemberships(listOf(membership to firstPayment)).single()

    override suspend fun addMemberships(items: List<Pair<Membership, Payment?>>): List<String> {
        if (items.isEmpty()) return emptyList()
        return write {
            supabase.postgrest.rpc(
                "add_memberships",
                buildJsonObject {
                    put("items", buildJsonArray { items.forEach { (membership, payment) -> add(membership.toItem(payment)) } })
                },
            ).decodeAs<List<String>>()
        }
    }

    override suspend fun updateMembership(membership: Membership) = write {
        memberships.update(
            buildJsonObject {
                put("plan_id", membership.planId)
                put("plan_name", membership.planName.trim())
                put("start_date", membership.startDate.toString())
                put("end_date", membership.endDate.toString())
                put("price", membership.price)
                put("discount", membership.discount)
                put("notes", membership.notes?.trim()?.ifEmpty { null })
            },
        ) { filter { eq("id", membership.id) } }
        Unit
    }

    /** Its payments go with it. */
    override suspend fun deleteMembership(id: String) = write {
        memberships.delete { filter { eq("id", id) } }
        Unit
    }

    private suspend fun <T> write(block: suspend () -> T): T = remote(block).also { refresher.changed() }

    private fun Membership.toItem(payment: Payment?): JsonObject = buildJsonObject {
        put("member_id", memberId)
        put("plan_id", planId)
        put("plan_name", planName.trim())
        put("start_date", startDate.toString())
        put("end_date", endDate.toString())
        put("price", price)
        put("discount", discount)
        put("notes", notes)
        if (payment != null) {
            put(
                "payment",
                buildJsonObject {
                    put("amount", payment.amount)
                    put("mode", payment.mode.name)
                    put("paid_on", payment.paidOn.toString())
                },
            )
        }
    }

    @Serializable
    private data class EndDateRow(@SerialName("end_date") val endDate: LocalDate)
}
