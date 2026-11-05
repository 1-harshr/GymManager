package com.harsh.gymmanager.domain.digest

import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What the owner needs to know on the morning of [date]. Amounts are whole rupees. */
data class MorningDigest(
    val date: LocalDate,
    /** Names of members whose latest membership ends on [date]. */
    val endingToday: List<String>,
    /** Members whose latest membership ends within [EXPIRING_SOON_DAYS] days after [date]. */
    val endingSoonCount: Int,
    /** Names of members whose latest membership ended the day before [date]. */
    val endedYesterday: List<String>,
    val membersWithDuesCount: Int,
    val totalDue: Long,
    /** Collected from the 1st of [date]'s month up to now. */
    val collectedThisMonth: Long,
)

/**
 * Builds morning digests from the data as it stands now. Data only changes when the owner uses
 * the app, so digests for coming days stay right until then; only the dates move them along.
 */
class MorningDigestUseCase(
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
) {
    suspend fun digest(date: LocalDate): MorningDigest = digests(date, days = 1).single()

    /** One digest for each of [days] mornings, starting on [from]. */
    suspend fun digests(from: LocalDate, days: Int): List<MorningDigest> {
        val members = memberRepository.observeMembers().first()
        val withDues = members.filter { it.amountDue > 0 }
        val revenueByMonth = mutableMapOf<LocalDate, Long>()
        return (0 until days).map { offset ->
            val date = from.plus(offset, DateTimeUnit.DAY)
            val soonUntil = date.plus(EXPIRING_SOON_DAYS, DateTimeUnit.DAY)
            val yesterday = date.minus(1, DateTimeUnit.DAY)
            val monthStart = LocalDate(date.year, date.month, 1)
            MorningDigest(
                date = date,
                endingToday = members.filter { it.latestEndDate == date }.map { it.name },
                endingSoonCount = members.count { end -> end.latestEndDate?.let { it > date && it <= soonUntil } == true },
                endedYesterday = members.filter { it.latestEndDate == yesterday }.map { it.name },
                membersWithDuesCount = withDues.size,
                totalDue = withDues.sumOf { it.amountDue },
                collectedThisMonth = revenueByMonth.getOrPut(monthStart) {
                    val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY)
                    paymentRepository.observeRevenue(monthStart, monthEnd).first()
                },
            )
        }
    }
}
