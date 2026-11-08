package com.harsh.gymmanager.ui.format

import androidx.compose.runtime.Composable
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.importing.ImportFileProblem
import com.harsh.gymmanager.domain.repository.GymProblem
import com.harsh.gymmanager.domain.repository.SignInProblem
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.formatRupees
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A message's resource and its arguments, so it can be read in composables and in coroutines alike. */
private sealed interface Text {
    data class Plain(val res: StringResource, val args: List<Any> = emptyList()) : Text
    data class Counted(val res: PluralStringResource, val count: Int) : Text
}

private fun ValidationError.res(): StringResource = when (this) {
    ValidationError.NAME_REQUIRED -> Res.string.err_name_required
    ValidationError.PLAN_NAME_REQUIRED -> Res.string.err_plan_name_required
    ValidationError.GYM_NAME_REQUIRED -> Res.string.err_gym_name_required
    ValidationError.DURATION_TOO_SHORT -> Res.string.err_duration_too_short
    ValidationError.PRICE_NEGATIVE -> Res.string.err_price_negative
    ValidationError.DISCOUNT_TOO_LARGE -> Res.string.err_discount_too_large
    ValidationError.END_BEFORE_START -> Res.string.err_end_before_start
    ValidationError.AMOUNT_NOT_POSITIVE -> Res.string.err_amount_not_positive
}

private fun UiMessage.text(): Text = when (this) {
    is UiMessage.Invalid -> Text.Plain(error.res())
    is UiMessage.Overpayment ->
        if (amountDue == 0L) Text.Plain(Res.string.err_nothing_due) else Text.Plain(Res.string.err_overpayment, listOf(formatRupees(amountDue)))
    UiMessage.SomethingWentWrong -> Text.Plain(Res.string.err_generic)
    UiMessage.NoConnection -> Text.Plain(Res.string.err_no_connection)
    UiMessage.EnterAmount -> Text.Plain(Res.string.err_enter_amount)
    UiMessage.EnterDuration -> Text.Plain(Res.string.err_enter_duration)
    UiMessage.EnterPrice -> Text.Plain(Res.string.err_enter_price)
    UiMessage.EnterDiscount -> Text.Plain(Res.string.err_enter_discount)
    UiMessage.EnterAmountPaid -> Text.Plain(Res.string.err_enter_amount_paid)
    UiMessage.ChoosePlan -> Text.Plain(Res.string.err_choose_plan)
    UiMessage.ChooseDates -> Text.Plain(Res.string.err_choose_dates)
    is UiMessage.AmountPaidTooHigh -> Text.Plain(Res.string.err_amount_paid_too_high, listOf(formatRupees(max)))
    is UiMessage.PaymentRecorded -> Text.Plain(Res.string.msg_payment_recorded, listOf(formatRupees(amount)))
    UiMessage.PaymentDeleted -> Text.Plain(Res.string.msg_payment_deleted)
    is UiMessage.CheckedIn -> when {
        status == MembershipStatus.EXPIRED -> Text.Plain(Res.string.msg_checked_in_expired, listOf(name))
        status == MembershipStatus.NONE -> Text.Plain(Res.string.msg_checked_in_no_plan, listOf(name))
        amountDue > 0 -> Text.Plain(Res.string.msg_checked_in_due, listOf(name, formatRupees(amountDue)))
        else -> Text.Plain(Res.string.msg_checked_in, listOf(name))
    }
    is UiMessage.AlreadyCheckedIn -> Text.Plain(Res.string.msg_already_checked_in, listOf(name))
    is UiMessage.CheckInUndone -> Text.Plain(Res.string.msg_check_in_undone, listOf(name))
    UiMessage.MembershipDeleted -> Text.Plain(Res.string.msg_membership_deleted)
    UiMessage.MemberArchived -> Text.Plain(Res.string.msg_member_archived)
    UiMessage.MemberRestored -> Text.Plain(Res.string.msg_member_restored)
    UiMessage.PlanArchived -> Text.Plain(Res.string.msg_plan_archived)
    UiMessage.PlanRestored -> Text.Plain(Res.string.msg_plan_restored)
    is UiMessage.MembersArchived -> Text.Counted(Res.plurals.msg_members_archived, count)
    is UiMessage.MembersRestored -> Text.Counted(Res.plurals.msg_members_restored, count)
    is UiMessage.MembersDeleted -> Text.Counted(Res.plurals.msg_members_deleted, count)
    is UiMessage.PlansArchived -> Text.Counted(Res.plurals.msg_plans_archived, count)
    is UiMessage.PlansRestored -> Text.Counted(Res.plurals.msg_plans_restored, count)
    is UiMessage.FileUnreadable -> Text.Plain(Res.string.err_file_unreadable, listOf(fileName))
    is UiMessage.BadImportFile -> Text.Plain(
        when (problem) {
            ImportFileProblem.EMPTY -> Res.string.err_file_empty
            ImportFileProblem.NO_NAME_COLUMN -> Res.string.err_no_name_column
        },
    )
    UiMessage.EnterPhone -> Text.Plain(Res.string.err_enter_phone)
    UiMessage.EnterCode -> Text.Plain(Res.string.err_enter_code)
    is UiMessage.SignInFailed -> Text.Plain(
        when (problem) {
            SignInProblem.WRONG_CODE -> Res.string.err_signin_wrong_code
            SignInProblem.TOO_MANY_TRIES -> Res.string.err_signin_too_many
            SignInProblem.CODE_NOT_SENT -> Res.string.err_signin_not_sent
            SignInProblem.NO_CONNECTION -> Res.string.err_no_connection
            SignInProblem.IDENTITY_REJECTED -> Res.string.err_signin_identity
        },
    )
    UiMessage.EnterYourName -> Text.Plain(Res.string.err_enter_your_name)
    UiMessage.EnterInviteCode -> Text.Plain(Res.string.err_enter_invite_code)
    is UiMessage.GymFailed -> Text.Plain(
        when (problem) {
            GymProblem.ALREADY_IN_GYM -> Res.string.err_already_in_gym
            GymProblem.INVALID_INVITE -> Res.string.err_invalid_invite
            GymProblem.NOT_OWNER -> Res.string.err_not_owner
            GymProblem.HAND_OVER_FIRST -> Res.string.err_hand_over_first
            GymProblem.NO_CONNECTION -> Res.string.err_no_connection
        },
    )
    UiMessage.InviteCancelled -> Text.Plain(Res.string.msg_invite_cancelled)
    UiMessage.StaffRemoved -> Text.Plain(Res.string.msg_staff_removed)
    UiMessage.RoleChanged -> Text.Plain(Res.string.msg_role_changed)
}

/** The message in the current language, for text on screen. */
@Composable
fun UiMessage.asString(): String = when (val t = text()) {
    is Text.Plain -> stringResource(t.res, *t.args.toTypedArray())
    is Text.Counted -> pluralStringResource(t.res, t.count, t.count)
}

/** The message in the current language, for snackbars shown from a coroutine. */
suspend fun UiMessage.resolve(): String = when (val t = text()) {
    is Text.Plain -> getString(t.res, *t.args.toTypedArray())
    is Text.Counted -> getPluralString(t.res, t.count, t.count)
}
