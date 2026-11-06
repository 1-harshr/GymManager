package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ensure
import kotlinx.datetime.LocalDate

/**
 * One purchase or renewal. [startDate] and [endDate] are both inclusive.
 * [payments] is filled when reading; it is ignored when saving a membership.
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
) {
    init {
        ensure(price >= 0, ValidationError.PRICE_NEGATIVE)
        ensure(discount in 0..price, ValidationError.DISCOUNT_TOO_LARGE)
        ensure(endDate >= startDate, ValidationError.END_BEFORE_START)
    }

    val amountCharged: Long get() = price - discount
    val amountPaid: Long get() = payments.sumOf { it.amount }
    val amountDue: Long get() = amountCharged - amountPaid
}
