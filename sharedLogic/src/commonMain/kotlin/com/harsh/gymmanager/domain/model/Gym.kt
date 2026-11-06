package com.harsh.gymmanager.domain.model

import kotlin.time.Instant

/** Owners run the gym: plans and prices, deleting records, the gym's details and its staff. Staff do the daily work. */
enum class StaffRole { OWNER, STAFF }

/** The gym the signed-in person works at, and as what. */
data class GymMembership(
    val gymId: String,
    val gymName: String,
    val gymAddress: String?,
    val role: StaffRole,
    /** What they called themselves when they set up or joined the gym. */
    val yourName: String?,
) {
    val isOwner: Boolean get() = role == StaffRole.OWNER
}

data class StaffMember(
    val userId: String,
    val name: String?,
    val role: StaffRole,
    val joinedAt: Instant,
    /** The signed-in person. */
    val isYou: Boolean,
)

/** A one-time code the owner shares; whoever uses it first joins as [role]. */
data class StaffInvite(val code: String, val role: StaffRole, val expiresAt: Instant)
