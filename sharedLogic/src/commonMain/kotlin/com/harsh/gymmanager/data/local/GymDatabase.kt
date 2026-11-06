package com.harsh.gymmanager.data.local

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.harsh.gymmanager.data.local.dao.BackupDao
import com.harsh.gymmanager.data.local.dao.InboxDao
import com.harsh.gymmanager.data.local.dao.MemberDao
import com.harsh.gymmanager.data.local.dao.MembershipDao
import com.harsh.gymmanager.data.local.dao.MessageLogDao
import com.harsh.gymmanager.data.local.dao.OutboxDao
import com.harsh.gymmanager.data.local.dao.PaymentDao
import com.harsh.gymmanager.data.local.dao.PlanDao
import com.harsh.gymmanager.data.local.dao.UsageDao
import com.harsh.gymmanager.data.local.entity.InboxEntity
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MemberSummaryView
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.MessageLogEntity
import com.harsh.gymmanager.data.local.entity.OutboxEntity
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.data.local.entity.PlanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

const val DATABASE_NAME = "gym.db"

/** Sample data for demos, next to the real file and never mixed with it. */
const val DEMO_DATABASE_NAME = "gym-demo.db"

@Database(
    entities = [
        MemberEntity::class, PlanEntity::class, MembershipEntity::class, PaymentEntity::class, MessageLogEntity::class,
        InboxEntity::class, OutboxEntity::class,
    ],
    views = [MemberSummaryView::class],
    version = 4,
    // 2: message_log, for WhatsApp messages. 3: inbox, for the bell's alerts. 4: whatsapp_outbox, for automatic sending.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
@ConstructedBy(GymDatabaseConstructor::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun planDao(): PlanDao
    abstract fun membershipDao(): MembershipDao
    abstract fun paymentDao(): PaymentDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun backupDao(): BackupDao
    abstract fun inboxDao(): InboxDao
    abstract fun usageDao(): UsageDao
    abstract fun outboxDao(): OutboxDao
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
