package com.harsh.gymmanager.ui.notifications

import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.digest.MorningDigest
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.presentation.formatRupees
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.morning_collected
import gymmanager.sharedui.generated.resources.morning_dues
import gymmanager.sharedui.generated.resources.morning_ended_yesterday
import gymmanager.sharedui.generated.resources.morning_ending_soon
import gymmanager.sharedui.generated.resources.morning_ending_today
import gymmanager.sharedui.generated.resources.morning_more_names
import gymmanager.sharedui.generated.resources.morning_no_dues
import gymmanager.sharedui.generated.resources.morning_title
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Keeps the phone's morning-update notification in line with the settings and the gym's data. */
interface MorningUpdateScheduler {
    /** Called on launch and whenever settings or data change while the app is open. */
    suspend fun sync(settings: AppSettings)
}

data class NotificationText(val title: String, val body: String)

/** The notification in the current string-resource language, one fact per line. */
suspend fun MorningDigest.notificationText(gymName: String): NotificationText {
    val lines = buildList {
        if (endingToday.isNotEmpty()) add(getString(Res.string.morning_ending_today, names(endingToday)))
        if (endingSoonCount > 0) {
            add(getPluralString(Res.plurals.morning_ending_soon, endingSoonCount, endingSoonCount, EXPIRING_SOON_DAYS))
        }
        if (endedYesterday.isNotEmpty()) add(getString(Res.string.morning_ended_yesterday, names(endedYesterday)))
        add(
            if (membersWithDuesCount > 0) {
                getPluralString(Res.plurals.morning_dues, membersWithDuesCount, formatRupees(totalDue), membersWithDuesCount)
            } else {
                getString(Res.string.morning_no_dues)
            }
        )
        add(getString(Res.string.morning_collected, formatRupees(collectedThisMonth)))
    }
    return NotificationText(getString(Res.string.morning_title, gymName), lines.joinToString("\n"))
}

/** "Ravi, Priya, Amit and 2 more"; a notification only has room for a few names. */
private suspend fun names(all: List<String>): String {
    val shown = all.take(MAX_NAMES).joinToString(", ")
    return if (all.size > MAX_NAMES) getString(Res.string.morning_more_names, shown, all.size - MAX_NAMES) else shown
}

private const val MAX_NAMES = 3
