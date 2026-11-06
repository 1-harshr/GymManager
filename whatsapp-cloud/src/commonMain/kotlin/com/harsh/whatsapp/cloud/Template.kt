package com.harsh.whatsapp.cloud

/**
 * A message template approved in WhatsApp Manager. Parameters fill `{{1}}`, `{{2}}`, … in order,
 * so their count must match the template or the API rejects the message
 * ([WhatsAppError.Kind.TEMPLATE]).
 *
 * @property name The template's name, e.g. `membership_receipt`.
 * @property languageCode The language it was approved in, e.g. `en` or `hi`.
 * @property headerParameters Values for a text header's variables, if it has any.
 * @property bodyParameters Values for the body's variables.
 */
public data class Template(
    val name: String,
    val languageCode: String,
    val bodyParameters: List<String> = emptyList(),
    val headerParameters: List<String> = emptyList(),
)
