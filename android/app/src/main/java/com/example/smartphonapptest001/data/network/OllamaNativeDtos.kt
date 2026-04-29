package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.ChatRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaRequestMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
    val think: Boolean? = null,
)

@Serializable
data class OllamaRequestMessage(
    val role: ChatRole,
    val content: String,
    val images: List<String>? = null,
)

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaResponseMessage? = null,
    val done: Boolean = false,
    @SerialName("done_reason")
    val doneReason: String? = null,
)

@Serializable
data class OllamaResponseMessage(
    val role: ChatRole? = null,
    val content: String = "",
)
