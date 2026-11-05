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

data class AppSettings(
    /** Null until the owner finishes first-run setup. */
    val gym: GymProfile? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
)
