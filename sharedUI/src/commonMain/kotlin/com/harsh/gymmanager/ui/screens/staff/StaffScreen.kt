package com.harsh.gymmanager.ui.screens.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.domain.model.StaffInvite
import com.harsh.gymmanager.domain.model.StaffMember
import com.harsh.gymmanager.domain.model.StaffRole
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.presentation.staff.StaffViewModel
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.LoadingMark
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.ConfirmSheet
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.MyGymBottomSheet
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.formatFull
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * Who can open the gym. The owner invites people with a one-time code, changes roles and removes
 * people; staff see the list and can leave.
 */
@Composable
fun StaffScreen(gyms: GymRepository, onBack: () -> Unit) {
    val vm = viewModel { StaffViewModel(gyms) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val gymName = LocalGym.current.name
    var acting by remember { mutableStateOf<StaffMember?>(null) }
    var removing by remember { mutableStateOf<StaffMember?>(null) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.resolve()) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader(stringResource(Res.string.staff_title), onBack, subtitle = stringResource(Res.string.staff_subtitle, gymName))
        Column(
            Modifier.screenGutter().navigationBarsPadding().padding(bottom = MyGymSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            if (state.isOwner) {
                MyGymButton(
                    stringResource(Res.string.invite_staff),
                    { vm.invite() },
                    Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                    icon = MyGymIcons.Plus,
                )
            }

            if (state.isLoading && state.loadError == null) LoadingMark(Modifier.padding(vertical = MyGymSpace.xxl))

            state.loadError?.let { error ->
                MyGymCard {
                    Text(stringResource(Res.string.staff_load_failed), style = MyGymTheme.typography.bodyStrong)
                    Text(error.asString(), style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
                    MyGymButton(stringResource(Res.string.try_again), vm::load, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            }

            if (state.staff.isNotEmpty()) {
                MyGymCard {
                    state.staff.forEach { person ->
                        // The owner can act on anyone but themselves.
                        val canAct = state.isOwner && !person.isYou
                        StaffRow(person, onClick = if (canAct) ({ acting = person }) else null)
                    }
                }
            }

            if (state.isOwner && state.invites.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.pending_invites), Modifier.padding(top = MyGymSpace.md))
                MyGymCard {
                    state.invites.forEach { invite -> InviteRow(invite, onCancel = { vm.cancelInvite(invite.code) }) }
                }
            }

            if (!state.isOwner && !state.isLoading && state.loadError == null) {
                MyGymButton(
                    stringResource(Res.string.leave_gym),
                    { leaving = true },
                    Modifier.fillMaxWidth().padding(top = MyGymSpace.lg),
                    kind = ButtonKind.Danger,
                )
            }
        }
    }

    state.newInvite?.let { invite -> InviteSheet(invite, gymName, onDismiss = vm::dismissNewInvite) }

    acting?.let { person ->
        MyGymBottomSheet(onDismiss = { acting = null }) {
            Text(person.displayName(), style = MyGymTheme.typography.title)
            val (label, role) = if (person.role == StaffRole.OWNER) {
                Res.string.make_staff to StaffRole.STAFF
            } else {
                Res.string.make_owner to StaffRole.OWNER
            }
            MyGymButton(stringResource(label), { vm.setRole(person.userId, role); acting = null }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            MyGymButton(
                stringResource(Res.string.remove_from_gym),
                { removing = person; acting = null },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Danger,
            )
        }
    }

    removing?.let { person ->
        ConfirmSheet(
            title = stringResource(Res.string.remove_staff_title, person.displayName()),
            body = stringResource(Res.string.remove_staff_body),
            confirmLabel = stringResource(Res.string.remove_from_gym),
            onConfirm = { vm.remove(person.userId); removing = null },
            onDismiss = { removing = null },
        )
    }

    if (leaving) {
        ConfirmSheet(
            title = stringResource(Res.string.leave_gym_title, gymName),
            body = stringResource(Res.string.leave_gym_body),
            confirmLabel = stringResource(Res.string.leave_gym),
            onConfirm = { vm.leave(); leaving = false },
            onDismiss = { leaving = false },
        )
    }
}

@Composable
private fun StaffRow(person: StaffMember, onClick: (() -> Unit)?) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    val row: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            Avatar(person.displayName(), photoPath = null)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                    Text(person.displayName(), style = typography.bodyStrong)
                    if (person.isYou) Text(stringResource(Res.string.staff_you), style = typography.caption, color = colors.inkMuted)
                }
                Text(
                    stringResource(Res.string.staff_joined, person.joinedAt.localDate().formatFull()),
                    style = typography.caption,
                    color = colors.inkMuted,
                )
            }
            Badge(
                stringResource(if (person.role == StaffRole.OWNER) Res.string.role_owner else Res.string.role_staff),
                if (person.role == StaffRole.OWNER) BadgeTone.Active else BadgeTone.Expired,
                showDot = false,
            )
        }
    }
    if (onClick != null) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) { row() }
    } else {
        row()
    }
}

@Composable
private fun InviteRow(invite: StaffInvite, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
        Column(Modifier.weight(1f)) {
            Text(invite.code.grouped(), style = MyGymTheme.typography.stat)
            Text(
                stringResource(Res.string.invite_expires, invite.expiresAt.localDate().formatFull()),
                style = MyGymTheme.typography.caption,
                color = MyGymTheme.colors.inkMuted,
            )
        }
        GhostButton(stringResource(Res.string.cancel_invite), onCancel, color = MyGymTheme.colors.statusDue)
    }
}

/** The new code, big enough to read out, with ways to send it. */
@Composable
private fun InviteSheet(invite: StaffInvite, gymName: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION") // LocalClipboard needs a platform ClipEntry; plain text is all this needs.
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(Res.string.invite_copied)
    val message = stringResource(Res.string.invite_message, gymName, invite.code.grouped())

    MyGymBottomSheet(onDismiss) {
        Text(stringResource(Res.string.invite_title), style = MyGymTheme.typography.title)
        Text(
            invite.code.grouped(),
            Modifier.fillMaxWidth().padding(vertical = MyGymSpace.md),
            style = MyGymTheme.typography.displayXl,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.invite_body, gymName, invite.expiresAt.localDate().formatFull()),
            style = MyGymTheme.typography.body,
            color = MyGymTheme.colors.inkMuted,
        )
        MyGymButton(
            stringResource(Res.string.invite_share),
            { runCatching { uriHandler.openUri("https://wa.me/?text=${message.urlEncoded()}") } },
            Modifier.fillMaxWidth(),
            icon = MyGymIcons.MessageCircle,
        )
        MyGymButton(
            stringResource(Res.string.invite_copy),
            {
                clipboard.setText(AnnotatedString(invite.code))
                scope.launch { snackbar.showSnackbar(copied) }
            },
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Secondary,
        )
        MyGymButton(stringResource(Res.string.done), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

@Composable
private fun StaffMember.displayName(): String = name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.staff_unnamed)

private fun Instant.localDate() = toLocalDateTime(TimeZone.currentSystemDefault()).date

/** "K7P4-QXM2", as people read it out. */
private fun String.grouped(): String = if (length > 4) "${take(4)}-${drop(4)}" else this

/** Percent-encodes text for a URL query, byte by byte in UTF-8. */
private fun String.urlEncoded(): String = buildString {
    for (byte in this@urlEncoded.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") {
            append(c)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
