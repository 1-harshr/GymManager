package com.harsh.gymmanager.domain.model

import kotlinx.datetime.LocalDate

/** A payment as a list of payments shows it: who paid, for which plan, how and when. */
data class PaymentRecord(
    val paymentId: Long,
    val memberId: Long,
    val memberName: String,
    val planName: String,
    /** Whole rupees. */
    val amount: Long,
    val mode: PaymentMode,
    val paidOn: LocalDate,
)
