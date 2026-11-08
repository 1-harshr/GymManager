package com.harsh.gymmanager.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.harsh.gymmanager.ui.theme.MyGymSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.format.durationLabel
import com.harsh.gymmanager.ui.format.formatDayMonth
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.format.statusShortLabel
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.SharedKeys
import com.harsh.gymmanager.ui.motion.sharedElement
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import kotlinx.datetime.LocalDate

/**
 * A plain `surface` card; contrast with `bg` does the separating, so no border or shadow. [color]
 * tints one that should stand out from its neighbours.
 */
@Composable
fun MyGymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MyGymTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(MyGymSpace.lg), verticalArrangement = Arrangement.spacedBy(MyGymSpace.md), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MyGymRadius.cardShape, color = color, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = MyGymRadius.cardShape, color = color, content = inner)
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
    val colors = MyGymTheme.colors
    val secondary = listOfNotNull(
        member.phone?.let(::formatPhone),
        member.latestEndDate?.let { if (it >= today) stringResource(Res.string.ends_on, it.formatDayMonth()) else stringResource(Res.string.ended_on, it.formatDayMonth()) },
    ).joinToString(" · ").ifEmpty { stringResource(Res.string.no_membership_yet) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MyGymRadius.cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(Res.string.select))
            .semantics { this.selected = selected },
        shape = MyGymRadius.cardShape,
        color = if (selected) colors.accentSoft else colors.surface,
    ) {
        Row(
            Modifier.padding(horizontal = MyGymSpace.lg, vertical = MyGymSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            if (selected) SelectedMark() else Avatar(member.name, member.photoPath, Modifier.sharedElement(SharedKeys.memberPhoto(member.memberId)))
            Column(Modifier.weight(1f)) {
                Text(member.name, Modifier.sharedElement(SharedKeys.memberName(member.memberId)), style = MyGymTheme.typography.title, color = if (selected) colors.onAccentSoft else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(secondary, style = MyGymTheme.typography.caption, color = if (selected) colors.onAccentSoft else colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                if (member.isArchived) {
                    Badge(stringResource(Res.string.archived), BadgeTone.Expired)
                } else {
                    MemberStatusBadge(member, today)
                }
                if (member.amountDue > 0) Badge(formatRupees(member.amountDue), BadgeTone.Due)
            }
        }
    }
}

/** The tick that replaces a photo on a selected row. */
@Composable
fun SelectedMark(modifier: Modifier = Modifier, size: Dp = MyGymSize.avatar) {
    val colors = MyGymTheme.colors
    Box(modifier.size(size).background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
        Icon(MyGymIcons.Check, contentDescription = stringResource(Res.string.selected), Modifier.size(22.dp), tint = colors.onInk)
    }
}

/**
 * One member as a tile in the members grid: photo, name, when their membership ends, and their
 * badges. Selecting works as in [MemberRow]: a ticked tile turns `accent-soft` with a check for a photo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberTile(
    member: MemberSummary,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MyGymTheme.colors
    val ends = member.latestEndDate?.let {
        if (it >= today) stringResource(Res.string.ends_on, it.formatDayMonth()) else stringResource(Res.string.ended_on, it.formatDayMonth())
    }?.replaceFirstChar { it.uppercase() } ?: stringResource(Res.string.no_membership_yet)
    Surface(
        modifier = modifier
            .clip(MyGymRadius.cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(Res.string.select))
            .semantics { this.selected = selected },
        shape = MyGymRadius.cardShape,
        color = if (selected) colors.accentSoft else colors.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MyGymSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
        ) {
            if (selected) {
                SelectedMark(size = TILE_PHOTO)
            } else {
                Avatar(member.name, member.photoPath, Modifier.sharedElement(SharedKeys.memberPhoto(member.memberId)), size = TILE_PHOTO)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    member.name,
                    Modifier.sharedElement(SharedKeys.memberName(member.memberId)),
                    style = MyGymTheme.typography.title,
                    color = if (selected) colors.onAccentSoft else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(ends, style = MyGymTheme.typography.caption, color = if (selected) colors.onAccentSoft else colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MyGymSpace.xs, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs),
            ) {
                if (member.isArchived) {
                    Badge(stringResource(Res.string.archived), BadgeTone.Expired)
                } else {
                    MemberStatusBadge(member, today)
                }
                if (member.amountDue > 0) Badge(formatRupees(member.amountDue), BadgeTone.Due)
            }
        }
    }
}

private val TILE_PHOTO = 64.dp

/** A number with its label in the Home grid. */
@Composable
fun StatTile(label: String, value: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    Surface(modifier = modifier, shape = MyGymRadius.cardShape, color = colors.surface) {
        Column(Modifier.padding(MyGymSpace.lg), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                StatusDot(tone.colors().first)
                Text(label, style = MyGymTheme.typography.caption, color = colors.inkMuted, maxLines = 1)
            }
            Text(value, style = MyGymTheme.typography.stat, maxLines = 1)
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
    val colors = MyGymTheme.colors
    val ground = if (selected) colors.accentSoft else colors.surface
    val ink = if (selected) colors.onAccentSoft else colors.ink
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MyGymRadius.cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(Res.string.select))
            .semantics { this.selected = selected },
        shape = MyGymRadius.cardShape,
        color = ground,
        contentColor = ink,
    ) {
        Row(
            Modifier.padding(start = MyGymSpace.lg, end = if (trailing != null) MyGymSpace.sm else MyGymSpace.lg, top = MyGymSpace.lg, bottom = MyGymSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                    Text(plan.name, style = MyGymTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (selected) Icon(MyGymIcons.Check, contentDescription = stringResource(Res.string.selected), Modifier.size(16.dp))
                    if (plan.isArchived) Badge(stringResource(Res.string.archived), BadgeTone.Expired, showDot = false)
                }
                Text(plan.durationLabel(), style = MyGymTheme.typography.caption, color = if (selected) ink else colors.inkMuted)
            }
            Text(
                formatRupees(plan.defaultPrice),
                style = MyGymTheme.typography.stat,
                color = if (plan.isArchived) colors.inkMuted else ink,
            )
            trailing?.invoke()
        }
    }
}
