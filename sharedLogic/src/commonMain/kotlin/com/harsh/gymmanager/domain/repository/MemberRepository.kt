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

    /** Adds all of [members] in one transaction; returns their ids. */
    suspend fun addMembers(members: List<Member>): List<Long>

    suspend fun getMembers(ids: List<Long>): List<Member>

    /** Every stored phone number, as typed. */
    suspend fun getPhones(): List<String>

    suspend fun updateMember(member: Member)

    suspend fun archiveMember(id: Long)

    suspend fun restoreMember(id: Long)

    /** Also removes the member's memberships and payments. Prefer [archiveMember]. */
    suspend fun deleteMemberPermanently(id: Long)

    /** Returns how many members changed. */
    suspend fun archiveMembers(ids: List<Long>): Int

    suspend fun restoreMembers(ids: List<Long>): Int

    /** Also removes their memberships and payments; returns how many were deleted. */
    suspend fun deleteMembersPermanently(ids: List<Long>): Int
}
