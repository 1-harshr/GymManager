package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.MembershipWithPayments
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface MembershipDao {
    @Insert
    suspend fun insert(membership: MembershipEntity): Long

    @Update
    suspend fun update(membership: MembershipEntity)

    /** Also removes the membership's payments. */
    @Query("DELETE FROM memberships WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM memberships WHERE id = :id")
    suspend fun getById(id: Long): MembershipEntity?

    @Query("SELECT MAX(endDate) FROM memberships WHERE memberId = :memberId")
    suspend fun getLatestEndDate(memberId: Long): LocalDate?

    @Transaction
    @Query("SELECT * FROM memberships WHERE memberId = :memberId ORDER BY startDate DESC")
    fun observeForMember(memberId: Long): Flow<List<MembershipWithPayments>>
}
