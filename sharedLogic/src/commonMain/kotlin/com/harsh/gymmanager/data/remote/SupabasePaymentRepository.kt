package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentRecord
import com.harsh.gymmanager.domain.repository.PaymentRepository
import com.harsh.gymmanager.domain.suggestions.UsageRepository
import com.harsh.gymmanager.domain.suggestions.UsageStats
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The gym's payments online. The database refuses a payment above what's still due on its membership
 * (raised here as an OverpaymentException) and lets only the owner delete one.
 */
internal class SupabasePaymentRepository(
    private val supabase: SupabaseClient,
    private val refresher: Refresher,
    private val gym: GymGate,
) : PaymentRepository {
    private val payments get() = supabase.postgrest.from("payments")

    override suspend fun addPayment(payment: Payment): String = write {
        payments.insert(
            buildJsonObject {
                put("gym_id", gym.gymId())
                put("membership_id", payment.membershipId)
                put("amount", payment.amount)
                put("mode", payment.mode.name)
                put("paid_on", payment.paidOn.toString())
                put("note", payment.note?.trim()?.ifEmpty { null })
            },
        ) { select(Columns.raw("id")) }.decodeSingle<IdRow>().id
    }

    override suspend fun deletePayment(id: String) = write {
        payments.delete { filter { eq("id", id) } }
        Unit
    }

    override suspend fun getAmountDue(membershipId: String): Long? = remote {
        supabase.postgrest.from("memberships")
            .select(Columns.raw("price, discount, payments(amount)")) { filter { eq("id", membershipId) } }
            .decodeSingleOrNull<DueRow>()
            ?.let { it.price - it.discount - it.payments.sumOf { p -> p.amount } }
    }

    override fun observeRevenue(from: LocalDate, to: LocalDate): Flow<Long> = refresher.observe {
        amountsBetween(from, to).sumOf { it.amount }
    }

    override fun observeDailyRevenue(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Long>> = refresher.observe {
        amountsBetween(from, to).groupBy { it.paidOn }.mapValues { (_, day) -> day.sumOf { it.amount } }
    }

    private suspend fun amountsBetween(from: LocalDate, to: LocalDate): List<AmountRow> =
        payments.select(Columns.raw("amount, paid_on")) {
            filter {
                gte("paid_on", from.toString())
                lte("paid_on", to.toString())
            }
        }.decodeList<AmountRow>()

    /** Newest first; [limit] null for all. */
    override fun observePayments(from: LocalDate, to: LocalDate, limit: Int?): Flow<List<PaymentRecord>> = refresher.observe {
        payments.select(Columns.raw("id, amount, mode, paid_on, memberships(plan_name, member_id, members(name))")) {
            filter {
                gte("paid_on", from.toString())
                lte("paid_on", to.toString())
            }
            order("paid_on", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
            if (limit != null) limit(limit.toLong())
        }.decodeList<PaymentRecordRow>().map { it.toDomain() }
    }

    private suspend fun <T> write(block: suspend () -> T): T = remote(block).also { refresher.changed() }

    @Serializable
    private data class AmountRow(val amount: Long, @SerialName("paid_on") val paidOn: LocalDate)

    @Serializable
    private data class DueRow(val price: Long, val discount: Long, val payments: List<Amount> = emptyList()) {
        @Serializable
        data class Amount(val amount: Long)
    }
}

/** What the gym does most often, counted by the database. */
internal class SupabaseUsageRepository(private val supabase: SupabaseClient) : UsageRepository {
    override suspend fun stats(): UsageStats = remote {
        val row = supabase.postgrest.rpc("usage_stats").decodeAs<UsageRow>()
        UsageStats(planUses = row.planUses, paymentAmounts = row.paymentAmounts, discounts = row.discounts)
    }

    @Serializable
    private data class UsageRow(
        @SerialName("plan_uses") val planUses: Map<String, Int> = emptyMap(),
        @SerialName("payment_amounts") val paymentAmounts: List<Long> = emptyList(),
        val discounts: List<Long> = emptyList(),
    )
}
