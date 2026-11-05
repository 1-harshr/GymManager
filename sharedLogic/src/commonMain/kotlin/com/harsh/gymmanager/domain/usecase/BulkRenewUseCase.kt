package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.renewalStartDate
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** One member's renewal as it will be saved. */
data class BulkRenewItem(val member: Member, val startDate: LocalDate, val endDate: LocalDate)

/**
 * Renews many members on one plan at the plan's price. Each member's dates follow the
 * usual renewal rule, so someone still active continues from their own end date.
 */
class BulkRenewUseCase(
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** What [invoke] would save, for the confirmation screen. Archived members are left out. */
    suspend fun preview(memberIds: Collection<Long>, plan: Plan): List<BulkRenewItem> {
        val today = clock.todayIn(timeZone)
        return memberRepository.getMembers(memberIds.toList())
            .filterNot { it.isArchived }
            .map { member ->
                val start = renewalStartDate(membershipRepository.getLatestEndDate(member.id), today)
                BulkRenewItem(member, start, plan.endDateFor(start))
            }
    }

    /**
     * Saves every renewal in one transaction and returns how many were saved.
     * With [markPaid], each also gets a payment of the full price, dated today.
     */
    suspend operator fun invoke(
        memberIds: Collection<Long>,
        plan: Plan,
        markPaid: Boolean = false,
        paymentMode: PaymentMode = PaymentMode.CASH,
    ): Result<Int> = resultOf {
        val today = clock.todayIn(timeZone)
        val items = preview(memberIds, plan).map { item ->
            val membership = Membership(
                memberId = item.member.id,
                planId = plan.id,
                planName = plan.name,
                startDate = item.startDate,
                endDate = item.endDate,
                price = plan.defaultPrice,
            )
            val payment = if (markPaid && plan.defaultPrice > 0) {
                Payment(membershipId = 0, amount = plan.defaultPrice, mode = paymentMode, paidOn = today)
            } else {
                null
            }
            membership to payment
        }
        membershipRepository.addMemberships(items).size
    }
}
