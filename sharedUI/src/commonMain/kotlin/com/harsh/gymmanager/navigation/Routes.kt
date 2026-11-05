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
