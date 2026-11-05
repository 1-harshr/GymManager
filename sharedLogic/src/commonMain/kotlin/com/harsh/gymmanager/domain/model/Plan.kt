package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.membershipEndDate
import kotlinx.datetime.LocalDate

/** A template for new memberships. Changing a plan never alters memberships already sold. */
data class Plan(
    val id: Long = 0,
    val name: String,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    /** Whole rupees. */
    val defaultPrice: Long,
    val isArchived: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Plan name is required" }
        require(durationValue > 0) { "Duration must be at least 1" }
        require(defaultPrice >= 0) { "Price can't be negative" }
    }

    fun endDateFor(start: LocalDate): LocalDate = membershipEndDate(start, durationValue, durationUnit)
}
