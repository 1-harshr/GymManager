package com.harsh.gymmanager.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.ui.LocalLanguage
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.date_range_same_year
import gymmanager.sharedui.generated.resources.days_left
import gymmanager.sharedui.generated.resources.duration_days
import gymmanager.sharedui.generated.resources.duration_months
import gymmanager.sharedui.generated.resources.ends_today
import gymmanager.sharedui.generated.resources.ends_tomorrow
import gymmanager.sharedui.generated.resources.expires_in_days
import gymmanager.sharedui.generated.resources.expires_today
import gymmanager.sharedui.generated.resources.expires_tomorrow
import gymmanager.sharedui.generated.resources.gender_female
import gymmanager.sharedui.generated.resources.gender_male
import gymmanager.sharedui.generated.resources.gender_other
import gymmanager.sharedui.generated.resources.mode_cash
import gymmanager.sharedui.generated.resources.mode_other
import gymmanager.sharedui.generated.resources.mode_upi
import gymmanager.sharedui.generated.resources.status_active
import gymmanager.sharedui.generated.resources.status_expired
import gymmanager.sharedui.generated.resources.status_no_plan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Month and weekday names for one language; digits stay Latin in both, as Indian apps write them. */
private class DateNames(val shortMonths: List<String>, val longMonths: List<String>, val weekdays: List<String>)

private val English = DateNames(
    shortMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    longMonths = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
    weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
)

private val HindiMonths = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")

private val Hindi = DateNames(
    shortMonths = HindiMonths,
    longMonths = HindiMonths,
    weekdays = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार"),
)

private val names: DateNames
    @Composable @ReadOnlyComposable get() = if (LocalLanguage.current == "hi") Hindi else English

/** "5 Nov 2026" / "5 नवंबर 2026". */
@Composable @ReadOnlyComposable
fun LocalDate.formatFull(): String = "$day ${names.shortMonths[month.ordinal]} $year"

/** "5 Nov". */
@Composable @ReadOnlyComposable
fun LocalDate.formatDayMonth(): String = "$day ${names.shortMonths[month.ordinal]}"

/** "Thursday, 5 November". */
@Composable @ReadOnlyComposable
fun LocalDate.formatWeekday(): String = "${names.weekdays[dayOfWeek.ordinal]}, $day ${names.longMonths[month.ordinal]}"

/** "November". */
@Composable @ReadOnlyComposable
fun LocalDate.monthName(): String = names.longMonths[month.ordinal]

/** "5 Nov – 4 Dec 2026", or both years when they differ. */
@Composable
fun formatDateRange(start: LocalDate, end: LocalDate): String =
    stringResource(
        Res.string.date_range_same_year,
        if (start.year == end.year) start.formatDayMonth() else start.formatFull(),
        end.formatFull(),
    )

/** Indian mobile numbers as two groups of five: 98765 43210. */
fun formatPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    return if (digits.length == 10) "${digits.take(5)} ${digits.drop(5)}" else phone
}

@Composable
fun Gender.label(): String = stringResource(
    when (this) {
        Gender.MALE -> Res.string.gender_male
        Gender.FEMALE -> Res.string.gender_female
        Gender.OTHER -> Res.string.gender_other
    },
)

@Composable
fun PaymentMode.label(): String = stringResource(
    when (this) {
        PaymentMode.CASH -> Res.string.mode_cash
        PaymentMode.UPI -> Res.string.mode_upi
        PaymentMode.OTHER -> Res.string.mode_other
    },
)

/** "1 month", "3 months", "30 days". */
@Composable
fun Plan.durationLabel(): String = durationLabel(durationValue, durationUnit)

@Composable
fun durationLabel(value: Int, unit: DurationUnit): String = when (unit) {
    DurationUnit.DAYS -> pluralStringResource(Res.plurals.duration_days, value, value)
    DurationUnit.MONTHS -> pluralStringResource(Res.plurals.duration_months, value, value)
}

/** Full status words for member detail: "Expires in 3 days". */
@Composable
fun statusLabel(status: MembershipStatus, endDate: LocalDate?, today: LocalDate): String = when (status) {
    MembershipStatus.ACTIVE -> stringResource(Res.string.status_active)
    MembershipStatus.EXPIRING_SOON -> when (val days = endDate?.let { today.daysUntil(it) } ?: 0) {
        0 -> stringResource(Res.string.expires_today)
        1 -> stringResource(Res.string.expires_tomorrow)
        else -> pluralStringResource(Res.plurals.expires_in_days, days, days)
    }
    MembershipStatus.EXPIRED -> stringResource(Res.string.status_expired)
    MembershipStatus.NONE -> stringResource(Res.string.status_no_plan)
}

/** Short status words for list rows: "4 days left". */
@Composable
fun statusShortLabel(status: MembershipStatus, endDate: LocalDate?, today: LocalDate): String = when (status) {
    MembershipStatus.EXPIRING_SOON -> when (val days = endDate?.let { today.daysUntil(it) } ?: 0) {
        0 -> stringResource(Res.string.ends_today)
        1 -> stringResource(Res.string.ends_tomorrow)
        else -> pluralStringResource(Res.plurals.days_left, days, days)
    }
    else -> statusLabel(status, endDate, today)
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun rememberToday(): LocalDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
