package com.harsh.gymmanager.domain.insights

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import com.harsh.gymmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

// Everything here is whole rupees and counts worked out from the gym's own data; nothing is guessed.

/** Money collected this month against last month, up to the same day of the month. */
data class CollectionPace(
    val thisMonthToDate: Long,
    /** Last month from the 1st to the same day (or its last day, if it was shorter). */
    val lastMonthToDate: Long,
    val lastMonthTotal: Long,
    /** Running total for each day of this month up to today; index 0 is the 1st. */
    val thisMonthCumulative: List<Long>,
    /** Running total for every day of last month. */
    val lastMonthCumulative: List<Long>,
) {
    /** Whole-number change against last month to date; null when last month had nothing to compare with. */
    val changePercent: Int?
        get() = if (lastMonthToDate == 0L) null else ((thisMonthToDate - lastMonthToDate) * 100 / lastMonthToDate).toInt()
}

/** What this month could bring in if every due is paid and every membership ending this month renews at its price. */
data class MonthForecast(
    val collected: Long,
    val outstandingDues: Long,
    val renewalsValue: Long,
    val renewalsCount: Int,
) {
    val total: Long get() = collected + outstandingDues + renewalsValue
}

data class EndingMember(val memberId: String, val name: String, val endDate: LocalDate, val value: Long)

data class RenewalOutlook(
    /** Members whose membership ends within the "expiring soon" window, soonest first. */
    val endingSoon: List<EndingMember>,
    /** What those renewals are worth at their current price. */
    val atStake: Long,
    /** Members whose membership ended in the last [WINDOW_DAYS] days, and how many of them have renewed. */
    val endedRecently: Int,
    val renewedRecently: Int,
) {
    val renewalPercent: Int? get() = if (endedRecently == 0) null else renewedRecently * 100 / endedRecently
}

/** A member who has owed money for at least [ATTENTION_DUE_DAYS], counted from their oldest unpaid membership's start. */
data class DueAge(val memberId: String, val name: String, val amount: Long, val owedSince: LocalDate, val days: Int)

/** A member whose last membership ended recently and who hasn't renewed. */
data class LapsedMember(
    val memberId: String,
    val name: String,
    val endedOn: LocalDate,
    val daysAgo: Int,
    val planName: String,
    val value: Long,
)

data class GymInsights(
    val today: LocalDate,
    val pace: CollectionPace,
    val forecast: MonthForecast,
    val renewals: RenewalOutlook,
    /** Longest-owed first; at most [TOP]. */
    val oldestDues: List<DueAge>,
    /** Members who have owed money for at least [ATTENTION_DUE_DAYS]. */
    val overdueCount: Int,
    /** Most recently lapsed first; at most [TOP]. */
    val winBack: List<LapsedMember>,
    val winBackCount: Int,
)

/** How far back "recently" reaches for renewals and win-backs. */
const val WINDOW_DAYS = 30

/** How many members each list shows. */
const val TOP = 3

/** A due this old is worth chasing; fresher ones are usually paid at the next visit. */
const val ATTENTION_DUE_DAYS = 7

class ObserveInsightsUseCase(
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val paymentRepository: PaymentRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** Reads "today" once; observe again after midnight. */
    operator fun invoke(): Flow<GymInsights> {
        val today = clock.todayIn(timeZone)
        val lastMonthStart = monthStart(today).minus(DatePeriod(months = 1))
        return combine(
            memberRepository.observeMembers(),
            membershipRepository.observeAllMemberships(),
            paymentRepository.observeDailyRevenue(lastMonthStart, monthEnd(today)),
        ) { members, memberships, daily -> computeInsights(today, members, memberships, daily) }
    }
}

/** [members] and [memberships] are for members who aren't archived; [daily] covers last month and this one. */
fun computeInsights(
    today: LocalDate,
    members: List<MemberSummary>,
    memberships: List<Membership>,
    daily: Map<LocalDate, Long>,
): GymInsights {
    val names = members.associate { it.memberId to it.name }
    val byMember = memberships.filter { it.memberId in names }.groupBy { it.memberId }
    val latest = byMember.mapValues { (_, list) -> list.maxBy { it.endDate } }
    val pace = pace(today, daily)

    val endingSoon = members
        .filter { it.status == MembershipStatus.EXPIRING_SOON }
        .mapNotNull { m -> latest[m.memberId]?.let { EndingMember(m.memberId, m.name, it.endDate, it.amountCharged) } }
        .sortedBy { it.endDate }

    val endingThisMonth = latest.values.filter { it.endDate >= today && it.endDate <= monthEnd(today) }
    val windowStart = today.minus(WINDOW_DAYS, DateTimeUnit.DAY)

    // For each member, their most recent membership that ended in the window; renewed if a later one exists.
    var ended = 0
    var renewed = 0
    byMember.values.forEach { list ->
        val endedInWindow = list.filter { it.endDate >= windowStart && it.endDate < today }.maxByOrNull { it.startDate } ?: return@forEach
        ended++
        if (list.any { it.startDate > endedInWindow.startDate }) renewed++
    }

    val dues = byMember.mapNotNull { (memberId, list) ->
        val owing = list.filter { it.amountDue > 0 }
        val oldest = owing.minByOrNull { it.startDate } ?: return@mapNotNull null
        DueAge(memberId, names.getValue(memberId), owing.sumOf { it.amountDue }, oldest.startDate, oldest.startDate.daysUntil(today).coerceAtLeast(0))
    }.filter { it.days >= ATTENTION_DUE_DAYS }.sortedWith(compareByDescending<DueAge> { it.days }.thenByDescending { it.amount })

    val lapsed = latest.values
        .filter { it.endDate >= windowStart && it.endDate < today }
        .sortedByDescending { it.endDate }
        .map { LapsedMember(it.memberId, names.getValue(it.memberId), it.endDate, it.endDate.daysUntil(today), it.planName, it.amountCharged) }

    return GymInsights(
        today = today,
        pace = pace,
        forecast = MonthForecast(
            collected = pace.thisMonthToDate,
            outstandingDues = memberships.filter { it.memberId in names }.sumOf { it.amountDue },
            renewalsValue = endingThisMonth.sumOf { it.amountCharged },
            renewalsCount = endingThisMonth.size,
        ),
        renewals = RenewalOutlook(endingSoon, endingSoon.sumOf { it.value }, ended, renewed),
        oldestDues = dues.take(TOP),
        overdueCount = dues.size,
        winBack = lapsed.take(TOP),
        winBackCount = lapsed.size,
    )
}

private fun pace(today: LocalDate, daily: Map<LocalDate, Long>): CollectionPace {
    fun runningTotals(start: LocalDate, days: Int): List<Long> {
        var sum = 0L
        return List(days) { offset -> (sum + (daily[start.plus(offset, DateTimeUnit.DAY)] ?: 0L)).also { sum = it } }
    }
    val thisMonth = runningTotals(monthStart(today), today.day)
    val lastStart = monthStart(today).minus(DatePeriod(months = 1))
    val lastMonth = runningTotals(lastStart, lastStart.daysUntil(monthStart(today)))
    return CollectionPace(
        thisMonthToDate = thisMonth.last(),
        lastMonthToDate = lastMonth[minOf(today.day, lastMonth.size) - 1],
        lastMonthTotal = lastMonth.last(),
        thisMonthCumulative = thisMonth,
        lastMonthCumulative = lastMonth,
    )
}

private fun monthStart(date: LocalDate) = LocalDate(date.year, date.month, 1)

private fun monthEnd(date: LocalDate) = monthStart(date).plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY)
