package com.harsh.gymmanager.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun gymDatabaseBuilder(context: Context, name: String = DATABASE_NAME): RoomDatabase.Builder<GymDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(name)
    return Room.databaseBuilder<GymDatabase>(context = appContext, name = dbFile.absolutePath)
}
