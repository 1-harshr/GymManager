package com.harsh.gymmanager.domain.backup

import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

/** Bump when the file's shape changes; older apps refuse files newer than they understand. */
const val BACKUP_FORMAT = 1

/** Marks a file as ours, so a random JSON file is rejected instead of wiping the data. */
const val BACKUP_APP = "gym-manager"

/**
 * Everything the app stores, in one JSON file: the records, the gym's details and settings, and
 * photos and the logo inlined as Base64. Ids are kept so links between records survive.
 * Times are epoch milliseconds; dates are ISO dates.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupFile(
    // Always written and required on reading, so they identify the file rather than default in.
    @Required @EncodeDefault val app: String = BACKUP_APP,
    @Required @EncodeDefault val format: Int = BACKUP_FORMAT,
    val createdAt: Long,
    val gym: GymBackup? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val members: List<MemberBackup> = emptyList(),
    val plans: List<PlanBackup> = emptyList(),
    val memberships: List<MembershipBackup> = emptyList(),
    val payments: List<PaymentBackup> = emptyList(),
    val messages: List<MessageBackup> = emptyList(),
)

@Serializable
data class GymBackup(val name: String, val address: String? = null, val logo: String? = null)

@Serializable
data class MemberBackup(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val gender: Gender? = null,
    val dateOfBirth: LocalDate? = null,
    val notes: String? = null,
    val joinedOn: LocalDate,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** JPEG, Base64. */
    val photo: String? = null,
)

@Serializable
data class PlanBackup(
    val id: Long,
    val name: String,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    val defaultPrice: Long,
    val isArchived: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class MembershipBackup(
    val id: Long,
    val memberId: Long,
    val planId: Long? = null,
    val planName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val price: Long,
    val discount: Long = 0,
    val notes: String? = null,
    val createdAt: Long,
)

@Serializable
data class PaymentBackup(
    val id: Long,
    val membershipId: Long,
    val amount: Long,
    val mode: PaymentMode = PaymentMode.CASH,
    val paidOn: LocalDate,
    val note: String? = null,
    val createdAt: Long,
)

@Serializable
data class MessageBackup(val id: Long, val memberId: Long, val kind: MessageKind, val sentAt: Long)

/** Why a picked file can't be restored. */
enum class BackupProblem { NOT_A_BACKUP, FROM_NEWER_APP }

class BackupException(val problem: BackupProblem) : Exception(problem.name)
