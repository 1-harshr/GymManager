package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ensure
import kotlinx.datetime.LocalDate

data class Payment(
    val id: String = "",
    val membershipId: String,
    /** Whole rupees. */
    val amount: Long,
    val mode: PaymentMode = PaymentMode.CASH,
    val paidOn: LocalDate,
    val note: String? = null,
) {
    init {
        ensure(amount > 0, ValidationError.AMOUNT_NOT_POSITIVE)
    }
}
