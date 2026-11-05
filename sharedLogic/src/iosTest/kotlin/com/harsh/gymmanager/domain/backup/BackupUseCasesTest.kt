package com.harsh.gymmanager.domain.backup

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.BackupRepositoryImpl
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.MessageLogRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.repository.SettingsRepository
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class BackupUseCasesTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        override fun observe() = state
        override suspend fun setGym(profile: GymProfile) { state.value = state.value.copy(gym = profile) }
        override suspend fun setTheme(theme: ThemeMode) { state.value = state.value.copy(theme = theme) }
        override suspend fun setLanguage(language: AppLanguage) { state.value = state.value.copy(language = language) }
        override suspend fun setLastBackupAt(at: Instant) { state.value = state.value.copy(lastBackupAt = at) }
        override suspend fun setMorningUpdate(update: MorningUpdate) { state.value = state.value.copy(morningUpdate = update) }
        override suspend fun setHomeFeatures(features: HomeFeatures) { state.value = state.value.copy(homeFeatures = features) }
    }

    /** An in-memory folder tree standing in for app storage. */
    private class FakeFiles : StoredFiles {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun read(path: String) = files[path]
        override suspend fun write(folder: String, fileName: String, bytes: ByteArray) = "$folder/$fileName".also { files[it] = bytes }
        override suspend fun deleteOthers(folder: String, keep: Set<String>) {
            files.keys.removeAll { it.startsWith("$folder/") && it !in keep }
        }
    }

    private lateinit var db: GymDatabase
    private lateinit var settings: FakeSettings
    private lateinit var files: FakeFiles
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var payments: PaymentUseCases
    private lateinit var log: MessageLogRepositoryImpl
    private lateinit var backup: BackupUseCases

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        settings = FakeSettings()
        files = FakeFiles()
        members = MemberUseCases(MemberRepositoryImpl(db.memberDao(), clock, tz), clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(MembershipRepositoryImpl(db.membershipDao(), clock), clock, tz)
        payments = PaymentUseCases(PaymentRepositoryImpl(db.paymentDao(), clock), clock, tz)
        log = MessageLogRepositoryImpl(db.messageLogDao(), clock)
        backup = BackupUseCases(BackupRepositoryImpl(db.backupDao()), settings, files, clock, tz)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun restoreBringsBackEverythingIncludingPhotosAndSettings() = runTest {
        val photo = byteArrayOf(1, 2, 3, -1)
        val logo = byteArrayOf(9, 8, 7)
        files.files["photos/ravi.jpg"] = photo
        files.files["branding/logo.png"] = logo
        settings.setGym(GymProfile("Iron House", "Main Road", "branding/logo.png"))
        settings.setTheme(ThemeMode.DARK)
        settings.setLanguage(AppLanguage.HINDI)
        val planId = plans.addPlan("Monthly", 1, DurationUnit.MONTHS, 1500).getOrThrow()
        val plan = plans.observePlans().first().single()
        val ravi = members.addMember("Ravi", phone = "9876543210", photoPath = "photos/ravi.jpg").getOrThrow()
        members.addMember("Sita").getOrThrow()
        memberships.sellMembership(ravi, plan, discount = 100, firstPaymentAmount = 1000).getOrThrow()
        val membershipId = memberships.observeMemberships(ravi).first().single().id
        payments.recordPayment(membershipId, 200, PaymentMode.UPI).getOrThrow()
        log.record(ravi, MessageKind.RECEIPT)

        val bytes = backup.create().getOrThrow()
        val text = bytes.decodeToString()
        assertTrue(text.contains("\"app\":\"gym-manager\"") && text.contains("\"format\":1"), "the file says what it is")
        assertEquals("gym-manager-backup-2026-11-05.json", backup.fileName())

        // A different phone: other data, other settings.
        members.deleteMemberPermanently(ravi).getOrThrow()
        members.addMember("Someone else").getOrThrow()
        settings.setGym(GymProfile("Other Gym"))
        settings.setTheme(ThemeMode.LIGHT)
        files.files.clear()

        val file = backup.read(bytes).getOrThrow()
        assertEquals(2, file.members.size)
        backup.restore(file).getOrThrow()

        val restored = members.observeMembers().first()
        assertEquals(listOf("Ravi", "Sita"), restored.map { it.name }.sorted())
        val restoredRavi = members.observeMember(ravi).first()
        assertNotNull(restoredRavi)
        assertContentEquals(photo, files.files[restoredRavi.photoPath])
        val membership = memberships.observeMemberships(ravi).first().single()
        assertEquals(planId, membership.planId)
        assertEquals(1400, membership.amountCharged)
        assertEquals(listOf(1000L, 200L), membership.payments.map { it.amount }.sortedDescending())
        assertEquals(1, db.backupDao().messages().size)

        val gym = assertNotNull(settings.state.value.gym)
        assertEquals("Iron House", gym.name)
        assertEquals("Main Road", gym.address)
        assertContentEquals(logo, files.files[gym.logoPath])
        assertEquals(ThemeMode.DARK, settings.state.value.theme)
        assertEquals(AppLanguage.HINDI, settings.state.value.language)
        assertEquals(2, files.files.size, "only the restored photo and logo remain")
    }

    @Test
    fun rejectsFilesThatAreNotBackupsOrTooNew() = runTest {
        val notJson = backup.read("name,phone\nRavi,98765".encodeToByteArray()).exceptionOrNull()
        assertEquals(BackupProblem.NOT_A_BACKUP, assertIs<BackupException>(notJson).problem)

        val otherJson = backup.read("""{"hello":"world"}""".encodeToByteArray()).exceptionOrNull()
        assertEquals(BackupProblem.NOT_A_BACKUP, assertIs<BackupException>(otherJson).problem)

        val unmarked = backup.read("""{"createdAt":0,"members":[]}""".encodeToByteArray()).exceptionOrNull()
        assertEquals(BackupProblem.NOT_A_BACKUP, assertIs<BackupException>(unmarked).problem)

        val newer = backup.read("""{"app":"gym-manager","format":99,"createdAt":0}""".encodeToByteArray()).exceptionOrNull()
        assertEquals(BackupProblem.FROM_NEWER_APP, assertIs<BackupException>(newer).problem)
    }

    @Test
    fun markSavedRecordsWhen() = runTest {
        assertNull(settings.state.value.lastBackupAt)
        backup.markSaved().getOrThrow()
        assertEquals(clock.now(), settings.state.value.lastBackupAt)
        assertTrue(backup.create().isSuccess)
    }
}
