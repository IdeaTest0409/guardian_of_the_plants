package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class ServerChatApi(
    private val client: HttpClient,
    private val json: Json,
    private val logger: AppLogger,
    private val baseUrl: String,
) {
    fun stream(
        messages: List<ServerMessage>,
        deviceId: String,
        conversationId: String,
        options: Map<String, Any>? = null,
    ): Flow<StreamToken> = flow {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        if (normalizedUrl.isBlank()) {
            error("Server chat API base URL is not configured")
        }

        val request = ServerChatRequest(
            deviceId = deviceId,
            conversationId = conversationId,
            messages = messages,
            options = options,
        )

        val response = client.post("$normalizedUrl/chat") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(request)
        }

        val channel = response.bodyAsChannel()
        val buffer = StringBuilder()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue
            if (!line.startsWith("data:")) continue

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") {
                emit(StreamToken(text = buffer.toString(), finished = true))
                break
            }

            runCatching {
                json.decodeFromString(OpenAiChatResponse.serializer(), payload)
            }.onSuccess { chunk ->
                val deltaText = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
                if (deltaText.isNotBlank()) {
                    buffer.append(deltaText)
                    emit(StreamToken(text = buffer.toString(), finished = false))
                }
            }
        }

        logger.log(
            AppLogSeverity.INFO,
            "ServerChatApi",
            "Server streaming completed",
            details = "totalChars=${buffer.length}",
        )
    }

    suspend fun complete(
        messages: List<ServerMessage>,
        deviceId: String,
        conversationId: String,
        options: Map<String, Any>? = null,
    ): String {
        var accumulated = ""
        stream(messages, deviceId, conversationId, options).collect { token ->
            accumulated = token.text
            if (token.finished) return@collect
        }
        return accumulated
    }

    companion object {
        private const val TAG = "ServerChatApi"
    }
}
