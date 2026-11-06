package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harsh.gymmanager.domain.messaging.MessageKind
import kotlin.time.Instant

/** One message handed to WhatsApp for a member. Removed with the member. */
@Entity(
    tableName = "message_log",
    indices = [Index("memberId")],
)
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: String,
    val kind: MessageKind,
    val sentAt: Instant,
)

/** The latest send of one kind to one member. */
data class LastSentRow(
    val memberId: String,
    val kind: MessageKind,
    val sentAt: Instant,
)
