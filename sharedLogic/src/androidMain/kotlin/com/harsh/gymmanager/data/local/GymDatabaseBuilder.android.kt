package com.harsh.gymmanager.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun gymDatabaseBuilder(context: Context): RoomDatabase.Builder<GymDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_NAME)
    return Room.databaseBuilder<GymDatabase>(context = appContext, name = dbFile.absolutePath)
}
