package com.harsh.gymmanager.data.local.entity

import androidx.room.DatabaseView
import kotlinx.datetime.LocalDate

/** One row per member with the figures the member list needs; status is derived from [latestEndDate]. */
@DatabaseView(
    viewName = "member_summary",
    value = """
        SELECT m.id AS memberId, m.name, m.phone, m.photoPath, m.isArchived,
            (SELECT MAX(ms.endDate) FROM memberships ms WHERE ms.memberId = m.id) AS latestEndDate,
            (SELECT COALESCE(SUM(ms.price - ms.discount), 0) FROM memberships ms WHERE ms.memberId = m.id)
                - (SELECT COALESCE(SUM(p.amount), 0) FROM payments p
                    INNER JOIN memberships ms ON ms.id = p.membershipId
                    WHERE ms.memberId = m.id) AS amountDue
        FROM members m
    """,
)
data class MemberSummaryView(
    val memberId: Long,
    val name: String,
    val phone: String?,
    val photoPath: String?,
    val isArchived: Boolean,
    val latestEndDate: LocalDate?,
    /** Whole rupees still owed across all of the member's memberships. */
    val amountDue: Long,
)
