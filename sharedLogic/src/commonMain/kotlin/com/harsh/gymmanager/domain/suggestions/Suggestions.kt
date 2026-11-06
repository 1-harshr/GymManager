package com.harsh.gymmanager.domain.suggestions

import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Plan

/** What the owner has done before, most used first. Empty for a new gym; the suggestions fill in. */
data class UsageStats(
    /** Memberships sold per plan id. */
    val planUses: Map<String, Int> = emptyMap(),
    val paymentAmounts: List<Long> = emptyList(),
    val discounts: List<Long> = emptyList(),
)

interface UsageRepository {
    suspend fun stats(): UsageStats
}

class SuggestionsUseCase(private val repository: UsageRepository) {
    suspend fun stats(): UsageStats = repository.stats()
}

/** One amount chip. [Kind.FULL] and [Kind.HALF] are named on the chip; [Kind.PAY_LATER] clears the amount. */
data class AmountSuggestion(val amount: Long, val kind: Kind) {
    enum class Kind { FULL, HALF, USUAL, PAY_LATER }
}

/** Plans the owner sells most come first; ties keep their usual (alphabetical) order. */
fun List<Plan>.mostUsedFirst(stats: UsageStats): List<Plan> = sortedByDescending { stats.planUses[it.id] ?: 0 }

/**
 * Amounts for a payment of up to [due]: the full amount, half, the ones taken most often, then
 * round figures. [payLater] adds a chip for paying nothing now.
 */
fun amountSuggestions(due: Long, stats: UsageStats, payLater: Boolean = false): List<AmountSuggestion> {
    if (due <= 0) return emptyList()
    val picks = buildList {
        add(AmountSuggestion(due, AmountSuggestion.Kind.FULL))
        if (due >= 2) add(AmountSuggestion(due / 2, AmountSuggestion.Kind.HALF))
        stats.paymentAmounts.filter { it in 1 until due }.forEach { add(AmountSuggestion(it, AmountSuggestion.Kind.USUAL)) }
        ROUND_AMOUNTS.filter { it < due }.forEach { add(AmountSuggestion(it, AmountSuggestion.Kind.USUAL)) }
    }.distinctBy { it.amount }.take(MAX_CHIPS - if (payLater) 1 else 0)
    return if (payLater) picks + AmountSuggestion(0, AmountSuggestion.Kind.PAY_LATER) else picks
}

/** Discounts below [price]: the ones given most often, then a few round ones. */
fun discountSuggestions(price: Long, stats: UsageStats): List<Long> =
    (stats.discounts + STARTER_DISCOUNTS).filter { it in 1 until price }.distinct().take(MAX_CHIPS)

/** Plan lengths a gym usually offers, from a one-day trial to a year. */
val PLAN_LENGTHS: List<Pair<Int, DurationUnit>> = listOf(
    1 to DurationUnit.DAYS,
    7 to DurationUnit.DAYS,
    1 to DurationUnit.MONTHS,
    3 to DurationUnit.MONTHS,
    6 to DurationUnit.MONTHS,
    12 to DurationUnit.MONTHS,
)

private const val MAX_CHIPS = 4
private val ROUND_AMOUNTS = listOf(500L, 1000L, 2000L, 5000L)
private val STARTER_DISCOUNTS = listOf(100L, 200L, 500L)
