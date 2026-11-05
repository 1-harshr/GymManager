package com.harsh.gymmanager.domain.insights

import com.harsh.gymmanager.presentation.formatRupees
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * A language model that runs on the phone (Gemini Nano on Android, Apple's on iOS), where the
 * phone has one. A callback rather than a suspend function so Swift can implement it.
 */
interface OnDeviceModel {
    /** Calls [onResult] once with the reply, or null when there's no model on this phone or it failed. */
    fun generate(instructions: String, prompt: String, onResult: (String?) -> Unit)
}

/** For phones without an on-device model; the brief falls back to its template. */
object NoOnDeviceModel : OnDeviceModel {
    override fun generate(instructions: String, prompt: String, onResult: (String?) -> Unit) = onResult(null)
}

/**
 * Turns the day's insights into a few natural sentences with the phone's own model. The numbers
 * are worked out in code and handed over as facts; a reply with any number not among them is
 * thrown away, so the brief can be reworded by the model but never made up.
 */
class WriteBriefUseCase(private val model: OnDeviceModel) {
    private var last: Pair<String, String?>? = null

    /** The brief in English, or null to use the template; the same facts are only sent once. */
    suspend fun write(insights: GymInsights, gymName: String): String? {
        val facts = briefFacts(insights, gymName)
        last?.let { (lastFacts, reply) -> if (lastFacts == facts) return reply }
        val reply = ask(facts)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH && isFaithful(it, facts) }
        last = facts to reply
        return reply
    }

    private suspend fun ask(facts: String): String? = try {
        withTimeout(TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                model.generate(BRIEF_INSTRUCTIONS, facts) { if (continuation.isActive) continuation.resume(it) }
            }
        }
    } catch (e: TimeoutCancellationException) {
        null
    }

    private companion object {
        val TIMEOUT = 20.seconds
        const val MAX_LENGTH = 420
    }
}

const val BRIEF_INSTRUCTIONS =
    "You write the morning note on a gym owner's phone in India. Using only the facts given, write " +
        "2 or 3 short, warm sentences in plain English: say how collections are going, then the one " +
        "thing most worth doing today. Copy every number exactly as written, with ₹ and commas. Don't " +
        "add numbers, names or advice that aren't in the facts. No greeting, no lists, no emoji."

/** The day's insights as plain English lines; everything the model may mention is here. */
fun briefFacts(insights: GymInsights, gymName: String): String = buildList {
    val pace = insights.pace
    add("Gym: $gymName.")
    add("Collected so far this month: ${formatRupees(pace.thisMonthToDate)}.")
    pace.changePercent?.let {
        val direction = if (it >= 0) "ahead of" else "behind"
        add("That is ${kotlin.math.abs(it)}% $direction last month by the same date (${formatRupees(pace.lastMonthToDate)}).")
    }
    val forecast = insights.forecast
    if (forecast.total > forecast.collected) {
        add("This month could reach ${formatRupees(forecast.total)} if all dues are paid and memberships ending this month renew.")
    }
    val renewals = insights.renewals
    if (renewals.endingSoon.isNotEmpty()) {
        val first = renewals.endingSoon.first()
        add("${renewals.endingSoon.size} memberships worth ${formatRupees(renewals.atStake)} end this week; the first is ${first.name}'s.")
    }
    renewals.renewalPercent?.let { add("${renewals.renewedRecently} of ${renewals.endedRecently} members whose membership ended in the last $WINDOW_DAYS days renewed ($it%).") }
    insights.oldestDues.firstOrNull()?.let { add("${it.name} has owed ${formatRupees(it.amount)} for ${it.days} days, the longest of ${insights.overdueCount} members owing for a week or more.") }
    insights.winBack.firstOrNull()?.let { add("${it.name}'s membership ended ${it.daysAgo} days ago and they haven't renewed; ${insights.winBackCount} members lapsed in the last $WINDOW_DAYS days.") }
}.joinToString("\n")

/** True when every number in [reply] (ignoring ₹ and digit grouping) also appears in [facts]. */
fun isFaithful(reply: String, facts: String): Boolean {
    val allowed = numbers(facts)
    return numbers(reply).all { it in allowed }
}

private fun numbers(text: String): Set<String> =
    Regex("""\d[\d,]*""").findAll(text).map { it.value.replace(",", "").trimStart('0').ifEmpty { "0" } }.toSet()
