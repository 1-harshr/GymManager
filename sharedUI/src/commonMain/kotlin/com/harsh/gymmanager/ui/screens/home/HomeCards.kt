package com.harsh.gymmanager.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.domain.insights.CollectionPace
import com.harsh.gymmanager.domain.insights.GymInsights
import com.harsh.gymmanager.domain.insights.MonthForecast
import com.harsh.gymmanager.domain.insights.RenewalOutlook
import com.harsh.gymmanager.domain.insights.WINDOW_DAYS
import com.harsh.gymmanager.domain.model.PaymentRecord
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.PaceChart
import com.harsh.gymmanager.ui.format.formatDayMonth
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.monthName
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import gymmanager.sharedui.generated.resources.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** A few sentences on the day; [aiText] replaces the template where the phone's own model wrote one. */
@Composable
fun BriefCard(insights: GymInsights, aiText: String?) {
    val colors = MyGymTheme.colors
    MyGymCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
            Icon(MyGymIcons.Sparkles, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.brief_title), Modifier.weight(1f), style = MyGymTheme.typography.bodyStrong)
            if (aiText != null) {
                Text(
                    stringResource(Res.string.brief_ai),
                    Modifier.background(colors.accentSoft, MyGymRadius.pillShape).padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MyGymTheme.typography.caption,
                    color = colors.onAccentSoft,
                )
            }
        }
        val template = templateBrief(insights)
        Crossfade(aiText ?: template, label = "brief") { text ->
            Text(text, style = MyGymTheme.typography.body)
        }
    }
}

/** The brief without a model: how collections are going, the one most useful thing to do, and the outlook. */
@Composable
private fun templateBrief(insights: GymInsights): String {
    val pace = insights.pace
    val lastMonth = insights.today.minus(DatePeriod(months = 1)).monthName()
    val collected = formatRupees(pace.thisMonthToDate)
    val change = pace.changePercent
    val lines = mutableListOf(
        when {
            change == null -> stringResource(Res.string.brief_pace_plain, collected)
            change >= 0 -> stringResource(Res.string.brief_pace_ahead, collected, change, lastMonth)
            else -> stringResource(Res.string.brief_pace_behind, collected, abs(change), lastMonth)
        }
    )
    val renewals = insights.renewals
    val due = insights.oldestDues.firstOrNull()
    val lapsed = insights.winBack.firstOrNull()
    when {
        renewals.endingSoon.isNotEmpty() -> lines += pluralStringResource(
            Res.plurals.brief_renewals, renewals.endingSoon.size,
            renewals.endingSoon.size, formatRupees(renewals.atStake), renewals.endingSoon.first().name,
        )
        due != null -> lines += stringResource(Res.string.brief_due, due.name, formatRupees(due.amount), due.owedSince.formatDayMonth())
        lapsed != null -> lines += stringResource(Res.string.brief_win_back, lapsed.name, lapsed.endedOn.formatDayMonth())
        else -> lines += stringResource(Res.string.brief_quiet)
    }
    val forecast = insights.forecast
    if (forecast.total > forecast.collected) lines += stringResource(Res.string.brief_forecast, formatRupees(forecast.total))
    return lines.joinToString(" ")
}

