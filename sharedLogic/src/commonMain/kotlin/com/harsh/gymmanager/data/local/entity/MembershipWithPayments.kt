package com.harsh.gymmanager.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MembershipWithPayments(
    @Embedded val membership: MembershipEntity,
    @Relation(parentColumn = "id", entityColumn = "membershipId")
    val payments: List<PaymentEntity>,
)
