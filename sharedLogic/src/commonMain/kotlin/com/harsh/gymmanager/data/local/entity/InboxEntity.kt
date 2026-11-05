package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harsh.gymmanager.domain.model.InboxKind
import kotlinx.datetime.LocalDate

/** One alert in the inbox. Removed with the member. */
@Entity(
    tableName = "inbox",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["eventKey"], unique = true), Index("memberId"), Index("createdOn")],
)
data class InboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Names the event, e.g. "ends:12:2026-11-18", so re-checking never adds it twice. */
    val eventKey: String,
    val kind: InboxKind,
    val memberId: Long,
    /** Kept so the alert still reads right if the member is renamed. */
    val memberName: String,
    val amount: Long,
    val date: LocalDate,
    val createdOn: LocalDate,
    val isRead: Boolean = false,
)