/** This month's collections and how they compare with last month by the same date, with the running totals if [showChart]. */
@Composable
fun CollectionsCard(pace: CollectionPace, today: LocalDate, showChart: Boolean) {
    val colors = MyGymTheme.colors
    val thisMonth = today.monthName()
    val lastMonth = today.minus(DatePeriod(months = 1)).monthName()
    MyGymCard {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(stringResource(Res.string.collected_in, thisMonth), style = MyGymTheme.typography.caption, color = colors.inkMuted)
            Text(formatRupees(pace.thisMonthToDate), style = MyGymTheme.typography.displayXl)
            val change = pace.changePercent
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                // Up is good here, so the delta takes the "good" or "bad" status colour, always with an arrow and words.
                val (icon, tint) = when {
                    change == null -> null to colors.inkMuted
                    change > 0 -> MyGymIcons.TrendingUp to colors.statusActive
                    change < 0 -> MyGymIcons.TrendingDown to colors.statusDue
                    else -> null to colors.inkMuted
                }
                icon?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
                Text(
                    when {
                        change == null -> stringResource(Res.string.pace_no_compare, lastMonth)
                        change > 0 -> stringResource(Res.string.pace_ahead, change, lastMonth) + " " + stringResource(Res.string.pace_by_date)
                        change < 0 -> stringResource(Res.string.pace_behind, abs(change), lastMonth) + " " + stringResource(Res.string.pace_by_date)
                        else -> stringResource(Res.string.pace_level, lastMonth)
                    },
                    style = MyGymTheme.typography.caption.copy(fontWeight = MyGymTheme.typography.bodyStrong.fontWeight),
                    color = tint,
                )
            }
        }
        if (!showChart) return@MyGymCard
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY)
        PaceChart(
            thisMonth = pace.thisMonthCumulative,
            // A flat line at zero would promise a comparison there isn't.
            lastMonth = if (pace.lastMonthTotal > 0) pace.lastMonthCumulative else emptyList(),
            daysInMonth = monthStart.daysUntil(monthEnd) + 1,
            thisLabel = thisMonth,
            lastLabel = lastMonth,
            startLabel = monthStart.formatDayMonth(),
            endLabel = monthEnd.formatDayMonth(),
            description = stringResource(
                Res.string.chart_description,
                formatRupees(pace.thisMonthToDate), thisMonth, formatRupees(pace.lastMonthToDate), lastMonth,
            ),
            modifier = Modifier.padding(top = MyGymSpace.sm),
        )
    }
}

/** Where the month could land: what's in, what's owed, and what renewals would add. */
@Composable
fun ForecastCard(forecast: MonthForecast) {
    val colors = MyGymTheme.colors
    MyGymCard {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(stringResource(Res.string.forecast_title), style = MyGymTheme.typography.caption, color = colors.inkMuted)
            Text(formatRupees(forecast.total), style = MyGymTheme.typography.stat)
        }
        // Meter: the accent fills what's already collected; the rest of the track is a lighter step of the same hue.
        val share = if (forecast.total == 0L) 0f else forecast.collected.toFloat() / forecast.total
        Box(Modifier.fillMaxWidth().height(8.dp).clip(MyGymRadius.pillShape).background(colors.accentSoft)) {
            if (share > 0f) Box(Modifier.fillMaxWidth(share).height(8.dp).clip(MyGymRadius.pillShape).background(colors.accent))
        }
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            AmountLine(stringResource(Res.string.forecast_collected), forecast.collected, key = colors.accent)
            if (forecast.outstandingDues > 0) AmountLine(stringResource(Res.string.forecast_dues), forecast.outstandingDues, key = colors.accentSoft)
            if (forecast.renewalsCount > 0) {
                AmountLine(
                    pluralStringResource(Res.plurals.forecast_renewals, forecast.renewalsCount, forecast.renewalsCount),
                    forecast.renewalsValue,
                    key = colors.accentSoft,
                )
            }
        }
        Text(stringResource(Res.string.forecast_note), style = MyGymTheme.typography.caption, color = colors.inkMuted)
    }
}

@Composable
private fun AmountLine(label: String, amount: Long, key: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
        Box(Modifier.size(10.dp).background(key, MyGymRadius.pillShape))
        Text(label, Modifier.weight(1f), style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
        Text(formatRupees(amount), style = MyGymTheme.typography.bodyStrong)
    }
}

/** Who's up for renewal this week and what it's worth, with how many recent endings renewed. */
@Composable
fun RenewalsCard(renewals: RenewalOutlook, today: LocalDate, onOpenMember: (String) -> Unit) {
    val colors = MyGymTheme.colors
    MyGymCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.renewals_title), Modifier.weight(1f), style = MyGymTheme.typography.title)
            Text(
                if (renewals.endingSoon.isEmpty()) stringResource(Res.string.renewals_none)
                else pluralStringResource(Res.plurals.renewals_at_stake, renewals.endingSoon.size, renewals.endingSoon.size, formatRupees(renewals.atStake)),
                style = MyGymTheme.typography.caption,
                color = colors.inkMuted,
            )
        }
        renewals.endingSoon.take(SHOWN).forEach { member ->
            PersonLine(
                name = member.name,
                detail = when (member.endDate) {
                    today -> stringResource(Res.string.ends_today)
                    today.plus(1, DateTimeUnit.DAY) -> stringResource(Res.string.ends_tomorrow)
                    else -> stringResource(Res.string.ends_on, member.endDate.formatDayMonth()).replaceFirstChar { it.uppercase() }
                },
                amount = formatRupees(member.value),
                onClick = { onOpenMember(member.memberId) },
            )
        }
        MoreLine(renewals.endingSoon.size - SHOWN)
        HorizontalDivider(color = colors.line)
        Text(
            renewals.renewalPercent?.let {
                stringResource(Res.string.renewal_rate, renewals.renewedRecently, renewals.endedRecently, WINDOW_DAYS, it)
            } ?: stringResource(Res.string.renewal_rate_none, WINDOW_DAYS),
            style = MyGymTheme.typography.caption,
            color = colors.inkMuted,
        )
    }
}

