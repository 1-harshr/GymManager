package com.harsh.gymmanager.presentation

import com.harsh.gymmanager.domain.OverpaymentException

/** Domain validation messages are already user-facing; anything unexpected gets a generic message. */
fun Throwable.toUserMessage(): String = when (this) {
    is OverpaymentException ->
        if (amountDue == 0L) "Nothing is due on this membership" else "Amount is more than the ${formatRupees(amountDue)} due"
    is IllegalArgumentException -> message ?: GENERIC_ERROR
    else -> GENERIC_ERROR
}

private const val GENERIC_ERROR = "Something went wrong. Please try again."
