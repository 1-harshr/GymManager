package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ensure
import kotlinx.datetime.LocalDate

data class Member(
    val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val gender: Gender? = null,
    val dateOfBirth: LocalDate? = null,
    /** Path to the photo file in app storage. */
    val photoPath: String? = null,
    val notes: String? = null,
    val joinedOn: LocalDate,
    val isArchived: Boolean = false,
) {
    init {
        ensure(name.isNotBlank(), ValidationError.NAME_REQUIRED)
    }
}
