package com.harsh.gymmanager.ui.notifications

import com.harsh.gymmanager.domain.digest.MorningDigestUseCase
import com.harsh.gymmanager.domain.model.AppSettings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

/**
 * iOS won't run the app at a set time, so the next [DAYS_AHEAD] mornings are written now and
 * handed to the system. Each sync replaces them, so they stay true as long as the app is opened
 * at least once in that many days.
 */
class IosMorningUpdates(
    private val digests: MorningDigestUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : MorningUpdateScheduler {
    override suspend fun sync(settings: AppSettings) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(IDS)
        val update = settings.morningUpdate
        val gym = settings.gym
        if (!update.enabled || gym == null) return

        val now = clock.now().toLocalDateTime(timeZone)
        val first = if (now.hour * 60 + now.minute < update.minuteOfDay) now.date else now.date.plus(1, DateTimeUnit.DAY)
        digests.digests(first, DAYS_AHEAD).forEachIndexed { index, digest ->
            val text = digest.notificationText(gym.name)
            val content = UNMutableNotificationContent().apply {
                setTitle(text.title)
                setBody(text.body)
                setSound(UNNotificationSound.defaultSound())
            }
            val at = NSDateComponents().apply {
                year = digest.date.year.toLong()
                month = digest.date.month.number.toLong()
                day = digest.date.day.toLong()
                hour = update.hour.toLong()
                minute = update.minute.toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(IDS[index], content, trigger), null)
        }
    }

    private companion object {
        /** Well under iOS's limit of 64 pending notifications per app. */
        const val DAYS_AHEAD = 14
        val IDS = List(DAYS_AHEAD) { "morning-update-$it" }
    }
}
