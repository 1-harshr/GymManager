package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ensure
import com.harsh.gymmanager.domain.membershipEndDate
import kotlinx.datetime.LocalDate

/** A template for new memberships. Changing a plan never alters memberships already sold. */
data class Plan(
    val id: String = "",
    val name: String,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    /** Whole rupees. */
    val defaultPrice: Long,
    val isArchived: Boolean = false,
) {
    init {
        ensure(name.isNotBlank(), ValidationError.PLAN_NAME_REQUIRED)
        ensure(durationValue > 0, ValidationError.DURATION_TOO_SHORT)
        ensure(defaultPrice >= 0, ValidationError.PRICE_NEGATIVE)
    }

    fun endDateFor(start: LocalDate): LocalDate = membershipEndDate(start, durationValue, durationUnit)
}
