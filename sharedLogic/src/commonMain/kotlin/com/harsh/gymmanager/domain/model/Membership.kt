package com.harsh.gymmanager.domain.model

import kotlinx.datetime.LocalDate

/**
 * One purchase or renewal. [startDate] and [endDate] are both inclusive.
 * [payments] is filled when reading; it is ignored when saving a membership.
 */
data class Membership(
    val id: Long = 0,
    val memberId: Long,
    val planId: Long?,
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
        require(price >= 0) { "price must not be negative" }
        require(discount in 0..price) { "discount must be between 0 and price" }
        require(endDate >= startDate) { "endDate must not be before startDate" }
    }

    val amountCharged: Long get() = price - discount
    val amountPaid: Long get() = payments.sumOf { it.amount }
    val amountDue: Long get() = amountCharged - amountPaid
}
