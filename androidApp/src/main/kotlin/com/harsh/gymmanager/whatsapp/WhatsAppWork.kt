package com.harsh.gymmanager.whatsapp

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.harsh.gymmanager.GymManagerApplication
import com.harsh.gymmanager.domain.whatsapp.FlushResult
import com.harsh.gymmanager.domain.whatsapp.WhatsAppSettings
import com.harsh.gymmanager.domain.whatsapp.WhatsAppScheduler
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val SEND_WORK = "whatsapp-send"
private const val REMINDER_WORK = "whatsapp-reminders"

/**
 * The phone's "cron": sends queued WhatsApp messages to Make as soon as there's internet, even
 * if the app is closed, and wakes the app each day at the reminder time. Both always use the
 * gym's real data.
 */
class AndroidWhatsAppScheduler(private val context: Context) : WhatsAppScheduler {
    override fun outboxChanged() = enqueueSend(context)

    override suspend fun sync(settings: WhatsAppSettings) {
        val workManager = WorkManager.getInstance(context)
        val auto = settings.autoSend
        if (!auto.reminders || !settings.isSetUp) {
            workManager.cancelUniqueWork(REMINDER_WORK)
            return
        }
        // Already queued for this time: leave it, so an unrelated settings change doesn't reset it.
        val queued = workManager.getWorkInfosForUniqueWorkFlow(REMINDER_WORK).first()
        if (queued.any { !it.state.isFinished && timeTag(auto.reminderMinuteOfDay) in it.tags }) return
        enqueueReminders(context, auto.reminderMinuteOfDay, ExistingWorkPolicy.REPLACE)
    }
}

class WhatsAppSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val whatsApp = (applicationContext as GymManagerApplication).container.whatsApp
        val result = whatsApp.flush()
        return if (result == FlushResult.RETRY_LATER) Result.retry() else Result.success()
    }
}

class WhatsAppReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymManagerApplication
        val whatsApp = app.container.whatsApp
        val settings = whatsApp.observeSettings().first()
        if (!settings.autoSend.reminders || !settings.isSetUp) return Result.success()
        // Queue tomorrow first, so a failure below can't break the chain.
        enqueueReminders(applicationContext, settings.autoSend.reminderMinuteOfDay, ExistingWorkPolicy.APPEND_OR_REPLACE)
        // Who's due comes from the gym online; without it, today's reminders wait for tomorrow.
        if (!app.backend.awaitGym()) return Result.success()
        whatsApp.sendDueReminders()
        enqueueSend(applicationContext)
        return Result.success()
    }
}

private fun enqueueSend(context: Context) {
    val request = OneTimeWorkRequestBuilder<WhatsAppSendWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()
    // Appended, so a message queued while a run is under way still gets its own run.
    WorkManager.getInstance(context).enqueueUniqueWork(SEND_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

private fun enqueueReminders(context: Context, minuteOfDay: Int, policy: ExistingWorkPolicy) {
    val request = OneTimeWorkRequestBuilder<WhatsAppReminderWorker>()
        .setInitialDelay(untilNext(minuteOfDay))
        .addTag(timeTag(minuteOfDay))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(REMINDER_WORK, policy, request)
}

/** Time from now to the next [minuteOfDay], today or tomorrow. */
private fun untilNext(minuteOfDay: Int): Duration {
    val now = ZonedDateTime.now()
    val today = now.withHour(minuteOfDay / 60).withMinute(minuteOfDay % 60).withSecond(0).withNano(0)
    return Duration.between(now, if (today.isAfter(now)) today else today.plusDays(1))
}

private fun timeTag(minuteOfDay: Int) = "at-$minuteOfDay"
