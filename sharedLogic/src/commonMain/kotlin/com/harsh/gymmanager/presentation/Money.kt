package com.harsh.gymmanager.presentation

import kotlin.math.absoluteValue

/** Formats whole rupees with Indian digit grouping, e.g. ₹1,23,456. */
fun formatRupees(amount: Long): String {
    val digits = amount.absoluteValue.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val lakhsAndAbove = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$lakhsAndAbove,${digits.takeLast(3)}"
    }
    return if (amount < 0) "-₹$grouped" else "₹$grouped"
}

/** Parses input like "1500", "1,500" or "₹ 1500" into whole rupees; null if it isn't a whole number. */
fun parseRupees(text: String): Long? =
    text.filterNot { it == ',' || it == '₹' || it.isWhitespace() }
        .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.toLongOrNull()
