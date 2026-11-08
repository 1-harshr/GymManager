package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.harsh.gymmanager.data.local.entity.CheckInEntity
import com.harsh.gymmanager.data.local.entity.LastVisitRow
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface CheckInDao {
    /** Checks the member in on [checkIn]'s day, unless they already are; returns that day's check-in. */
    @Transaction
    suspend fun checkIn(checkIn: CheckInEntity): CheckInEntity {
        insertIfNew(checkIn)
        return getOn(checkIn.memberId, checkIn.checkedInOn)!!
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(checkIn: CheckInEntity): Long

    @Query("SELECT * FROM check_ins WHERE memberId = :memberId AND checkedInOn = :date")
    suspend fun getOn(memberId: Long, date: LocalDate): CheckInEntity?

    @Query("DELETE FROM check_ins WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM check_ins WHERE checkedInOn = :date ORDER BY checkedInAt DESC, id DESC")
    fun observeOn(date: LocalDate): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE memberId = :memberId ORDER BY checkedInOn DESC LIMIT :limit")
    fun observeForMember(memberId: Long, limit: Int): Flow<List<CheckInEntity>>

    @Query("SELECT memberId, MAX(checkedInOn) AS lastVisit FROM check_ins GROUP BY memberId")
    fun observeLastVisits(): Flow<List<LastVisitRow>>
}
