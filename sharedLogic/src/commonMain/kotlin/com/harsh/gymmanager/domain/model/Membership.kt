package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ensure
import kotlinx.datetime.LocalDate

/**
 * One purchase or renewal. [startDate] and [endDate] are both inclusive.
 * [payments] and [pauses] are filled when reading; they're ignored when saving a membership.
 */
data class Membership(
    val id: String = "",
    val memberId: String,
    val planId: String?,
    /** Copied from the plan at sale time so renaming a plan doesn't rewrite history. */
    val planName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Whole rupees. */
    val price: Long,
    /** Whole rupees off [price]. */
    val discount: Long = 0,
    val notes: String? = null,
    val payments: List<Payment> = emptyList(),
    /** Oldest first. [endDate] already includes the days they add. */
    val pauses: List<MembershipPause> = emptyList(),
) {
    init {
        ensure(price >= 0, ValidationError.PRICE_NEGATIVE)
        ensure(discount in 0..price, ValidationError.DISCOUNT_TOO_LARGE)
        ensure(endDate >= startDate, ValidationError.END_BEFORE_START)
    }

    val amountCharged: Long get() = price - discount
    val amountPaid: Long get() = payments.sumOf { it.amount }
    val amountDue: Long get() = amountCharged - amountPaid

    /** The pause running on [today] or still to come, if any; a membership has at most one. */
    fun pauseOn(today: LocalDate): MembershipPause? = pauses.firstOrNull { it.resumesOn > today }
}

/** A stretch the member is away: from [pausedFrom] until they're back on [resumesOn]. */
data class MembershipPause(val pausedFrom: LocalDate, val resumesOn: LocalDate) {
    fun isRunning(today: LocalDate): Boolean = today >= pausedFrom && today < resumesOn
}
