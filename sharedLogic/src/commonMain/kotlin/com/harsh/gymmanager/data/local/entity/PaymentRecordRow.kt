package com.harsh.gymmanager.data.local.entity

import com.harsh.gymmanager.domain.model.PaymentMode
import kotlinx.datetime.LocalDate

/** A payment joined with its member and membership; read-only, not a table. */
data class PaymentRecordRow(
    val paymentId: Long,
    val memberId: Long,
    val memberName: String,
    val planName: String,
    val amount: Long,
    val mode: PaymentMode,
    val paidOn: LocalDate,
)
