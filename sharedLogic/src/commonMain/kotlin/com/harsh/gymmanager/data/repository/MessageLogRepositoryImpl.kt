package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.MessageLogDao
import com.harsh.gymmanager.data.local.entity.MessageLogEntity
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.repository.MessageLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

class MessageLogRepositoryImpl(
    private val dao: MessageLogDao,
    private val clock: Clock = Clock.System,
) : MessageLogRepository {

    override suspend fun record(memberId: Long, kind: MessageKind) {
        dao.insert(MessageLogEntity(memberId = memberId, kind = kind, sentAt = clock.now()))
    }

    override fun observeLastSent(): Flow<Map<Long, Map<MessageKind, Instant>>> =
        dao.observeLastSent().map { rows ->
            rows.groupBy { it.memberId }.mapValues { (_, sends) -> sends.associate { it.kind to it.sentAt } }
        }
}
