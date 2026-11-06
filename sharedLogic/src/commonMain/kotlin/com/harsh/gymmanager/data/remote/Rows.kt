package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.membershipStatus
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.PaymentRecord
import com.harsh.gymmanager.domain.model.Plan
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Rows as Supabase sends them, and how each becomes the app's own model. Enums travel by name.

@Serializable
internal data class PlanRow(
    val id: String,
    val name: String,
    @SerialName("duration_value") val durationValue: Int,
    @SerialName("duration_unit") val durationUnit: String,
    @SerialName("default_price") val defaultPrice: Long,
    @SerialName("is_archived") val isArchived: Boolean,
) {
    fun toDomain() = Plan(id, name, durationValue, DurationUnit.valueOf(durationUnit), defaultPrice, isArchived)
}

@Serializable
internal data class MemberRow(
    val id: String,
    val name: String,
    val phone: String? = null,
    val gender: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: LocalDate? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    val notes: String? = null,
    @SerialName("joined_on") val joinedOn: LocalDate,
    @SerialName("is_archived") val isArchived: Boolean,
) {
    fun toDomain() = Member(
        id = id,
        name = name,
        phone = phone,
        gender = gender?.let(Gender::valueOf),
        dateOfBirth = dateOfBirth,
        photoPath = photoPath,
        notes = notes,
        joinedOn = joinedOn,
        isArchived = isArchived,
    )
}

/** A row of the member_summary view. */
@Serializable
internal data class MemberSummaryRow(
    @SerialName("member_id") val memberId: String,
    val name: String,
    val phone: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("latest_end_date") val latestEndDate: LocalDate? = null,
    @SerialName("amount_due") val amountDue: Long,
) {
    /** Status is worked out on the phone, as for the phone's own records, so both agree on "today". */
    fun toDomain(today: LocalDate) = MemberSummary(
        memberId = memberId,
        name = name,
        phone = phone,
        photoPath = photoPath,
        isArchived = isArchived,
        latestEndDate = latestEndDate,
        amountDue = amountDue,
        status = membershipStatus(latestEndDate, today, EXPIRING_SOON_DAYS),
    )
}

@Serializable
internal data class PaymentRow(
    val id: String,
    @SerialName("membership_id") val membershipId: String,
    val amount: Long,
    val mode: String,
    @SerialName("paid_on") val paidOn: LocalDate,
    val note: String? = null,
) {
    fun toDomain() = Payment(id, membershipId, amount, PaymentMode.valueOf(mode), paidOn, note)
}

@Serializable
internal data class MembershipRow(
    val id: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("plan_name") val planName: String,
    @SerialName("start_date") val startDate: LocalDate,
    @SerialName("end_date") val endDate: LocalDate,
    val price: Long,
    val discount: Long,
    val notes: String? = null,
    val payments: List<PaymentRow> = emptyList(),
) {
    fun toDomain() = Membership(
        id = id,
        memberId = memberId,
        planId = planId,
        planName = planName,
        startDate = startDate,
        endDate = endDate,
        price = price,
        discount = discount,
        notes = notes,
        payments = payments.sortedBy { it.paidOn }.map { it.toDomain() },
    )
}

/** A payment with who paid and for what, for the payments lists. */
@Serializable
internal data class PaymentRecordRow(
    val id: String,
    val amount: Long,
    val mode: String,
    @SerialName("paid_on") val paidOn: LocalDate,
    val memberships: MembershipOf,
) {
    @Serializable
    data class MembershipOf(
        @SerialName("plan_name") val planName: String,
        @SerialName("member_id") val memberId: String,
        val members: MemberName,
    )

    @Serializable
    data class MemberName(val name: String)

    fun toDomain() = PaymentRecord(
        paymentId = id,
        memberId = memberships.memberId,
        memberName = memberships.members.name,
        planName = memberships.planName,
        amount = amount,
        mode = PaymentMode.valueOf(mode),
        paidOn = paidOn,
    )
}

@Serializable
internal data class IdRow(val id: String)
