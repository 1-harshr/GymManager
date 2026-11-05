package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.harsh.gymmanager.domain.model.DurationUnit
import kotlin.time.Clock
import kotlin.time.Instant

/** A template for new memberships. Changing a plan never alters memberships already sold. */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    /** Whole rupees. */
    val defaultPrice: Long,
    val isArchived: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
)
