package com.harsh.gymmanager

import android.content.Context
import com.harsh.gymmanager.ai.GeminiNanoModel
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.settings.SETTINGS_FILE_NAME
import com.harsh.gymmanager.data.settings.createSettingsDataStore
import com.harsh.gymmanager.notifications.AndroidMorningUpdates
import com.harsh.gymmanager.ui.platform.AndroidStoredFiles

/** Android's app-wide singletons: the shared container, opened in the app's private storage. */
class AppContainer(context: Context) : AppDependencies by DefaultAppDependencies(
    openDatabase = { buildGymDatabase(gymDatabaseBuilder(context.applicationContext)) },
    openSettings = { createSettingsDataStore(context.applicationContext.filesDir.resolve(SETTINGS_FILE_NAME).absolutePath) },
    storedFiles = AndroidStoredFiles(context.applicationContext),
    createMorningUpdates = { AndroidMorningUpdates(context.applicationContext) },
    onDeviceModel = GeminiNanoModel(),
)
