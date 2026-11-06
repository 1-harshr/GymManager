package com.harsh.gymmanager.domain.model

import kotlinx.datetime.LocalDate

enum class InboxKind {
    /** A membership ends today; [InboxItem.amount] is what renewing is worth. */
    ENDS_TODAY,

    /** A membership ended on [InboxItem.date] and the member hasn't renewed. */
    LAPSED,

    /** [InboxItem.amount] has been owed since [InboxItem.date] for at least [OVERDUE_DAYS] days. */
    DUE_OVERDUE,
}

/** An alert in the bell's inbox. The screen words it from [kind], so it follows the app's language. */
data class InboxItem(
    val id: String,
    val kind: InboxKind,
    val memberId: String,
    val memberName: String,
    val amount: Long,
    val date: LocalDate,
    val createdOn: LocalDate,
    val isRead: Boolean,
)

/** A due this many days old earns an alert. */
const val OVERDUE_DAYS = 14
