package com.harsh.gymmanager

import android.app.Application
import java.io.File
import kotlinx.coroutines.MainScope
import com.harsh.gymmanager.ai.GeminiNanoModel
import com.harsh.gymmanager.data.local.DEMO_DATABASE_NAME
import com.harsh.gymmanager.data.remote.SupabaseBackend
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.data.settings.SETTINGS_FILE_NAME
import com.harsh.gymmanager.data.settings.createSettingsDataStore
import com.harsh.gymmanager.ui.notifications.NoMorningUpdates
import com.harsh.gymmanager.ui.platform.AndroidStoredFiles

class GymManagerApplication : Application() {
    /** The gym's real data. Background work (morning update, assistant actions) only ever uses this. */
    lateinit var container: AppContainer
        private set

    /** Sample data for showing the app, in its own database file. */
    lateinit var demoMode: DemoMode
        private set

    /** Sign-in and the gym's data online. */
    lateinit var backend: SupabaseBackend
        private set

    private val settingsStore by lazy { createSettingsDataStore(filesDir.resolve(SETTINGS_FILE_NAME).absolutePath) }

    override fun onCreate() {
        super.onCreate()
        backend = SupabaseBackend(MainScope(), AndroidStoredFiles(this))
        val model = GeminiNanoModel()
        container = AppContainer(this, { settingsStore }, model, backend)
        demoMode = DemoMode(
            createDemo = {
                DefaultAppDependencies(
                    openDatabase = { buildGymDatabase(gymDatabaseBuilder(this, DEMO_DATABASE_NAME)) },
                    openSettings = { settingsStore },
                    createMorningUpdates = { NoMorningUpdates },
                    onDeviceModel = model,
                    // Sample data must never message real people.
                    sendsWhatsApp = false,
                )
            },
            deleteDemoFile = {
                deleteDatabase(DEMO_DATABASE_NAME)
                // The bundled SQLite driver also leaves a lock file that deleteDatabase doesn't know about.
                File(getDatabasePath(DEMO_DATABASE_NAME).path + ".lck").delete()
            },
        )
    }
}
