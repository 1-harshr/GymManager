package com.harsh.gymmanager.ui.platform

import androidx.compose.runtime.Composable

/** Runs [onBack] instead of leaving the screen when the system back gesture fires and [enabled] is true. */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Returns a function that opens the system file picker for a CSV or text file and calls
 * [onPicked] with its name and text, or null text if it couldn't be read.
 */
@Composable
expect fun rememberTextFilePicker(onPicked: (name: String, text: String?) -> Unit): () -> Unit
