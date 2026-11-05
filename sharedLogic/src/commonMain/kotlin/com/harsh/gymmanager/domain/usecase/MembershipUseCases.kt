package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.renewalStartDate
import com.harsh.gymmanager.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Suggested values to pre-fill the sell/renew form. */
data class MembershipQuote(
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Whole rupees. */
    val price: Long,
)

class ObserveMembershipsUseCase(private val repository: MembershipRepository) {
    /** Newest first, each with its payments. */
    operator fun invoke(memberId: Long): Flow<List<Membership>> = repository.observeMemberships(memberId)
}

/** Renewals continue from a running membership; otherwise the new one starts today. */
class QuoteMembershipUseCase(
    private val repository: MembershipRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    suspend operator fun invoke(memberId: Long, plan: Plan): MembershipQuote {
        val start = renewalStartDate(repository.getLatestEndDate(memberId), clock.todayIn(timeZone))
        return MembershipQuote(startDate = start, endDate = plan.endDateFor(start), price = plan.defaultPrice)
    }
}

/**
 * Sells a new membership or renewal. Anything left null falls back to the quote:
 * the renewal start date, the plan's end date for that start, and the plan's price.
 * A [firstPaymentAmount] is recorded in the same transaction, dated today.
 */
class SellMembershipUseCase(
    private val repository: MembershipRepository,
    private val quoteMembership: QuoteMembershipUseCase,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    suspend operator fun invoke(
        memberId: Long,
        plan: Plan,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        price: Long? = null,
        discount: Long = 0,
        notes: String? = null,
        firstPaymentAmount: Long? = null,
        firstPaymentMode: PaymentMode = PaymentMode.CASH,
    ): Result<Long> = resultOf {
        val quote = quoteMembership(memberId, plan)
        val start = startDate ?: quote.startDate
        val membership = Membership(
            memberId = memberId,
            planId = plan.id,
            planName = plan.name,
            startDate = start,
            endDate = endDate ?: plan.endDateFor(start),
            price = price ?: quote.price,
            discount = discount,
            notes = notes,
        )
        val firstPayment = firstPaymentAmount?.takeIf { it > 0 }?.let { amount ->
            Payment(membershipId = 0, amount = amount, mode = firstPaymentMode, paidOn = clock.todayIn(timeZone))
        }
        repository.addMembership(membership, firstPayment)
    }
}

/** For corrections such as a changed end date or discount. Payments are not touched. */
class UpdateMembershipUseCase(private val repository: MembershipRepository) {
    suspend operator fun invoke(membership: Membership): Result<Unit> =
        resultOf { repository.updateMembership(membership) }
}

/** Also removes the membership's payments. */
class DeleteMembershipUseCase(private val repository: MembershipRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.deleteMembership(id) }
}

class MembershipUseCases(repository: MembershipRepository, clock: Clock, timeZone: TimeZone) {
    val observeMemberships = ObserveMembershipsUseCase(repository)
    val quoteMembership = QuoteMembershipUseCase(repository, clock, timeZone)
    val sellMembership = SellMembershipUseCase(repository, quoteMembership, clock, timeZone)
    val updateMembership = UpdateMembershipUseCase(repository)
    val deleteMembership = DeleteMembershipUseCase(repository)
}
