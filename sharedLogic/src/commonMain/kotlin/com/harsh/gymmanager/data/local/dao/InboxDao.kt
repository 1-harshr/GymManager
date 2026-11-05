package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.harsh.gymmanager.data.local.entity.InboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface InboxDao {
    /** Adds alerts whose event isn't in the inbox yet; returns how many were new. */
    suspend fun addNew(items: List<InboxEntity>): Int = insertIgnoringKnown(items).count { it != -1L }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringKnown(items: List<InboxEntity>): List<Long>

    @Query("SELECT * FROM inbox ORDER BY createdOn DESC, id DESC")
    fun observeAll(): Flow<List<InboxEntity>>

    @Query("SELECT COUNT(*) FROM inbox WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE inbox SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM inbox WHERE createdOn < :before")
    suspend fun deleteCreatedBefore(before: LocalDate)
}
