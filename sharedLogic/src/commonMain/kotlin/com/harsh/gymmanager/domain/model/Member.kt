package com.harsh.gymmanager.domain.model

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
        require(name.isNotBlank()) { "name must not be blank" }
    }
}
