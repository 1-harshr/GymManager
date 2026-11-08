package com.harsh.gymmanager.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A member's visit: one a day at most, on the gym's date. */
data class CheckIn(
    val id: String,
    val memberId: String,
    val date: LocalDate,
    val at: Instant,
)
