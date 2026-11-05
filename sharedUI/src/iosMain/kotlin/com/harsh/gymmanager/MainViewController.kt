package com.harsh.gymmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.harsh.gymmanager.ui.LaunchSplash
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.settings.SETTINGS_FILE_NAME
import com.harsh.gymmanager.data.settings.createSettingsDataStore
import com.harsh.gymmanager.ui.platform.documentsDirectory
import platform.UIKit.UIViewController

private val iosDependencies: AppDependencies by lazy {
    DefaultAppDependencies(
        openDatabase = { buildGymDatabase(gymDatabaseBuilder()) },
        openSettings = { createSettingsDataStore("${documentsDirectory()}/$SETTINGS_FILE_NAME") },
    )
}

/** The whole app as one view controller; the Swift app shows it full screen. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    var ready by remember { mutableStateOf(false) }
    // UIKit drops the launch screen at the first frame, so a Compose copy of it carries the hold and fade.
    Box(Modifier.fillMaxSize()) {
        App(iosDependencies, onReady = { ready = true })
        LaunchSplash(appReady = ready)
    }
}
