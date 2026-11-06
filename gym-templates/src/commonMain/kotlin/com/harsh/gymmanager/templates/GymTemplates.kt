package com.harsh.gymmanager.templates

import com.harsh.whatsapp.cloud.TemplateCategory
import com.harsh.whatsapp.cloud.TemplateDefinition

/**
 * The templates the gym needs approved in WhatsApp Manager: the app lists them for the owner to
 * create, fills them in `gymTemplate()` (sharedLogic) and previews them with [render]. WhatsApp rejects a body that starts or ends with a variable.
 */
val gymTemplates = listOf(
    TemplateDefinition(
        name = "gym_welcome",
        languageCode = "en",
        category = TemplateCategory.UTILITY,
        body = "Hi {{1}}, welcome to {{2}}! Your membership is set up. Message us here if you have any questions.",
        bodyExample = listOf("Ravi", "AKHADA Gym & MMA"),
    ),
    TemplateDefinition(
        name = "gym_receipt",
        languageCode = "en",
        category = TemplateCategory.UTILITY,
        body = "Receipt from {{1}}\nReceipt no. {{2}}\n\nHi {{3}}, thank you for your payment.\n\n" +
            "Plan: {{4}}\nValid: {{5}} to {{6}}\nPaid: {{7}}\nBalance due: {{8}}\n\nThank you!",
        bodyExample = listOf("AKHADA Gym & MMA", "12", "Ravi", "Monthly", "6 Nov 2026", "5 Dec 2026", "₹1,000", "₹500"),
    ),
    TemplateDefinition(
        name = "gym_membership_ending",
        languageCode = "en",
        category = TemplateCategory.UTILITY,
        body = "Hi {{1}}, your membership at {{2}} ends on {{3}}. Please renew to keep training without a break.",
        bodyExample = listOf("Ravi", "AKHADA Gym & MMA", "8 Nov 2026"),
    ),
    TemplateDefinition(
        name = "gym_membership_ended",
        languageCode = "en",
        category = TemplateCategory.UTILITY,
        body = "Hi {{1}}, your membership at {{2}} ended on {{3}}. Renew any time to get back to training.",
        bodyExample = listOf("Ravi", "AKHADA Gym & MMA", "1 Nov 2026"),
    ),
    TemplateDefinition(
        name = "gym_payment_due",
        languageCode = "en",
        category = TemplateCategory.UTILITY,
        body = "Hi {{1}}, a balance of {{2}} is due on your membership at {{3}}. Please clear it on your next visit. Thank you!",
        bodyExample = listOf("Ravi", "₹500", "AKHADA Gym & MMA"),
    ),
)

fun gymTemplateDefinition(name: String, languageCode: String): TemplateDefinition? =
    gymTemplates.find { it.name == name && it.languageCode == languageCode }

/** The body as the member will read it, with `{{1}}`, `{{2}}`, … filled from [parameters]. */
fun TemplateDefinition.render(parameters: List<String>): String =
    VARIABLE.replace(body) { match -> parameters.getOrNull(match.groupValues[1].toInt() - 1) ?: match.value }

/** How many variables the body has. */
val TemplateDefinition.variableCount: Int get() = VARIABLE.findAll(body).map { it.groupValues[1] }.toSet().size

// Both braces escaped: Android's regex engine (ICU) rejects a bare `}`, where Java's accepts it.
private val VARIABLE = Regex("""\{\{(\d+)\}\}""")
