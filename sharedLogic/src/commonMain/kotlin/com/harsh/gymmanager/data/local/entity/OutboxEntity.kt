package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.whatsapp.OutboxStatus
import kotlin.time.Instant

/** One WhatsApp message queued for the Make.com webhook. Removed with the member. */
@Entity(
    tableName = "whatsapp_outbox",
    indices = [Index(value = ["clientId"], unique = true), Index("memberId"), Index("status")],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String,
    val memberId: String,
    /** Kept so the list still reads right if the member is renamed. */
    val memberName: String,
    val kind: MessageKind,
    val recipient: String,
    val templateName: String,
    val languageCode: String,
    /** The template's variables as a JSON array of strings. */
    val parameters: String,
    val status: OutboxStatus,
    val attempts: Int,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
