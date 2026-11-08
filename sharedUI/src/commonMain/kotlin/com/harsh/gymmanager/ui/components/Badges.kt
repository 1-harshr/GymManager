package com.harsh.gymmanager.ui.components

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.ui.format.statusShortLabel
import kotlinx.datetime.LocalDate
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymTheme

enum class BadgeTone { Active, Expiring, Expired, Due }

fun MembershipStatus.tone(): BadgeTone = when (this) {
    MembershipStatus.ACTIVE -> BadgeTone.Active
    MembershipStatus.EXPIRING_SOON -> BadgeTone.Expiring
    MembershipStatus.EXPIRED, MembershipStatus.NONE -> BadgeTone.Expired
}

@Composable
fun BadgeTone.colors(): Pair<Color, Color> {
    val c = MyGymTheme.colors
    return when (this) {
        BadgeTone.Active -> c.statusActive to c.statusActiveSoft
        BadgeTone.Expiring -> c.statusExpiring to c.statusExpiringSoft
        BadgeTone.Expired -> c.statusExpired to c.statusExpiredSoft
        BadgeTone.Due -> c.statusDue to c.statusDueSoft
    }
}

/** A one-word state pill. It always shows its word, never colour alone. */
@Composable
fun Badge(text: String, tone: BadgeTone, modifier: Modifier = Modifier, showDot: Boolean = true) {
    val (ink, ground) = tone.colors()
    Row(
        modifier.background(ground, MyGymRadius.pillShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showDot) StatusDot(ink)
        Text(text, style = MyGymTheme.typography.caption.copy(fontWeight = MyGymTheme.typography.bodyStrong.fontWeight), color = ink, maxLines = 1)
    }
}

/** A member's state in a list row: "Paused" while they're away, otherwise their membership's status. */
@Composable
fun MemberStatusBadge(member: MemberSummary, today: LocalDate, modifier: Modifier = Modifier) =
    if (member.pausedUntil != null) {
        Badge(stringResource(Res.string.paused), BadgeTone.Expiring, modifier)
    } else {
        Badge(statusShortLabel(member.status, member.latestEndDate, today), member.status.tone(), modifier)
    }

@Composable
fun DueBadge(amount: Long, modifier: Modifier = Modifier) = Badge(stringResource(Res.string.amount_due, formatRupees(amount)), BadgeTone.Due, modifier)

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(color, CircleShape))
}
