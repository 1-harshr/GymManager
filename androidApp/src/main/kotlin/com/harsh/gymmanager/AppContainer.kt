package com.harsh.gymmanager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.remote.CloudGymSettingsRepository
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.data.local.gymDatabaseBuilder
import com.harsh.gymmanager.domain.insights.OnDeviceModel
import com.harsh.gymmanager.notifications.AndroidMorningUpdates
import com.harsh.gymmanager.ui.platform.AndroidStoredFiles
import com.harsh.gymmanager.whatsapp.AndroidWhatsAppScheduler

/**
 * Android's app-wide singletons on the gym's real data, in the app's private storage. [settings]
 * is shared with demo mode, since a settings file can only be open once.
 */
class AppContainer(
    context: Context,
    settings: () -> DataStore<Preferences>,
    onDeviceModel: OnDeviceModel,
    gyms: GymRepository,
) : AppDependencies by DefaultAppDependencies(
    openDatabase = { buildGymDatabase(gymDatabaseBuilder(context.applicationContext)) },
    openSettings = settings,
    storedFiles = AndroidStoredFiles(context.applicationContext),
    createMorningUpdates = { AndroidMorningUpdates(context.applicationContext) },
    onDeviceModel = onDeviceModel,
    createWhatsAppScheduler = { AndroidWhatsAppScheduler(context.applicationContext) },
    wrapSettings = { CloudGymSettingsRepository(it, gyms) },
)
