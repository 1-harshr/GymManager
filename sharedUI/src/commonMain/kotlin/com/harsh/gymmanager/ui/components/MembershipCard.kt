package com.harsh.gymmanager.ui.components

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.format.formatDateRange
import com.harsh.gymmanager.ui.format.formatDayMonth
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.statusLabel
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.datetime.LocalDate

/** One purchase or renewal: plan, dates, charged/paid/due, and its payments. */
@Composable
fun MembershipCard(
    membership: Membership,
    today: LocalDate,
    /** Null hides the card's own "record payment", e.g. when the page already offers it. */
    onRecordPayment: (() -> Unit)?,
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
                val discount = if (membership.discount > 0) " · " + stringResource(Res.string.discount_off, formatRupees(membership.discount)) else ""
                Text(formatDateRange(membership.startDate, membership.endDate) + discount, style = typography.caption, color = colors.inkMuted)
            }
            if (status == null) {
                Badge(stringResource(Res.string.starts_on, membership.startDate.formatDayMonth()), BadgeTone.Expired)
            } else {
                Badge(statusLabel(status, membership.endDate, today), status.tone())
            }
            MoreMenu(
                actions = listOf(MenuAction(stringResource(Res.string.delete_membership), destructive = true, onClick = onDelete)),
                contentDescription = stringResource(Res.string.more_for, membership.planName),
                container = colors.surface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            MoneyFigure(stringResource(Res.string.charged), formatRupees(membership.amountCharged), Modifier.weight(1f))
            MoneyFigure(stringResource(Res.string.paid), formatRupees(membership.amountPaid), Modifier.weight(1f))
            if (membership.amountDue > 0) {
                MoneyFigure(stringResource(Res.string.due), formatRupees(membership.amountDue), Modifier.weight(1f), colors.statusDue)
            } else {
                MoneyFigure(stringResource(Res.string.due), stringResource(Res.string.paid_in_full), Modifier.weight(1f), colors.statusActive)
            }
        }
        PaidProgress(membership)
        if (membership.payments.isNotEmpty()) {
            HorizontalDivider(color = colors.line)
            Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                membership.payments.forEach { payment ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(Res.string.delete_payment)) { onPaymentClick(payment) }.padding(vertical = AkhadaSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.payment_line, payment.paidOn.formatDayMonth(), payment.mode.label()), Modifier.weight(1f), style = typography.body, color = colors.inkMuted)
                        Text(formatRupees(payment.amount), style = typography.bodyStrong.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
        if (membership.amountDue > 0 && onRecordPayment != null) {
            GhostButton(stringResource(Res.string.record_payment), onRecordPayment, Modifier.padding(start = 0.dp))
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
