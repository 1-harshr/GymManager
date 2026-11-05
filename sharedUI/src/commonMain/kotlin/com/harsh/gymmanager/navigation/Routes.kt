package com.harsh.gymmanager.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MainRoute

@Serializable
data class MemberDetailRoute(val memberId: Long)

/** Adds a member when [memberId] is null. */
@Serializable
data class MemberEditRoute(val memberId: Long? = null)

@Serializable
data class SellMembershipRoute(val memberId: Long)

/** Adds a plan when [planId] is null. */
@Serializable
data class PlanEditRoute(val planId: Long? = null)

@Serializable
data object SettingsRoute

/** [memberIds] is comma-separated so the route stays a plain string. */
@Serializable
data class BulkRenewRoute(val memberIds: String) {
    constructor(ids: Set<Long>) : this(ids.sorted().joinToString(","))

    val ids: Set<Long> get() = memberIds.split(',').mapNotNull { it.toLongOrNull() }.toSet()
}

@Serializable
data object ImportMembersRoute

/** First-run setup and, later, editing the gym's name and logo. */
@Serializable
data object GymProfileRoute

/** Members due a WhatsApp reminder, with a one-tap-each run through them. */
@Serializable
data object RemindersRoute
