package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.CheckIn
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface AttendanceRepository {
    /** Checks the member in for today; if they already are, that check-in is returned unchanged. */
    suspend fun checkIn(memberId: String): CheckIn

    suspend fun undoCheckIn(id: String)

    /** Everyone checked in on [date], latest first. */
    fun observeCheckIns(date: LocalDate): Flow<List<CheckIn>>

    /** The member's latest [limit] visits, latest first. */
    fun observeMemberCheckIns(memberId: String, limit: Int): Flow<List<CheckIn>>

    /** Each member's latest visit; members who never came are left out. */
    fun observeLastVisits(): Flow<Map<String, LocalDate>>
}
