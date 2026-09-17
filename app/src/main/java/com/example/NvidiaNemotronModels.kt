package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data transfer objects for the NVIDIA Nemotron NIM Chat Completions API.
 */
@JsonClass(generateAdapter = true)
data class NemotronChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class NemotronChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<NemotronChatMessage>,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
    @Json(name = "temperature") val temperature: Double? = null,
    @Json(name = "top_p") val topP: Double? = null,
    @Json(name = "stream") val stream: Boolean? = false,
    @Json(name = "chat_template_kwargs") val chatTemplateKwargs: Map<String, Boolean>? = mapOf("thinking" to false)
)

@JsonClass(generateAdapter = true)
data class NemotronChoice(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "message") val message: NemotronChatMessage? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class NemotronUsage(
    @Json(name = "prompt_tokens") val promptTokens: Int? = null,
    @Json(name = "completion_tokens") val completionTokens: Int? = null,
    @Json(name = "total_tokens") val totalTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class NemotronChatResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<NemotronChoice>? = null,
    @Json(name = "usage") val usage: NemotronUsage? = null,
    @Json(name = "model") val model: String? = null
)
