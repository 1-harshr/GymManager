package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.domain.membershipStatus
import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.format.formatDateRange
import com.harsh.gymmanager.ui.format.formatDayMonth
import com.harsh.gymmanager.ui.format.statusLabel
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.datetime.LocalDate

fun PaymentMode.label(): String = when (this) {
    PaymentMode.CASH -> "Cash"
    PaymentMode.UPI -> "UPI"
    PaymentMode.OTHER -> "Other"
}

/** One purchase or renewal: plan, dates, charged/paid/due, and its payments. */
@Composable
fun MembershipCard(
    membership: Membership,
    today: LocalDate,
    onRecordPayment: () -> Unit,
    onDelete: () -> Unit,
    onPaymentClick: (Payment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    // A renewal that hasn't started yet has no status of its own.
    val status = if (membership.startDate > today) null else membershipStatus(membership.endDate, today, EXPIRING_SOON_DAYS)
    AkhadaCard(modifier) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            Column(Modifier.weight(1f)) {
                Text(membership.planName, style = typography.title)
                val discount = if (membership.discount > 0) " · ${formatRupees(membership.discount)} off" else ""
                Text(formatDateRange(membership.startDate, membership.endDate) + discount, style = typography.caption, color = colors.inkMuted)
            }
            if (status == null) {
                Badge("Starts ${membership.startDate.formatDayMonth()}", BadgeTone.Expired)
            } else {
                Badge(statusLabel(status, membership.endDate, today), status.tone())
            }
            MoreMenu(
                actions = listOf(MenuAction("Delete membership", destructive = true, onClick = onDelete)),
                contentDescription = "More for ${membership.planName}",
                container = colors.surface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            MoneyFigure("Charged", formatRupees(membership.amountCharged), Modifier.weight(1f))
            MoneyFigure("Paid", formatRupees(membership.amountPaid), Modifier.weight(1f))
            if (membership.amountDue > 0) {
                MoneyFigure("Due", formatRupees(membership.amountDue), Modifier.weight(1f), colors.statusDue)
            } else {
                MoneyFigure("Due", "Paid", Modifier.weight(1f), colors.statusActive)
            }
        }
        PaidProgress(membership)
        if (membership.payments.isNotEmpty()) {
            HorizontalDivider(color = colors.line)
            Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                membership.payments.forEach { payment ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Delete payment") { onPaymentClick(payment) }.padding(vertical = AkhadaSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${payment.paidOn.formatDayMonth()} · ${payment.mode.label()}", Modifier.weight(1f), style = typography.body, color = colors.inkMuted)
                        Text(formatRupees(payment.amount), style = typography.bodyStrong.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
        if (membership.amountDue > 0) {
            GhostButton("Record payment", onRecordPayment, Modifier.padding(start = 0.dp))
        }
    }
}

@Composable
private fun MoneyFigure(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = AkhadaTheme.colors.ink) {
    Column(modifier) {
        Text(label, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
        Text(value, style = AkhadaTheme.typography.stat.copy(fontSize = AkhadaTheme.typography.title.fontSize * 1.3f), color = color)
    }
}

@Composable
private fun PaidProgress(membership: Membership) {
    val colors = AkhadaTheme.colors
    val fraction = if (membership.amountCharged == 0L) 1f else (membership.amountPaid.toFloat() / membership.amountCharged).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceSunken, AkhadaRadius.pillShape)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.ink, AkhadaRadius.pillShape))
    }
}
