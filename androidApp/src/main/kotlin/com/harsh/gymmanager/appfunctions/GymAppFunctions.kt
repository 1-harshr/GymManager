package com.harsh.gymmanager.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionStringValueConstraint
import com.harsh.gymmanager.GymManagerApplication
import com.harsh.gymmanager.domain.assistant.GymAssistant
import com.harsh.gymmanager.domain.assistant.PaymentOutcome
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.presentation.formatRupees

/**
 * Gym actions an assistant such as Gemini can run for the owner. The compiler generates
 * [GymAppFunctionService] from this class; the manifest declares that.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(serviceName = "GymAppFunctionService", appFunctionXmlFileName = "gym_app_functions")
abstract class GymAppFunctions : AppFunctionService() {
    private val assistant: GymAssistant get() = (application as GymManagerApplication).container.assistant

    /**
     * Gets an overview of the gym: active, expiring and expired members, total dues, and money
     * collected this month.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getGymSummary(): GymSummary = assistant.summary().toGymSummary()

    /**
     * Finds gym members by name or phone number, with each one's membership status, end date and
     * amount due. Use this to look up a member before acting on them.
     *
     * @param query Part of the member's name or phone number.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun findMembers(query: String): MemberList = assistant.findMembers(query).toMemberList()

    /**
     * Lists members who need follow-up, most urgent first.
     *
     * @param status EXPIRING_SOON for memberships ending within a few days, EXPIRED for lapsed
     *   memberships, or WITH_DUES for members who owe money.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listMembersByStatus(
        @AppFunctionStringValueConstraint(enumValues = ["EXPIRING_SOON", "EXPIRED", "WITH_DUES"])
        status: String,
    ): MemberList {
        val filter = when (status) {
            "EXPIRING_SOON" -> MemberFilter.EXPIRING_SOON
            "EXPIRED" -> MemberFilter.EXPIRED
            "WITH_DUES" -> MemberFilter.WITH_DUES
            else -> throw AppFunctionInvalidArgumentException("status must be EXPIRING_SOON, EXPIRED or WITH_DUES")
        }
        return assistant.members(filter).toMemberList()
    }

    /** Lists the membership plans the gym currently sells, with duration and price. */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listPlans(): List<PlanInfo> = assistant.plans().map { it.toPlanInfo() }

    /**
     * Records money a member paid towards what they owe. Confirm the member and amount with the
     * user before calling this. Fails if the amount is more than the member owes.
     *
     * @param memberId The member's id from findMembers or listMembersByStatus.
     * @param amountRupees Whole rupees paid.
     * @param paymentMode CASH, UPI, or OTHER.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun recordPayment(
        memberId: String,
        amountRupees: Long,
        @AppFunctionStringValueConstraint(enumValues = ["CASH", "UPI", "OTHER"])
        paymentMode: String,
    ): PaymentReceipt {
        val mode = PaymentMode.entries.firstOrNull { it.name == paymentMode }
            ?: throw AppFunctionInvalidArgumentException("paymentMode must be CASH, UPI or OTHER")
        return when (val outcome = assistant.recordPayment(memberId, amountRupees, mode)) {
            is PaymentOutcome.Recorded -> PaymentReceipt(outcome.memberName, outcome.amount, outcome.remainingDue)
            is PaymentOutcome.MoreThanDue -> throw AppFunctionInvalidArgumentException(
                "${outcome.memberName} owes only ${formatRupees(outcome.amountDue)}; nothing was recorded"
            )
            PaymentOutcome.InvalidAmount -> throw AppFunctionInvalidArgumentException("amountRupees must be more than 0")
            PaymentOutcome.MemberNotFound -> throw AppFunctionElementNotFoundException("No member with id $memberId")
        }
    }
}
