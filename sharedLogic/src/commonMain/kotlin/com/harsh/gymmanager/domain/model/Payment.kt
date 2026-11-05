package com.harsh.gymmanager.domain.model

import kotlinx.datetime.LocalDate

data class Payment(
    val id: Long = 0,
    val membershipId: Long,
    /** Whole rupees. */
    val amount: Long,
    val mode: PaymentMode = PaymentMode.CASH,
    val paidOn: LocalDate,
    val note: String? = null,
) {
    init {
        require(amount > 0) { "amount must be positive" }
    }
}
