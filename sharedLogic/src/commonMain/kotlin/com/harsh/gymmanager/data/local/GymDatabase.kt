package com.harsh.gymmanager.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.harsh.gymmanager.data.local.dao.MemberDao
import com.harsh.gymmanager.data.local.dao.MembershipDao
import com.harsh.gymmanager.data.local.dao.PaymentDao
import com.harsh.gymmanager.data.local.dao.PlanDao
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MemberSummary
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.data.local.entity.PlanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

const val DATABASE_NAME = "gym.db"

@Database(
    entities = [MemberEntity::class, PlanEntity::class, MembershipEntity::class, PaymentEntity::class],
    views = [MemberSummary::class],
    version = 1,
)
@TypeConverters(Converters::class)
@ConstructedBy(GymDatabaseConstructor::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun planDao(): PlanDao
    abstract fun membershipDao(): MembershipDao
    abstract fun paymentDao(): PaymentDao
}

// Room generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object GymDatabaseConstructor : RoomDatabaseConstructor<GymDatabase> {
    override fun initialize(): GymDatabase
}

fun buildGymDatabase(builder: RoomDatabase.Builder<GymDatabase>): GymDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
