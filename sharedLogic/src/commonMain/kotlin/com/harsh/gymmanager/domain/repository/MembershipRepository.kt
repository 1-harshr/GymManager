package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface MembershipRepository {
    /** The member's memberships with their payments, newest first. */
    fun observeMemberships(memberId: String): Flow<List<Membership>>

    /** Every membership of members who aren't archived, each with its payments, in no set order. */
    fun observeAllMemberships(): Flow<List<Membership>>

    suspend fun getLatestEndDate(memberId: String): LocalDate?

    /**
     * Saves [membership] and, if given, [firstPayment] together. The payment's membershipId is ignored.
     * @throws com.harsh.gymmanager.domain.OverpaymentException if [firstPayment] is more than the amount charged.
     */
    suspend fun addMembership(membership: Membership, firstPayment: Payment? = null): String

    /**
     * Saves several memberships, each with an optional first payment, all or nothing.
     * @throws com.harsh.gymmanager.domain.OverpaymentException if a payment is more than its membership's charge.
     */
    suspend fun addMemberships(items: List<Pair<Membership, Payment?>>): List<String>

    suspend fun updateMembership(membership: Membership)

    /** Also removes the membership's payments. */
    suspend fun deleteMembership(id: String)

    /**
     * Pauses the membership from [pausedFrom] until the member is back on [resumesOn], moving its end, and
     * any renewal queued after it, out by those days.
     */
    suspend fun pauseMembership(id: String, pausedFrom: LocalDate, resumesOn: LocalDate)

    /** Ends the membership's pause today, or cancels one still to come, giving back the days not used. */
    suspend fun resumeMembership(id: String)
}
