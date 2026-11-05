package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harsh.gymmanager.domain.messaging.MessageKind
import kotlin.time.Instant

/** One message handed to WhatsApp for a member. Removed with the member. */
@Entity(
    tableName = "message_log",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memberId")],
)
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val kind: MessageKind,
    val sentAt: Instant,
)

/** The latest send of one kind to one member. */
data class LastSentRow(
    val memberId: Long,
    val kind: MessageKind,
    val sentAt: Instant,
)
