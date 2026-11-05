package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** One purchase or renewal. [startDate] and [endDate] are both inclusive. */
@Entity(
    tableName = "memberships",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("memberId"), Index("planId"), Index("endDate")],
)
data class MembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val planId: Long?,
    /** Copied from the plan at sale time so renaming a plan doesn't rewrite history. */
    val planName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Whole rupees. */
    val price: Long,
    /** Whole rupees off [price]. */
    val discount: Long = 0,
    val notes: String? = null,
    val createdAt: Instant = Clock.System.now(),
) {
    init {
        require(price >= 0) { "price must not be negative" }
        require(discount in 0..price) { "discount must be between 0 and price" }
        require(endDate >= startDate) { "endDate must not be before startDate" }
    }

    val amountCharged: Long get() = price - discount
}
