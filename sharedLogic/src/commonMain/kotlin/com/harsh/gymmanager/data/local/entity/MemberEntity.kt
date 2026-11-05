package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    tableName = "members",
    indices = [Index("name"), Index("phone")],
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val gender: Gender? = null,
    val dateOfBirth: LocalDate? = null,
    /** Path to the photo file in app storage; the image itself is never stored in the database. */
    val photoPath: String? = null,
    val notes: String? = null,
    val joinedOn: LocalDate,
    val isArchived: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
)
