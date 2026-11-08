package com.harsh.gymmanager.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MainRoute

@Serializable
data class MemberDetailRoute(val memberId: String)

/** Adds a member when [memberId] is null. */
@Serializable
data class MemberEditRoute(val memberId: String? = null)

@Serializable
data class SellMembershipRoute(val memberId: String)

/** Adds a plan when [planId] is null. */
@Serializable
data class PlanEditRoute(val planId: String? = null)

@Serializable
data object SettingsRoute

/** [memberIds] is comma-separated so the route stays a plain string. */
@Serializable
data class BulkRenewRoute(val memberIds: String) {
    constructor(ids: Set<String>) : this(ids.sorted().joinToString(","))

    val ids: Set<String> get() = memberIds.split(',').filter { it.isNotEmpty() }.toSet()
}

@Serializable
data object ImportMembersRoute

/** First-run setup and, later, editing the gym's name and logo. */
@Serializable
data object GymProfileRoute

/** Members due a WhatsApp reminder, with a one-tap-each run through them. */
@Serializable
data object RemindersRoute

/** Who's in today, and checking members in. */
@Serializable
data object AttendanceRoute

/** Members with a running plan who've stopped coming. */
@Serializable
data object GoneQuietRoute

/** The bell's alerts. */
@Serializable
data object InboxRoute

/** Settings → WhatsApp: connecting the gym's number and automatic messages. */
@Serializable
data object WhatsAppRoute

/** Settings → Staff: who works at the gym, and inviting more. */
@Serializable
data object StaffRoute
