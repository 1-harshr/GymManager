package com.harsh.gymmanager.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.StaffRole
import com.harsh.gymmanager.presentation.messaging.MessageRequest

/** One snackbar host for the whole app, so messages survive navigating back. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("Snackbar host not provided") }

/** The language code in use ("en", "hi"), so formatting recomposes when the person switches language. */
val LocalLanguage = staticCompositionLocalOf { "en" }

/** The gym the app is set up for. Screens behind setup can rely on it being present. */
val LocalGym = staticCompositionLocalOf { GymProfile(name = "Gym") }

/** Offers a WhatsApp message in the app-wide sheet; members without a mobile number are skipped. */
val LocalWhatsApp = staticCompositionLocalOf<(MessageRequest) -> Unit> { {} }

/** The signed-in person's role at the gym; null without an account (demo mode), where everything is open. */
val LocalGymRole = staticCompositionLocalOf<StaffRole?> { null }

/** True unless the person is staff, who don't see the owner's tools: plans, deletes, revenue and the gym's setup. */
val isOwnerOrNoAccount: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalGymRole.current != StaffRole.STAFF
