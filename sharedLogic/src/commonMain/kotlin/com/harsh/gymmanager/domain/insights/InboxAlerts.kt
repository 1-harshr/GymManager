package com.harsh.gymmanager.domain.insights

import com.harsh.gymmanager.domain.model.InboxKind
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.OVERDUE_DAYS
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/** An alert the data calls for today. [eventKey] names the event, so it's only ever added once. */
data class InboxAlert(
    val eventKey: String,
    val kind: InboxKind,
    val memberId: String,
    val memberName: String,
    val amount: Long,
    val date: LocalDate,
)

/** How long after a membership ends it still earns a "lapsed" alert, in case the app wasn't opened that day. */
const val LAPSED_ALERT_DAYS = 7

/** [members] and [memberships] are for members who aren't archived. */
fun inboxAlerts(today: LocalDate, members: List<MemberSummary>, memberships: List<Membership>): List<InboxAlert> {
    val names = members.associate { it.memberId to it.name }
    return memberships.filter { it.memberId in names }.groupBy { it.memberId }.flatMap { (memberId, list) ->
        val name = names.getValue(memberId)
        val latest = list.maxBy { it.endDate }
        val owing = list.filter { it.amountDue > 0 }
        val owedSince = owing.minOfOrNull { it.startDate }
        listOfNotNull(
            if (latest.endDate == today) {
                InboxAlert("ends:$memberId:${latest.endDate}", InboxKind.ENDS_TODAY, memberId, name, latest.amountCharged, latest.endDate)
            } else null,
            if (latest.endDate < today && latest.endDate >= today.minus(LAPSED_ALERT_DAYS, DateTimeUnit.DAY)) {
                InboxAlert("lapsed:$memberId:${latest.endDate}", InboxKind.LAPSED, memberId, name, latest.amountCharged, latest.endDate)
            } else null,
            if (owedSince != null && owedSince.daysUntil(today) >= OVERDUE_DAYS) {
                InboxAlert("due:$memberId:$owedSince", InboxKind.DUE_OVERDUE, memberId, name, owing.sumOf { it.amountDue }, owedSince)
            } else null,
        )
    }
}
