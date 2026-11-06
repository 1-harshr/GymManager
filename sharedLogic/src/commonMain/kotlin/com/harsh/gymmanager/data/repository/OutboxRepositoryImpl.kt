package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.OutboxDao
import com.harsh.gymmanager.data.local.entity.OutboxEntity
import com.harsh.gymmanager.domain.whatsapp.OutboxMessage
import com.harsh.gymmanager.domain.whatsapp.OutboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class OutboxRepositoryImpl(private val dao: OutboxDao) : OutboxRepository {
    override suspend fun add(message: OutboxMessage) {
        dao.insert(message.toEntity())
    }

    override suspend fun pending(): List<OutboxMessage> = dao.pending().map { it.toDomain() }

    override suspend fun update(message: OutboxMessage) = dao.update(message.toEntity())

    override fun observeRecent(limit: Int): Flow<List<OutboxMessage>> = dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    private fun OutboxMessage.toEntity() = OutboxEntity(
        id = id,
        clientId = clientId,
        memberId = memberId,
        memberName = memberName,
        kind = kind,
        recipient = recipient,
        templateName = templateName,
        languageCode = languageCode,
        parameters = Json.encodeToString(strings, parameters),
        status = status,
        attempts = attempts,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun OutboxEntity.toDomain() = OutboxMessage(
        id = id,
        clientId = clientId,
        memberId = memberId,
        memberName = memberName,
        kind = kind,
        recipient = recipient,
        templateName = templateName,
        languageCode = languageCode,
        parameters = runCatching { Json.decodeFromString(strings, parameters) }.getOrDefault(emptyList()),
        status = status,
        attempts = attempts,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        val strings = ListSerializer(String.serializer())
    }
}
