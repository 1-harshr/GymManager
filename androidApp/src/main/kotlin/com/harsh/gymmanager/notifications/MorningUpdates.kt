package com.harsh.gymmanager.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.harsh.gymmanager.GymManagerApplication
import com.harsh.gymmanager.R
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.ui.notifications.MorningUpdateScheduler
import com.harsh.gymmanager.ui.notifications.NotificationText
import com.harsh.gymmanager.ui.notifications.notificationText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.time.Clock

private const val WORK_NAME = "morning-update"
private const val CHANNEL_ID = "morning_update"
private const val NOTIFICATION_ID = 1
private const val TAG = "MorningUpdate"

/**
 * Android wakes the app each morning to write the update from fresh data. One run is queued at a
 * time; each run queues the next morning's before posting.
 */
class AndroidMorningUpdates(private val context: Context) : MorningUpdateScheduler {
    override suspend fun sync(settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        val update = settings.morningUpdate
        if (!update.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // Already queued (or running) for this time: leave it, so an edit mid-run doesn't cancel today's update.
        val queued = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).first()
        if (queued.any { !it.state.isFinished && timeTag(update) in it.tags }) return
        enqueueNext(context, update, ExistingWorkPolicy.REPLACE)
    }
}

class MorningUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymManagerApplication
        val container = app.container
        val settings = container.settingsUseCases.observe().first()
        val update = settings.morningUpdate
        val gym = settings.gym
        if (!update.enabled || gym == null) return Result.success()

        // Queue tomorrow first; it runs once this one finishes, so a failure below can't break the chain.
        enqueueNext(applicationContext, update, ExistingWorkPolicy.APPEND_OR_REPLACE)
        // Started with the app closed, the saved sign-in may not be read yet. Without a gym to read
        // (signed out, or it can't be reached) there's nothing true to say this morning.
        if (!app.backend.awaitGym()) return Result.success()
        // New alerts wait in the bell's inbox even if the app isn't opened today.
        container.inbox.refresh()
        try {
            // Match the app's language, as the screens do; string resources read the default locale.
            settings.language.tag?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
            val digest = container.morningDigest.digest(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            post(applicationContext, digest.notificationText(gym.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't post the morning update", e)
        }
        // A failed run would also fail tomorrow's, which is queued behind it.
        return Result.success()
    }
}

private fun enqueueNext(context: Context, update: MorningUpdate, policy: ExistingWorkPolicy) {
    val request = OneTimeWorkRequestBuilder<MorningUpdateWorker>()
        .setInitialDelay(untilNext(update))
        .addTag(timeTag(update))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
}

/** Time from now to the next [MorningUpdate.hour]:[MorningUpdate.minute], today or tomorrow. */
private fun untilNext(update: MorningUpdate): Duration {
    val now = ZonedDateTime.now()
    val today = now.withHour(update.hour).withMinute(update.minute).withSecond(0).withNano(0)
    return Duration.between(now, if (today.isAfter(now)) today else today.plusDays(1))
}

private fun timeTag(update: MorningUpdate) = "at-${update.minuteOfDay}"

@SuppressLint("MissingPermission") // Checked through areNotificationsEnabled.
private fun post(context: Context, text: NotificationText) {
    val notifications = NotificationManagerCompat.from(context)
    if (!notifications.areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Re-creating keeps the channel's name in the current language.
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.morning_update_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(context, R.color.brand_clay))
        .setContentTitle(text.title)
        .setContentText(text.body.lineSequence().first())
        .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    notifications.notify(NOTIFICATION_ID, notification)
}
