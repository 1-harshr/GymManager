package com.harsh.whatsapp.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The Graph API's JSON shapes. Kept internal so the public API doesn't change when Meta's does.

@Serializable
internal data class MessageRequest(
    @SerialName("messaging_product") val messagingProduct: String = "whatsapp",
    @SerialName("recipient_type") val recipientType: String = "individual",
    val to: String,
    val type: String,
    val template: TemplateBody? = null,
    val text: TextBody? = null,
)

@Serializable
internal data class TemplateBody(val name: String, val language: Language, val components: List<Component>)

@Serializable
internal data class Language(val code: String)

@Serializable
internal data class Component(val type: String, val parameters: List<Parameter>)

@Serializable
internal data class Parameter(val type: String = "text", val text: String)

@Serializable
internal data class TextBody(val body: String, @SerialName("preview_url") val previewUrl: Boolean)

@Serializable
internal data class MessageResponse(val contacts: List<Contact> = emptyList(), val messages: List<SentMessage> = emptyList())

@Serializable
internal data class Contact(val input: String? = null, @SerialName("wa_id") val waId: String? = null)

@Serializable
internal data class SentMessage(val id: String)

@Serializable
internal data class ErrorResponse(val error: ErrorBody)

@Serializable
internal data class ErrorBody(
    val message: String = "",
    val code: Int? = null,
    @SerialName("error_subcode") val subcode: Int? = null,
    @SerialName("error_data") val errorData: ErrorData? = null,
    @SerialName("fbtrace_id") val fbTraceId: String? = null,
)

@Serializable
internal data class ErrorData(val details: String? = null)

internal fun Template.toBody() = TemplateBody(
    name = name,
    language = Language(languageCode),
    components = buildList {
        if (headerParameters.isNotEmpty()) add(Component("header", headerParameters.map { Parameter(text = it) }))
        if (bodyParameters.isNotEmpty()) add(Component("body", bodyParameters.map { Parameter(text = it) }))
    },
)

@Serializable
internal data class Success(val success: Boolean = true)

@Serializable
internal data class RegisterRequest(@SerialName("messaging_product") val messagingProduct: String = "whatsapp", val pin: String)

@Serializable
internal data class SyncRequest(@SerialName("messaging_product") val messagingProduct: String = "whatsapp", @SerialName("sync_type") val syncType: String)

@Serializable
internal data class AccessToken(@SerialName("access_token") val accessToken: String)

/** A business phone number as the Graph API describes it. */
@Serializable
public data class PhoneNumberInfo(
    val id: String,
    @SerialName("display_phone_number") val displayPhoneNumber: String? = null,
    @SerialName("verified_name") val verifiedName: String? = null,
    @SerialName("quality_rating") val qualityRating: String? = null,
)

@Serializable
internal data class CreateTemplateRequest(
    val name: String,
    val language: String,
    val category: String,
    val components: List<TemplateComponent>,
)

@Serializable
internal data class TemplateComponent(val type: String, val text: String, val example: TemplateExample? = null)

@Serializable
internal data class TemplateExample(@SerialName("body_text") val bodyText: List<List<String>>)

@Serializable
internal data class CreatedTemplate(val id: String, val status: String, val category: String? = null)

@Serializable
internal data class TemplatePage(val data: List<ListedTemplate> = emptyList())

@Serializable
internal data class ListedTemplate(val id: String, val name: String, val language: String, val status: String, val category: String)

internal fun TemplateDefinition.toRequest() = CreateTemplateRequest(
    name = name,
    language = languageCode,
    category = category.name,
    components = listOf(TemplateComponent("BODY", body, bodyExample.takeIf { it.isNotEmpty() }?.let { TemplateExample(listOf(it)) })),
)
