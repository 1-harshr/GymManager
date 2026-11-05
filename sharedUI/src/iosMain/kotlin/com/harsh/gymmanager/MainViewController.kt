package com.harsh.gymmanager

import androidx.compose.ui.window.ComposeUIViewController
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
fun MainViewController(): UIViewController = ComposeUIViewController { App(iosDependencies) }
