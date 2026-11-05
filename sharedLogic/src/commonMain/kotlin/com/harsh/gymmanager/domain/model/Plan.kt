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
        require(name.isNotBlank()) { "name must not be blank" }
        require(durationValue > 0) { "durationValue must be positive" }
        require(defaultPrice >= 0) { "defaultPrice must not be negative" }
    }

    fun endDateFor(start: LocalDate): LocalDate = membershipEndDate(start, durationValue, durationUnit)
}
