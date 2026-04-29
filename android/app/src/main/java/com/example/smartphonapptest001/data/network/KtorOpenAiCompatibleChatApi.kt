package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.EndpointConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.json.Json

data class StreamToken(
    val text: String,
    val finished: Boolean = false,
)

class KtorOpenAiCompatibleChatApi(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun complete(
        config: EndpointConfig,
        request: OpenAiChatRequest,
    ): OpenAiChatResponse {
        val response: HttpResponse = client.post(buildChatCompletionsUrl(config.baseUrl)) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            if (!config.apiKey.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            }
            setBody(
                request.copy(
                    model = config.model,
                    stream = false,
                ),
            )
        }
        return response.body<OpenAiChatResponse>()
    }

    fun stream(
        config: EndpointConfig,
        request: OpenAiChatRequest,
    ): Flow<StreamToken> = flow {
        val response = client.post(buildChatCompletionsUrl(config.baseUrl)) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            if (!config.apiKey.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            }
            setBody(
                request.copy(
                    model = config.model,
                    stream = true,
                ),
            )
        }

        emitAll(
            emitSseStream(
                channel = response.bodyAsChannel(),
                json = json,
            ),
        )
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val apiBase = if (normalizedBase.endsWith("/v1")) {
            normalizedBase
        } else {
            "$normalizedBase/v1"
        }
        return "$apiBase/chat/completions"
    }

    private fun emitSseStream(
        channel: ByteReadChannel,
        json: Json,
    ): Flow<StreamToken> = flow {
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
    }
}
