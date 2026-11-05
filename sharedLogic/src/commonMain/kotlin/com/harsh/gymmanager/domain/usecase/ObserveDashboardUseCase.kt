package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class Dashboard(
    /** Members with a running membership, including those expiring soon. */
    val activeCount: Int,
    /** Soonest expiry first. */
    val expiringSoon: List<MemberSummary>,
    val expiredCount: Int,
    val membersWithDuesCount: Int,
    /** Whole rupees owed across all members. */
    val totalDue: Long,
    /** Whole rupees collected since the 1st of this month. */
    val revenueThisMonth: Long,
)

class ObserveDashboardUseCase(
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    operator fun invoke(): Flow<Dashboard> {
        val today = clock.todayIn(timeZone)
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY)
        return combine(
            memberRepository.observeMembers(),
            paymentRepository.observeRevenue(monthStart, monthEnd),
        ) { members, revenue ->
            val withDues = members.filter { it.amountDue > 0 }
            Dashboard(
                activeCount = members.count {
                    it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.EXPIRING_SOON
                },
                expiringSoon = members
                    .filter { it.status == MembershipStatus.EXPIRING_SOON }
                    .sortedBy { it.latestEndDate },
                expiredCount = members.count { it.status == MembershipStatus.EXPIRED },
                membersWithDuesCount = withDues.size,
                totalDue = withDues.sumOf { it.amountDue },
                revenueThisMonth = revenue,
            )
        }
    }
}
