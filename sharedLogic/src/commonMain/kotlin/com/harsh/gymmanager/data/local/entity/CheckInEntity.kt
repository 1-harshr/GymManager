package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A member's visit; one a day at most. Removed with the member. */
@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memberId", "checkedInOn", unique = true), Index("checkedInOn")],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val checkedInOn: LocalDate,
    val checkedInAt: Instant,
)

/** A member's latest visit. */
data class LastVisitRow(
    val memberId: Long,
    val lastVisit: LocalDate,
)
