package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.MemberSummary
import kotlinx.coroutines.flow.Flow

interface MemberRepository {
    /** Members matching [query] by name or phone; a blank query returns everyone. */
    fun observeMembers(query: String = "", archived: Boolean = false): Flow<List<MemberSummary>>

    /** Members whose latest membership ends between today and [withinDays] from now. */
    fun observeExpiring(withinDays: Int = EXPIRING_SOON_DAYS): Flow<List<MemberSummary>>

    fun observeExpired(): Flow<List<MemberSummary>>

    fun observeWithDues(): Flow<List<MemberSummary>>

    fun observeMember(id: Long): Flow<Member?>

    suspend fun addMember(member: Member): Long

    suspend fun updateMember(member: Member)

    suspend fun archiveMember(id: Long)

    suspend fun restoreMember(id: Long)

    /** Also removes the member's memberships and payments. Prefer [archiveMember]. */
    suspend fun deleteMemberPermanently(id: Long)
}
