package com.harsh.gymmanager.domain.assistant

import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.Dashboard
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.domain.usecase.mostUrgentFirst
import kotlinx.coroutines.flow.first

/** Up to [limit] members of a longer list; [totalCount] is how many matched in all. */
data class MemberList(val members: List<MemberSummary>, val totalCount: Int)

sealed interface PaymentOutcome {
    /** [remainingDue] is what the member still owes after this payment. */
    data class Recorded(val memberName: String, val amount: Long, val remainingDue: Long) : PaymentOutcome
    data class MoreThanDue(val memberName: String, val amountDue: Long) : PaymentOutcome
    data object InvalidAmount : PaymentOutcome
    data object MemberNotFound : PaymentOutcome
}

/**
 * One-shot answers and actions for voice assistants (Android App Functions, iOS App Intents).
 * Reads a snapshot of each flow instead of observing it. Nothing here deletes data.
 */
class GymAssistant(
    private val members: MemberUseCases,
    private val plans: PlanUseCases,
    private val memberships: MembershipUseCases,
    private val payments: PaymentUseCases,
    private val dashboard: ObserveDashboardUseCase,
) {
    @Throws(Exception::class)
    suspend fun summary(): Dashboard = dashboard().first()

    /** Unarchived members whose name or phone matches [query]. */
    @Throws(Exception::class)
    suspend fun findMembers(query: String, limit: Int = MAX_RESULTS): MemberList =
        members.observeMembers(query.trim()).first().toMemberList(limit)

    @Throws(Exception::class)
    suspend fun member(memberId: String): MemberSummary? =
        members.observeMembers().first().firstOrNull { it.memberId == memberId }

    /** Most urgent first: soonest expiry, latest lapse, or largest due. */
    @Throws(Exception::class)
    suspend fun members(filter: MemberFilter, limit: Int = MAX_RESULTS): MemberList =
        members.observeMembers(filter = filter).first().mostUrgentFirst(filter).toMemberList(limit)

    /** Plans that can be sold now. */
    @Throws(Exception::class)
    suspend fun plans(): List<Plan> = plans.observePlans().first()

    /**
     * Records [amount] whole rupees against the member's dues, oldest membership first. A payment
     * that covers more than one membership is saved as one payment per membership.
     */
    @Throws(Exception::class)
    suspend fun recordPayment(memberId: String, amount: Long, mode: PaymentMode = PaymentMode.CASH): PaymentOutcome {
        val member = members.observeMember(memberId).first() ?: return PaymentOutcome.MemberNotFound
        if (amount <= 0) return PaymentOutcome.InvalidAmount
        // Newest first from the repository; settle the oldest dues first.
        val owing = memberships.observeMemberships(memberId).first().filter { it.amountDue > 0 }.reversed()
        val totalDue = owing.sumOf { it.amountDue }
        if (amount > totalDue) return PaymentOutcome.MoreThanDue(member.name, totalDue)

        var left = amount
        for (membership in owing) {
            if (left == 0L) break
            val part = minOf(left, membership.amountDue)
            payments.recordPayment(membership.id, part, mode).getOrThrow()
            left -= part
        }
        return PaymentOutcome.Recorded(member.name, amount, totalDue - amount)
    }

    private fun List<MemberSummary>.toMemberList(limit: Int) = MemberList(take(limit), size)

    companion object {
        /** Enough to read out or show; assistants handle long lists poorly. */
        const val MAX_RESULTS = 10
    }
}
