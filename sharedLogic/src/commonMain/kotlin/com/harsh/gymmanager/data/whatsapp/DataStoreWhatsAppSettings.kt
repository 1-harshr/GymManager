package com.harsh.gymmanager.data.whatsapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.harsh.gymmanager.domain.whatsapp.AutoSend
import com.harsh.gymmanager.domain.whatsapp.WhatsAppSettings
import com.harsh.gymmanager.domain.whatsapp.WhatsAppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Stored in the app's private settings file next to the other settings. */
class DataStoreWhatsAppSettings(private val store: DataStore<Preferences>) : WhatsAppSettingsRepository {
    private object Keys {
        val webhookUrl = stringPreferencesKey("wa_webhook_url")
        val autoWelcome = booleanPreferencesKey("wa_auto_welcome")
        val autoReceipts = booleanPreferencesKey("wa_auto_receipts")
        val autoReminders = booleanPreferencesKey("wa_auto_reminders")
        val reminderMinute = intPreferencesKey("wa_reminder_minute")
        val lastReminderRun = stringPreferencesKey("wa_last_reminder_run")
    }

    override fun observe(): Flow<WhatsAppSettings> = store.data.map { prefs ->
        WhatsAppSettings(
            webhookUrl = prefs[Keys.webhookUrl],
            autoSend = AutoSend(
                welcome = prefs[Keys.autoWelcome] ?: false,
                receipts = prefs[Keys.autoReceipts] ?: false,
                reminders = prefs[Keys.autoReminders] ?: false,
                reminderMinuteOfDay = prefs[Keys.reminderMinute]?.takeIf { it in 0 until 24 * 60 } ?: AutoSend.DEFAULT_REMINDER_MINUTE,
            ),
            lastReminderRun = prefs[Keys.lastReminderRun]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }.distinctUntilChanged()

    override suspend fun setWebhookUrl(url: String?) {
        store.edit { if (url != null) it[Keys.webhookUrl] = url else it.remove(Keys.webhookUrl) }
    }

    override suspend fun setAutoSend(autoSend: AutoSend) {
        store.edit {
            it[Keys.autoWelcome] = autoSend.welcome
            it[Keys.autoReceipts] = autoSend.receipts
            it[Keys.autoReminders] = autoSend.reminders
            it[Keys.reminderMinute] = autoSend.reminderMinuteOfDay
        }
    }

    override suspend fun setLastReminderRun(day: LocalDate) {
        store.edit { it[Keys.lastReminderRun] = day.toString() }
    }
}
