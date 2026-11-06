package com.harsh.gymmanager.appfunctions

import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionStringValueConstraint
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.Dashboard
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate

// What the assistant sees. Amounts are whole Indian rupees throughout.

/**
 * How the gym is doing right now.
 *
 * @property activeMembers Members with a running membership, including those expiring soon.
 * @property expiringSoon Members whose membership ends within the next few days.
 * @property expired Members whose last membership has ended.
 * @property membersWithDues Members who still owe money.
 * @property totalDueRupees Rupees owed across all members.
 * @property collectedThisMonthRupees Rupees collected since the 1st of this month.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class GymSummary(
    val activeMembers: Int,
    val expiringSoon: Int,
    val expired: Int,
    val membersWithDues: Int,
    val totalDueRupees: Long,
    val collectedThisMonthRupees: Long,
)

/**
 * A gym member and their membership standing.
 *
 * @property memberId Pass this to other functions to act on this member.
 * @property name The member's name.
 * @property phone The member's phone number, if saved.
 * @property status ACTIVE, EXPIRING_SOON (ends within a few days), EXPIRED, or NONE (never had a membership).
 * @property membershipEndsOn Last day of the member's latest membership, if any.
 * @property amountDueRupees Rupees the member still owes.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class MemberInfo(
    val memberId: String,
    val name: String,
    val phone: String?,
    @property:AppFunctionStringValueConstraint(enumValues = ["ACTIVE", "EXPIRING_SOON", "EXPIRED", "NONE"])
    val status: String,
    val membershipEndsOn: LocalDate?,
    val amountDueRupees: Long,
)

/**
 * Members matching a request, most urgent first.
 *
 * @property members At most ten members.
 * @property totalCount How many members matched in all, which may be more than are listed.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class MemberList(
    val members: List<MemberInfo>,
    val totalCount: Int,
)

/**
 * A membership plan the gym sells.
 *
 * @property name The plan's name.
 * @property durationValue How long the plan lasts, in [durationUnit].
 * @property durationUnit DAYS or MONTHS.
 * @property priceRupees The plan's usual price; the owner may give a discount at sale time.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class PlanInfo(
    val name: String,
    val durationValue: Int,
    @property:AppFunctionStringValueConstraint(enumValues = ["DAYS", "MONTHS"])
    val durationUnit: String,
    val priceRupees: Long,
)

/**
 * A payment that was saved.
 *
 * @property memberName Who paid.
 * @property amountRupees How much was recorded.
 * @property remainingDueRupees What the member still owes after this payment.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class PaymentReceipt(
    val memberName: String,
    val amountRupees: Long,
    val remainingDueRupees: Long,
)

internal fun Dashboard.toGymSummary() = GymSummary(
    activeMembers = activeCount,
    expiringSoon = expiringSoon.size,
    expired = expiredCount,
    membersWithDues = membersWithDuesCount,
    totalDueRupees = totalDue,
    collectedThisMonthRupees = revenueThisMonth,
)

internal fun MemberSummary.toMemberInfo() = MemberInfo(
    memberId = memberId,
    name = name,
    phone = phone,
    status = status.name,
    membershipEndsOn = latestEndDate?.toJavaLocalDate(),
    amountDueRupees = amountDue,
)

internal fun com.harsh.gymmanager.domain.assistant.MemberList.toMemberList() =
    MemberList(members.map { it.toMemberInfo() }, totalCount)

internal fun Plan.toPlanInfo() = PlanInfo(name, durationValue, durationUnit.name, defaultPrice)
