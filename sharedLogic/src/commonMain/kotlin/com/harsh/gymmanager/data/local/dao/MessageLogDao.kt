package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.harsh.gymmanager.data.local.entity.LastSentRow
import com.harsh.gymmanager.data.local.entity.MessageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageLogDao {
    @Insert
    suspend fun insert(entry: MessageLogEntity): Long

    @Query("SELECT memberId, kind, MAX(sentAt) AS sentAt FROM message_log GROUP BY memberId, kind")
    fun observeLastSent(): Flow<List<LastSentRow>>
}
