package com.harsh.gymmanager.data.mapper

import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MemberSummaryView
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.MembershipWithPayments
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.data.local.entity.PaymentRecordRow
import com.harsh.gymmanager.data.local.entity.PlanEntity
import com.harsh.gymmanager.domain.membershipStatus
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentRecord
import com.harsh.gymmanager.domain.model.Plan
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

fun MemberEntity.toDomain() = Member(
    id = id.toString(),
    name = name,
    phone = phone,
    gender = gender,
    dateOfBirth = dateOfBirth,
    photoPath = photoPath,
    notes = notes,
    joinedOn = joinedOn,
    isArchived = isArchived,
)

fun Member.toEntity(createdAt: Instant, updatedAt: Instant) = MemberEntity(
    id = id.toRoomId(),
    name = name.trim(),
    phone = phone?.trim()?.ifEmpty { null },
    gender = gender,
    dateOfBirth = dateOfBirth,
    photoPath = photoPath,
    notes = notes?.trim()?.ifEmpty { null },
    joinedOn = joinedOn,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MemberSummaryView.toDomain(today: LocalDate, expiringWithinDays: Int) = MemberSummary(
    memberId = memberId.toString(),
    name = name,
    phone = phone,
    photoPath = photoPath,
    isArchived = isArchived,
    latestEndDate = latestEndDate,
    amountDue = amountDue,
    status = membershipStatus(latestEndDate, today, expiringWithinDays),
)

fun PlanEntity.toDomain() = Plan(
    id = id.toString(),
    name = name,
    durationValue = durationValue,
    durationUnit = durationUnit,
    defaultPrice = defaultPrice,
    isArchived = isArchived,
)

fun Plan.toEntity(createdAt: Instant) = PlanEntity(
    id = id.toRoomId(),
    name = name.trim(),
    durationValue = durationValue,
    durationUnit = durationUnit,
    defaultPrice = defaultPrice,
    isArchived = isArchived,
    createdAt = createdAt,
)

fun PaymentEntity.toDomain() = Payment(
    id = id.toString(),
    membershipId = membershipId.toString(),
    amount = amount,
    mode = mode,
    paidOn = paidOn,
    note = note,
)

fun Payment.toEntity(createdAt: Instant) = PaymentEntity(
    id = id.toRoomId(),
    membershipId = membershipId.toRoomId(),
    amount = amount,
    mode = mode,
    paidOn = paidOn,
    note = note?.trim()?.ifEmpty { null },
    createdAt = createdAt,
)

fun MembershipWithPayments.toDomain() = Membership(
    id = membership.id.toString(),
    memberId = membership.memberId.toString(),
    planId = membership.planId?.toString(),
    planName = membership.planName,
    startDate = membership.startDate,
    endDate = membership.endDate,
    price = membership.price,
    discount = membership.discount,
    notes = membership.notes,
    payments = payments.sortedBy { it.paidOn }.map { it.toDomain() },
)

fun Membership.toEntity(createdAt: Instant) = MembershipEntity(
    id = id.toRoomId(),
    memberId = memberId.toRoomId(),
    planId = planId?.toLong(),
    planName = planName.trim(),
    startDate = startDate,
    endDate = endDate,
    price = price,
    discount = discount,
    notes = notes?.trim()?.ifEmpty { null },
    createdAt = createdAt,
)

fun PaymentRecordRow.toDomain() = PaymentRecord(
    paymentId = paymentId.toString(),
    memberId = memberId.toString(),
    memberName = memberName,
    planName = planName,
    amount = amount,
    mode = mode,
    paidOn = paidOn,
)

/** Phone records are numbered by Room; the rest of the app sees the number as text. A new record has no id yet. */
internal fun String.toRoomId(): Long = if (isEmpty()) 0 else toLong()
