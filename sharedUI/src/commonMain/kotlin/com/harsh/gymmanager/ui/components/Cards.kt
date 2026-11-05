package com.harsh.gymmanager.ui.components

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.harsh.gymmanager.ui.theme.AkhadaSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.format.durationLabel
import com.harsh.gymmanager.ui.format.formatDayMonth
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.format.statusShortLabel
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.motion.SharedKeys
import com.harsh.gymmanager.ui.motion.sharedElement
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.datetime.LocalDate

/** A plain `surface` card; contrast with `bg` does the separating, so no border or shadow. */
@Composable
fun AkhadaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(AkhadaSpace.lg), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = AkhadaRadius.cardShape, color = AkhadaTheme.colors.surface, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = AkhadaRadius.cardShape, color = AkhadaTheme.colors.surface, content = inner)
    }
}

/**
 * One member in a list: photo, name, one secondary line and their badges. In select mode a
 * ticked row turns `accent-soft` with a check in place of the photo; [onLongClick] starts selecting.
 */
@Composable
fun MemberRow(
    member: MemberSummary,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AkhadaTheme.colors
    val secondary = listOfNotNull(
        member.phone?.let(::formatPhone),
        member.latestEndDate?.let { if (it >= today) stringResource(Res.string.ends_on, it.formatDayMonth()) else stringResource(Res.string.ended_on, it.formatDayMonth()) },
    ).joinToString(" · ").ifEmpty { stringResource(Res.string.no_membership_yet) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkhadaRadius.cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(Res.string.select))
            .semantics { this.selected = selected },
        shape = AkhadaRadius.cardShape,
        color = if (selected) colors.accentSoft else colors.surface,
    ) {
        Row(
            Modifier.padding(horizontal = AkhadaSpace.lg, vertical = AkhadaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            if (selected) SelectedMark() else Avatar(member.name, member.photoPath, Modifier.sharedElement(SharedKeys.memberPhoto(member.memberId)))
            Column(Modifier.weight(1f)) {
                Text(member.name, Modifier.sharedElement(SharedKeys.memberName(member.memberId)), style = AkhadaTheme.typography.title, color = if (selected) colors.onAccentSoft else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(secondary, style = AkhadaTheme.typography.caption, color = if (selected) colors.onAccentSoft else colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                if (member.isArchived) {
                    Badge(stringResource(Res.string.archived), BadgeTone.Expired)
                } else {
                    Badge(statusShortLabel(member.status, member.latestEndDate, today), member.status.tone())
                }
                if (member.amountDue > 0) Badge(formatRupees(member.amountDue), BadgeTone.Due)
            }
        }
    }
}

/** The tick that replaces a photo on a selected row. */
@Composable
fun SelectedMark(modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Box(modifier.size(AkhadaSize.avatar).background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
        Icon(AkhadaIcons.Check, contentDescription = stringResource(Res.string.selected), Modifier.size(22.dp), tint = colors.onInk)
    }
}

/** A number with its label in the Home grid; tapping opens the matching member list. */
@Composable
fun StatTile(label: String, value: String, tone: BadgeTone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Surface(onClick = onClick, modifier = modifier, shape = AkhadaRadius.cardShape, color = colors.surface) {
        Column(Modifier.padding(AkhadaSpace.lg), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
                StatusDot(tone.colors().first)
                Text(label, style = AkhadaTheme.typography.caption, color = colors.inkMuted, maxLines = 1)
            }
            Text(value, style = AkhadaTheme.typography.stat, maxLines = 1)
        }
    }
}

/** The one hero figure on Home: money collected this month, on an ink ground. */
@Composable
fun HeroTile(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Surface(modifier = modifier.fillMaxWidth(), shape = AkhadaRadius.cardShape, color = colors.ink, contentColor = colors.onInk) {
        Column(Modifier.padding(AkhadaSpace.lg), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            Text(label, style = AkhadaTheme.typography.caption, color = colors.onInk.copy(alpha = 0.78f))
            Text(value, style = AkhadaTheme.typography.displayXl)
            Text(caption, style = AkhadaTheme.typography.caption, color = colors.onInk.copy(alpha = 0.78f))
        }
    }
}

/** A plan's name, length and price. [selected] marks the chosen plan in the sell form. */
@Composable
fun PlanCard(
    plan: Plan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AkhadaTheme.colors
    val ground = if (selected) colors.accentSoft else colors.surface
    val ink = if (selected) colors.onAccentSoft else colors.ink
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkhadaRadius.cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(Res.string.select))
            .semantics { this.selected = selected },
        shape = AkhadaRadius.cardShape,
        color = ground,
        contentColor = ink,
    ) {
        Row(
            Modifier.padding(start = AkhadaSpace.lg, end = if (trailing != null) AkhadaSpace.sm else AkhadaSpace.lg, top = AkhadaSpace.lg, bottom = AkhadaSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
                    Text(plan.name, style = AkhadaTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (selected) Icon(AkhadaIcons.Check, contentDescription = stringResource(Res.string.selected), Modifier.size(16.dp))
                    if (plan.isArchived) Badge(stringResource(Res.string.archived), BadgeTone.Expired, showDot = false)
                }
                Text(plan.durationLabel(), style = AkhadaTheme.typography.caption, color = if (selected) ink else colors.inkMuted)
            }
            Text(
                formatRupees(plan.defaultPrice),
                style = AkhadaTheme.typography.stat,
                color = if (plan.isArchived) colors.inkMuted else ink,
            )
            trailing?.invoke()
        }
    }
}
