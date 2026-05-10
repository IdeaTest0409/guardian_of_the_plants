package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

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
            options = options?.mapValues { (_, v) -> toJsonElement(v) },
        )

        val response = client.post("$normalizedUrl/chat") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(request)
        }

        val channel = response.bodyAsChannel()
        val buffer = StringBuilder()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line(Int.MAX_VALUE) ?: break
            if (line.isBlank()) continue
            if (!line.startsWith("data:")) continue

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") {
                emit(StreamToken(text = buffer.toString(), finished = true))
                break
            }

            val errorMessage = parseErrorMessage(payload)
            if (errorMessage != null) {
                logger.log(
                    AppLogSeverity.ERROR,
                    "ServerChatApi",
                    "Server chat API returned an error",
                    details = listOf(
                        "baseUrl=$normalizedUrl",
                        "error=$errorMessage",
                        "payload=${payload.take(500)}",
                    ).joinToString(separator = "\n"),
                )
                error("Server chat API error: $errorMessage")
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
        completeLiveMessage(messages, deviceId, conversationId, options)?.let { return it }
        var accumulated = ""
        stream(messages, deviceId, conversationId, options).collect { token ->
            accumulated = token.text
            if (token.finished) return@collect
        }
        return accumulated
    }

    private suspend fun completeLiveMessage(
        messages: List<ServerMessage>,
        deviceId: String,
        conversationId: String,
        options: Map<String, Any>? = null,
    ): String? {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        if (normalizedUrl.isBlank()) {
            error("Server chat API base URL is not configured")
        }

        val request = ServerChatRequest(
            deviceId = deviceId,
            conversationId = conversationId,
            messages = messages,
            options = options?.mapValues { (_, v) -> toJsonElement(v) },
        )

        return runCatching {
            val response = client.post("$normalizedUrl/live/message") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.log(
                    AppLogSeverity.WARN,
                    TAG,
                    "Live message API returned non-success status",
                    details = "status=${response.status.value}\nbody=${body.take(500)}",
                )
                return null
            }
            val parsed = json.decodeFromString(LiveMessageResponse.serializer(), body)
            if (parsed.status == "error") {
                logger.log(
                    AppLogSeverity.WARN,
                    TAG,
                    "Live message API returned error status",
                    details = "messageId=${parsed.messageId.orEmpty()}",
                )
                return null
            }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Live message completed",
                details = listOf(
                    "messageId=${parsed.messageId.orEmpty()}",
                    "replyChars=${parsed.assistantText.length}",
                    "audioUrl=${parsed.audioUrl.orEmpty()}",
                ).joinToString(separator = "\n"),
            )
            parsed.assistantText
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "Live message API failed; falling back to chat stream",
                details = "type=${error::class.java.simpleName}\nmessage=${error.message.orEmpty()}",
            )
        }.getOrNull()
    }

    companion object {
        private const val TAG = "ServerChatApi"

        private fun toJsonElement(value: Any): JsonElement {
            return when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }

        private fun parseErrorMessage(payload: String): String? {
            return runCatching {
                val element = Json.parseToJsonElement(payload)
                val obj = element as? JsonObject ?: return null
                obj["error"]?.jsonPrimitive?.content
            }.getOrNull()
        }
    }
}
