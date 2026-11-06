package com.harsh.gymmanager.presentation

import com.harsh.gymmanager.domain.OverpaymentException
import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ValidationException
import com.harsh.gymmanager.domain.backup.BackupException
import com.harsh.gymmanager.domain.backup.BackupProblem
import com.harsh.gymmanager.domain.importing.ImportFileException
import com.harsh.gymmanager.domain.importing.ImportFileProblem
import com.harsh.gymmanager.domain.repository.GymException
import com.harsh.gymmanager.domain.repository.GymProblem
import com.harsh.gymmanager.domain.repository.SignInException
import com.harsh.gymmanager.domain.repository.SignInProblem

/**
 * Something to tell the person: a field error, a failed save or a confirmation. ViewModels
 * emit these instead of sentences so the UI can say them in the chosen language.
 */
sealed interface UiMessage {
    data class Invalid(val error: ValidationError) : UiMessage
    /** [amountDue] is whole rupees; zero means nothing is due. */
    data class Overpayment(val amountDue: Long) : UiMessage
    data object SomethingWentWrong : UiMessage

    // Form fields
    data object EnterAmount : UiMessage
    data object EnterDuration : UiMessage
    data object EnterPrice : UiMessage
    data object EnterDiscount : UiMessage
    data object EnterAmountPaid : UiMessage
    data object ChoosePlan : UiMessage
    data object ChooseDates : UiMessage
    data class AmountPaidTooHigh(val max: Long) : UiMessage

    // Confirmations
    data class PaymentRecorded(val amount: Long) : UiMessage
    data object PaymentDeleted : UiMessage
    data object MembershipDeleted : UiMessage
    data object MemberArchived : UiMessage
    data object MemberRestored : UiMessage
    data object PlanArchived : UiMessage
    data object PlanRestored : UiMessage
    data class MembersArchived(val count: Int) : UiMessage
    data class MembersRestored(val count: Int) : UiMessage
    data class MembersDeleted(val count: Int) : UiMessage
    data class PlansArchived(val count: Int) : UiMessage
    data class PlansRestored(val count: Int) : UiMessage

    // Import
    data class FileUnreadable(val fileName: String) : UiMessage
    data class BadImportFile(val problem: ImportFileProblem) : UiMessage

    // Backup
    data object BackupSaved : UiMessage
    data object BackupRestored : UiMessage
    data class BadBackup(val problem: BackupProblem) : UiMessage

    // Sign-in
    data object EnterPhone : UiMessage
    data object EnterCode : UiMessage
    data class SignInFailed(val problem: SignInProblem) : UiMessage

    // Gym and staff
    data object EnterYourName : UiMessage
    data object EnterInviteCode : UiMessage
    data class GymFailed(val problem: GymProblem) : UiMessage
    data object InviteCancelled : UiMessage
    data object StaffRemoved : UiMessage
    data object RoleChanged : UiMessage
}

/** Known failures become their own message; anything unexpected gets a generic one. */
fun Throwable.toUiMessage(): UiMessage = when (this) {
    is OverpaymentException -> UiMessage.Overpayment(amountDue)
    is ValidationException -> UiMessage.Invalid(error)
    is ImportFileException -> UiMessage.BadImportFile(problem)
    is BackupException -> UiMessage.BadBackup(problem)
    is SignInException -> UiMessage.SignInFailed(problem)
    is GymException -> UiMessage.GymFailed(problem)
    else -> UiMessage.SomethingWentWrong
}
