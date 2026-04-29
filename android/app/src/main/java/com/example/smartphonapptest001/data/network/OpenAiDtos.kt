package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.ChatRole
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiRequestMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
)

@Serializable
data class OpenAiRequestMessage(
    val role: ChatRole,
    val content: JsonElement,
)

@Serializable
data class OpenAiChatResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiResponseMessage? = null,
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class OpenAiResponseMessage(
    val role: ChatRole,
    val content: String,
)

@Serializable
data class OpenAiDelta(
    val role: ChatRole? = null,
    val content: String? = null,
)