/** Longest-unpaid dues and members worth winning back; Home only shows it when there's either. */
@Composable
fun AttentionCard(insights: GymInsights, onOpenMember: (String) -> Unit) {
    val colors = MyGymTheme.colors
    MyGymCard {
        Text(stringResource(Res.string.attention_title), style = MyGymTheme.typography.title)
        if (insights.oldestDues.isNotEmpty()) {
            SubHeading(stringResource(Res.string.oldest_dues))
            insights.oldestDues.forEach { due ->
                PersonLine(
                    name = due.name,
                    detail = pluralStringResource(Res.plurals.owed_days, due.days, due.days),
                    amount = formatRupees(due.amount),
                    amountColor = colors.statusDue,
                    onClick = { onOpenMember(due.memberId) },
                )
            }
            MoreLine(insights.overdueCount - insights.oldestDues.size)
        }
        if (insights.winBack.isNotEmpty()) {
            SubHeading(stringResource(Res.string.win_back))
            insights.winBack.forEach { lapsed ->
                PersonLine(
                    name = lapsed.name,
                    detail = pluralStringResource(Res.plurals.ended_days_ago, lapsed.daysAgo, lapsed.daysAgo) + " · " + lapsed.planName,
                    amount = formatRupees(lapsed.value),
                    onClick = { onOpenMember(lapsed.memberId) },
                )
            }
            MoreLine(insights.winBackCount - insights.winBack.size)
        }
    }
}

/** The latest payments, newest first. */
@Composable
fun RecentPaymentsCard(payments: List<PaymentRecord>, today: LocalDate, onOpenMember: (String) -> Unit) {
    val colors = MyGymTheme.colors
    MyGymCard {
        Text(stringResource(Res.string.section_recent_payments), style = MyGymTheme.typography.title)
        if (payments.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                Text(stringResource(Res.string.no_payments_title), style = MyGymTheme.typography.bodyStrong)
                Text(stringResource(Res.string.no_payments_body), style = MyGymTheme.typography.body, color = colors.inkMuted)
            }
        }
        payments.forEach { payment ->
            PersonLine(
                name = payment.memberName,
                detail = listOf(payment.planName, payment.mode.label(), relativeDay(payment.paidOn, today)).joinToString(" · "),
                amount = formatRupees(payment.amount),
                onClick = { onOpenMember(payment.memberId) },
            )
        }
    }
}

@Composable
private fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(Res.string.day_today)
    today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.day_yesterday)
    else -> date.formatDayMonth()
}

/** One person in a card: photo initials, name, a detail line and an amount; opens the member. */
@Composable
private fun PersonLine(
    name: String,
    detail: String,
    amount: String,
    onClick: () -> Unit,
    amountColor: androidx.compose.ui.graphics.Color = MyGymTheme.colors.ink,
) {
    Row(
        Modifier.fillMaxWidth().clip(MyGymRadius.fieldShape).clickable(onClick = onClick).padding(vertical = MyGymSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        Avatar(name, photoPath = null, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MyGymTheme.typography.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MyGymTheme.typography.caption, color = MyGymTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amount, style = MyGymTheme.typography.bodyStrong, color = amountColor)
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(text.uppercase(), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.inkMuted, modifier = Modifier.padding(top = MyGymSpace.xs))
}

@Composable
private fun MoreLine(extra: Int) {
    if (extra > 0) Text(stringResource(Res.string.more_count, extra), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.inkMuted)
}

private const val SHOWN = 5
