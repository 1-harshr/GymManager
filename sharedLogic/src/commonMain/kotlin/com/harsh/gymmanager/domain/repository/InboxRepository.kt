package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.insights.InboxAlert
import com.harsh.gymmanager.domain.model.InboxItem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface InboxRepository {
    /** Newest first. */
    fun observeItems(): Flow<List<InboxItem>>

    fun observeUnreadCount(): Flow<Int>

    /** Adds the alerts not already in the inbox, dated [createdOn]; returns how many were new. */
    suspend fun addNew(alerts: List<InboxAlert>, createdOn: LocalDate): Int

    suspend fun markAllRead()

    suspend fun deleteCreatedBefore(date: LocalDate)
}
