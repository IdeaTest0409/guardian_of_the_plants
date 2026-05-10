package com.example.smartphonapptest001.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ServerChatRequest(
    @SerialName("deviceId")
    val deviceId: String,
    @SerialName("conversationId")
    val conversationId: String,
    @SerialName("messages")
    val messages: List<ServerMessage>,
    @SerialName("options")
    val options: Map<String, JsonElement>? = null,
)

@Serializable
data class LiveMessageResponse(
    @SerialName("messageId")
    val messageId: String? = null,
    @SerialName("assistantText")
    val assistantText: String = "",
    @SerialName("audioUrl")
    val audioUrl: String? = null,
    @SerialName("audioFormat")
    val audioFormat: String? = null,
    @SerialName("status")
    val status: String = "",
)

@Serializable
data class ServerMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: JsonElement,
) {
    companion object {
        fun text(role: String, text: String): ServerMessage =
            ServerMessage(role = role, content = JsonPrimitive(text))
    }
}
