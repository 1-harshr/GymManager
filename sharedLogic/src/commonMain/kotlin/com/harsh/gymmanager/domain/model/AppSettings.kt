package com.harsh.gymmanager.domain.model

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ensure

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** [tag] is the BCP 47 language tag; null follows the phone's language. */
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), HINDI("hi") }

/** The gym the app is set up for; shown in the header and on Home. */
data class GymProfile(
    val name: String,
    val address: String? = null,
    /** Path to the logo image in app storage; null shows the gym's initials. */
    val logoPath: String? = null,
) {
    init {
        ensure(name.isNotBlank(), ValidationError.GYM_NAME_REQUIRED)
    }
}

/** A daily notification summarising the gym; [minuteOfDay] is local time, e.g. 480 for 8:00 AM. */
data class MorningUpdate(val enabled: Boolean = false, val minuteOfDay: Int = DEFAULT_MINUTE_OF_DAY) {
    init {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minuteOfDay out of range: $minuteOfDay" }
    }

    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60

    companion object {
        const val DEFAULT_MINUTE_OF_DAY = 8 * 60
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Optional extras on Home; both off until the owner turns them on in Settings. */
data class HomeFeatures(
    /** The daily brief, the month's outlook, renewals and who needs attention. */
    val aiInsights: Boolean = false,
    /** This month's collections against last month's, day by day. */
    val collectionsChart: Boolean = false,
)

data class AppSettings(
    /** Null until the owner finishes first-run setup. */
    val gym: GymProfile? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val morningUpdate: MorningUpdate = MorningUpdate(),
    val homeFeatures: HomeFeatures = HomeFeatures(),
    /** Members shown as a grid of tiles rather than a list. */
    val membersAsGrid: Boolean = false,
)
