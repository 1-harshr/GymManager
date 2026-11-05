package com.harsh.gymmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = MembershipEntity::class,
            parentColumns = ["id"],
            childColumns = ["membershipId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("membershipId"), Index("paidOn")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val membershipId: Long,
    /** Whole rupees. */
    val amount: Long,
    val mode: PaymentMode = PaymentMode.CASH,
    val paidOn: LocalDate,
    val note: String? = null,
    val createdAt: Instant = Clock.System.now(),
) {
    init {
        require(amount > 0) { "amount must be positive" }
    }
}
