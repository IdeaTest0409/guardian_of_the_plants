package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.AttachmentKind
import com.example.smartphonapptest001.data.model.ChatMessage

fun List<ChatMessage>.toOllamaNativeRequest(
    model: String,
    stream: Boolean = false,
    settings: AppSettings,
): OllamaChatRequest {
    return OllamaChatRequest(
        model = model,
        messages = map { message ->
            val imagePayloads = message.attachments
                .filter { it.kind == AttachmentKind.IMAGE }
                .mapNotNull { it.dataUrl?.toOllamaBase64Image() }
                .ifEmpty { null }

            OllamaRequestMessage(
                role = message.role,
                content = message.content.trim(),
                images = imagePayloads,
            )
        },
        stream = stream,
        options = OllamaOptions(
            temperature = settings.temperature,
            topP = settings.topP,
            topK = settings.topK,
            numPredict = settings.maxOutputTokens,
        ),
        think = settings.thinkingEnabled,
    )
}

private fun String.toOllamaBase64Image(): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val marker = ";base64,"
    val markerIndex = trimmed.indexOf(marker)
    return if (trimmed.startsWith("data:", ignoreCase = true) && markerIndex >= 0) {
        trimmed.substring(markerIndex + marker.length).takeIf { it.isNotBlank() }
    } else {
        trimmed
    }
}
