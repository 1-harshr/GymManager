package com.harsh.gymmanager

import com.harsh.gymmanager.domain.assistant.GymAssistant
import com.harsh.gymmanager.presentation.formatRupees

/** The app's assistant for Siri and Shortcuts; shares its database with the on-screen app. */
@Suppress("unused")
fun gymAssistant(): GymAssistant = iosDependencies.assistant

/** [formatRupees] for Swift, e.g. ₹1,23,456. */
@Suppress("unused")
fun rupees(amount: Long): String = formatRupees(amount)
