package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.AttachmentKind
import com.example.smartphonapptest001.data.model.ChatMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun List<ChatMessage>.toOpenAiRequest(
    model: String = "",
    stream: Boolean = false,
    settings: AppSettings = AppSettings(),
): OpenAiChatRequest {
    return OpenAiChatRequest(
        model = model,
        messages = map { message ->
            OpenAiRequestMessage(
                role = message.role,
                content = message.toOpenAiContent(),
            )
        },
        stream = stream,
        temperature = settings.temperature,
        topP = settings.topP,
        topK = settings.topK,
        maxTokens = settings.maxOutputTokens,
    )
}

private fun ChatMessage.toOpenAiContent(): JsonElement {
    val text = content.trim()
    val imageAttachments = attachments.filter { it.kind == AttachmentKind.IMAGE }

    if (imageAttachments.isEmpty()) {
        return JsonPrimitive(text)
    }

    return buildJsonArray {
        if (text.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(text))
                },
            )
        }

        imageAttachments.forEach { attachment ->
            val dataUrl = attachment.dataUrl
            if (!dataUrl.isNullOrBlank()) {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        putJsonObject("image_url") {
                            put("url", JsonPrimitive(dataUrl))
                        }
                    },
                )
            }
        }
    }
}
