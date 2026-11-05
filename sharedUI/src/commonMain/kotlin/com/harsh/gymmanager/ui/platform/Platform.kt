package com.harsh.gymmanager.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/** Runs [onBack] instead of leaving the screen when the system back gesture fires and [enabled] is true. */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Returns a function that opens the system file picker for a CSV or text file and calls
 * [onPicked] with its name and text, or null text if it couldn't be read.
 */
@Composable
expect fun rememberTextFilePicker(onPicked: (name: String, text: String?) -> Unit): () -> Unit

/**
 * Returns a function that opens the photo picker and saves the chosen image as a square logo
 * in app storage, calling [onPicked] with its path, or null if it couldn't be used.
 */
@Composable
expect fun rememberLogoPicker(onPicked: (path: String?) -> Unit): () -> Unit

/** Overrides the language used for string resources; a null tag follows the phone. */
expect object LocalAppLocale {
    @Composable
    infix fun provides(tag: String?): ProvidedValue<*>
}

/**
 * True where string resources only pick up a new language when the UI is rebuilt (iOS).
 * Android re-reads them in place, so it keeps the current screen when the language changes.
 */
expect val rebuildUiOnLanguageChange: Boolean

/** Makes the status and navigation bar icons readable on the app's own light or dark background. */
@Composable
expect fun SystemBarsFor(darkTheme: Boolean)
