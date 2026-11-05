package com.harsh.gymmanager.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/** One snackbar host for the whole app, so messages survive navigating back. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("Snackbar host not provided") }
