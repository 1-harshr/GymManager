package com.harsh.gymmanager.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.Plan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private val ShortMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val LongMonths = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val Weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "5 Nov 2026". */
fun LocalDate.formatFull(): String = "$day ${ShortMonths[month.ordinal]} $year"

/** "5 Nov". */
fun LocalDate.formatDayMonth(): String = "$day ${ShortMonths[month.ordinal]}"

/** "Thursday, 5 November". */
fun LocalDate.formatWeekday(): String = "${Weekdays[dayOfWeek.ordinal]}, $day ${LongMonths[month.ordinal]}"

/** "November". */
fun LocalDate.monthName(): String = LongMonths[month.ordinal]

/** "5 Nov – 4 Dec 2026", or both years when they differ. */
fun formatDateRange(start: LocalDate, end: LocalDate): String =
    if (start.year == end.year) "${start.formatDayMonth()} – ${end.formatFull()}" else "${start.formatFull()} – ${end.formatFull()}"

/** Indian mobile numbers as two groups of five: 98765 43210. */
fun formatPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    return if (digits.length == 10) "${digits.take(5)} ${digits.drop(5)}" else phone
}

fun Gender.label(): String = when (this) {
    Gender.MALE -> "Male"
    Gender.FEMALE -> "Female"
    Gender.OTHER -> "Other"
}

/** "1 month", "3 months", "30 days". */
fun Plan.durationLabel(): String = durationLabel(durationValue, durationUnit)

fun durationLabel(value: Int, unit: DurationUnit): String = when (unit) {
    DurationUnit.DAYS -> if (value == 1) "1 day" else "$value days"
    DurationUnit.MONTHS -> if (value == 1) "1 month" else "$value months"
}

/** Full status words for member detail: "Expires in 3 days". */
fun statusLabel(status: MembershipStatus, endDate: LocalDate?, today: LocalDate): String = when (status) {
    MembershipStatus.ACTIVE -> "Active"
    MembershipStatus.EXPIRING_SOON -> when (val days = endDate?.let { today.daysUntil(it) } ?: 0) {
        0 -> "Expires today"
        1 -> "Expires tomorrow"
        else -> "Expires in $days days"
    }
    MembershipStatus.EXPIRED -> "Expired"
    MembershipStatus.NONE -> "No plan"
}

/** Short status words for list rows: "4 days left". */
fun statusShortLabel(status: MembershipStatus, endDate: LocalDate?, today: LocalDate): String = when (status) {
    MembershipStatus.EXPIRING_SOON -> when (val days = endDate?.let { today.daysUntil(it) } ?: 0) {
        0 -> "Ends today"
        1 -> "Ends tomorrow"
        else -> "$days days left"
    }
    else -> statusLabel(status, endDate, today)
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun rememberToday(): LocalDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
