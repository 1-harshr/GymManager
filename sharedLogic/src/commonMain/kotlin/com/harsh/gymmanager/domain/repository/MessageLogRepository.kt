package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.messaging.MessageKind
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface MessageLogRepository {
    suspend fun record(memberId: String, kind: MessageKind)

    /** When each member was last sent each kind of message. */
    fun observeLastSent(): Flow<Map<String, Map<MessageKind, Instant>>>
}
