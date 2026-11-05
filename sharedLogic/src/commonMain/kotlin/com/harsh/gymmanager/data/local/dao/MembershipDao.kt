package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.MembershipWithPayments
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface MembershipDao {
    @Insert
    suspend fun insert(membership: MembershipEntity): Long

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    /** Saves a membership and, optionally, its first payment in one transaction. */
    @Transaction
    suspend fun insertWithFirstPayment(membership: MembershipEntity, firstPayment: PaymentEntity?): Long {
        val id = insert(membership)
        if (firstPayment != null) insertPayment(firstPayment.copy(membershipId = id))
        return id
    }

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
