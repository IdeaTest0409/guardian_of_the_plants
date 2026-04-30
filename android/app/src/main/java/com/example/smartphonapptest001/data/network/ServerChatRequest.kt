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
