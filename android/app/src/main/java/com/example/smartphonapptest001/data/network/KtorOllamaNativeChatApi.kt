package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.EndpointConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class KtorOllamaNativeChatApi(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun complete(
        config: EndpointConfig,
        request: OllamaChatRequest,
    ): OllamaChatResponse {
        return client.post(buildChatUrl(config.baseUrl)) {
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
        }.body()
    }

    fun stream(
        config: EndpointConfig,
        request: OllamaChatRequest,
    ): Flow<StreamToken> = flow {
        val response = client.post(buildChatUrl(config.baseUrl)) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
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

        emitNativeStream(response.bodyAsChannel(), json)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamToken>.emitNativeStream(
        channel: ByteReadChannel,
        json: Json,
    ) {
        val buffer = StringBuilder()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue

            runCatching {
                json.decodeFromString(OllamaChatResponse.serializer(), line)
            }.onSuccess { chunk ->
                val text = chunk.message?.content.orEmpty()
                if (text.isNotBlank()) {
                    buffer.append(text)
                    emit(StreamToken(text = buffer.toString(), finished = chunk.done))
                }
                if (chunk.done) {
                    emit(StreamToken(text = buffer.toString(), finished = true))
                    return
                }
            }
        }
    }

    private fun buildChatUrl(baseUrl: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val apiBase = if (normalizedBase.endsWith("/v1")) {
            normalizedBase.removeSuffix("/v1")
        } else {
            normalizedBase
        }
        return "$apiBase/api/chat"
    }
}
