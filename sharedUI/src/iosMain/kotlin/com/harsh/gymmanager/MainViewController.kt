package com.harsh.gymmanager

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import com.harsh.gymmanager.ui.notifications.NoMorningUpdates
import com.harsh.gymmanager.data.local.DEMO_DATABASE_NAME
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.harsh.gymmanager.ui.LaunchSplash
import com.harsh.gymmanager.ui.components.splashShowing
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.remote.CloudGymSettingsRepository
import com.harsh.gymmanager.data.remote.SupabaseBackend
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.settings.SETTINGS_FILE_NAME
import com.harsh.gymmanager.data.settings.createSettingsDataStore
import com.harsh.gymmanager.ui.notifications.IosMorningUpdates
import com.harsh.gymmanager.ui.platform.IosStoredFiles
import com.harsh.gymmanager.ui.platform.documentsDirectory
import platform.UIKit.UIViewController
import com.harsh.gymmanager.domain.whatsapp.InAppWhatsAppScheduler
import kotlinx.coroutines.MainScope

/** A settings file can only be open once, so the real data and demo mode share it. */
private val settingsStore by lazy { createSettingsDataStore("${documentsDirectory()}/$SETTINGS_FILE_NAME") }

/** The gym's real data. Siri and Shortcuts only ever use this. */
internal val iosDependencies: AppDependencies by lazy {
    DefaultAppDependencies(
        openDatabase = { buildGymDatabase(gymDatabaseBuilder()) },
        openSettings = { settingsStore },
        storedFiles = IosStoredFiles(),
        createMorningUpdates = { IosMorningUpdates(it) },
        onDeviceModel = InstalledOnDeviceModel,
        // iOS can't send at a set time in the background; messages go out while the app is open.
        createWhatsAppScheduler = { messaging -> InAppWhatsAppScheduler(MainScope()) { messaging().flush() } },
        wrapSettings = { CloudGymSettingsRepository(it, backend.gyms) },
    )
}

/** Sign-in and the gym's data online, for as long as the app runs. */
private val backend by lazy { SupabaseBackend(MainScope()) }

/** Sample data for showing the app, in its own database file next to the real one. */
@OptIn(ExperimentalForeignApi::class)
private val demoMode by lazy {
    DemoMode(
        createDemo = {
            DefaultAppDependencies(
                openDatabase = { buildGymDatabase(gymDatabaseBuilder(DEMO_DATABASE_NAME)) },
                openSettings = { settingsStore },
                storedFiles = IosStoredFiles(),
                createMorningUpdates = { NoMorningUpdates },
                onDeviceModel = InstalledOnDeviceModel,
                // Sample data must never message real people.
                sendsWhatsApp = false,
            )
        },
        deleteDemoFile = {
            // SQLite keeps side files next to the database, and the driver a lock file.
            listOf("", "-wal", "-shm", ".lck").forEach { NSFileManager.defaultManager.removeItemAtPath("${documentsDirectory()}/$DEMO_DATABASE_NAME$it", null) }
        },
    )
}

/** The whole app as one view controller; the Swift app shows it full screen. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    var ready by remember { mutableStateOf(false) }
    // UIKit drops the launch screen at the first frame, so a Compose copy of it carries the hold and fade.
    Box(Modifier.fillMaxSize()) {
        GymManagerApp(iosDependencies, demoMode, backend.auth, backend.gyms, onReady = { ready = true })
        LaunchSplash(appReady = ready, onGone = { splashShowing = false })
    }
}
