package com.harsh.gymmanager

import android.content.Context
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.local.gymDatabaseBuilder

/** App-wide singletons, created lazily on first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: GymDatabase by lazy { buildGymDatabase(gymDatabaseBuilder(appContext)) }
}
