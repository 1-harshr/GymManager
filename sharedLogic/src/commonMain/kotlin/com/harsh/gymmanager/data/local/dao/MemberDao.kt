package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MemberSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Dao
interface MemberDao {
    @Insert
    suspend fun insert(member: MemberEntity): Long

    @Update
    suspend fun update(member: MemberEntity)

    /** Permanently removes the member along with their memberships and payments. Prefer [setArchived]. */
    @Delete
    suspend fun delete(member: MemberEntity)

    @Query("UPDATE members SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Instant)

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeById(id: Long): Flow<MemberEntity?>

    @Query(
        """
        SELECT * FROM member_summary
        WHERE isArchived = :archived
            AND (:query = '' OR name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeSummaries(query: String = "", archived: Boolean = false): Flow<List<MemberSummary>>

    @Query(
        """
        SELECT * FROM member_summary
        WHERE isArchived = 0 AND latestEndDate BETWEEN :today AND :until
        ORDER BY latestEndDate
        """
    )
    fun observeExpiring(today: LocalDate, until: LocalDate): Flow<List<MemberSummary>>

    @Query(
        """
        SELECT * FROM member_summary
        WHERE isArchived = 0 AND latestEndDate < :today
        ORDER BY latestEndDate DESC
        """
    )
    fun observeExpired(today: LocalDate): Flow<List<MemberSummary>>

    @Query("SELECT * FROM member_summary WHERE isArchived = 0 AND amountDue > 0 ORDER BY amountDue DESC")
    fun observeWithDues(): Flow<List<MemberSummary>>
}
