package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.MembershipStatus
import kotlinx.datetime.LocalDate

/** What the member list shows for each member. */
data class MemberSummary(
    val memberId: Long,
    val name: String,
    val phone: String?,
    val photoPath: String?,
    val isArchived: Boolean,
    val latestEndDate: LocalDate?,
    /** Whole rupees still owed across all of the member's memberships. */
    val amountDue: Long,
    val status: MembershipStatus,
)
