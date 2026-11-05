package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.Membership
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface MembershipRepository {
    /** The member's memberships with their payments, newest first. */
    fun observeMemberships(memberId: Long): Flow<List<Membership>>

    suspend fun getLatestEndDate(memberId: Long): LocalDate?

    suspend fun addMembership(membership: Membership): Long

    suspend fun updateMembership(membership: Membership)

    /** Also removes the membership's payments. */
    suspend fun deleteMembership(id: Long)
}
