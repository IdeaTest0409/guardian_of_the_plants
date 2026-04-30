package com.example.smartphonapptest001.data.repository

import com.example.smartphonapptest001.data.local.LocalModelService
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.structuredDetails
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.data.network.KtorOpenAiCompatibleChatApi
import com.example.smartphonapptest001.data.network.ServerChatApi
import com.example.smartphonapptest001.data.network.ServerMessage
import com.example.smartphonapptest001.data.network.toOpenAiRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DefaultChatRepository(
    private val api: KtorOpenAiCompatibleChatApi,
    private val localModelService: LocalModelService,
    private val serverChatApi: ServerChatApi,
    private val logger: AppLogger,
) : ChatRepository {
    private val aiRequestMutex = Mutex()

    override suspend fun complete(
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): String = aiRequestMutex.withLock {
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "AI completion request started",
            details = buildString {
                appendLine("provider=${settings.providerType}")
                appendLine("baseUrl=${settings.activeEndpointConfig.baseUrl}")
                appendLine("model=${settings.activeEndpointConfig.model}")
                appendLine("messageCount=${messages.size}")
                appendLine("stream=${settings.streamResponses}")
                appendLine("lastUserMessage=${messages.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()}")
            },
        )
        try {
            val reply = when (settings.providerType) {
                ProviderType.CLOUD -> {
                    val response = api.complete(
                        settings.activeEndpointConfig,
                        messages.toOpenAiRequest(model = settings.activeEndpointConfig.model, settings = settings),
                    )
                    val reply = response.choices.firstOrNull()?.message?.content.orEmpty()
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Completion finished",
                        details = "replyChars=${reply.length}\nreply=$reply",
                    )
                    reply
                }

                ProviderType.LOCAL -> {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Using local on-device model",
                        details = buildString {
                            appendLine("localModel=${settings.localModel}")
                            appendLine("executionBackend=${settings.localExecutionBackend.name}")
                            appendLine("provider=${settings.providerType}")
                        },
                    )
                    val reply = localModelService.complete(messages, settings)
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Local completion finished",
                        details = "replyChars=${reply.length}\nreply=$reply",
                    )
                    reply
                }

                ProviderType.SERVER -> {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Using server-side model via VPS",
                        details = buildString {
                            appendLine("baseUrl=${settings.activeEndpointConfig.baseUrl}")
                            appendLine("provider=${settings.providerType}")
                        },
                    )
                    val serverMessages = messages.toServerMessages()
                    val conversationId = messages.findConversationId()
                    val reply = serverChatApi.complete(
                        messages = serverMessages,
                        deviceId = "android",
                        conversationId = conversationId,
                        options = mapOf(
                            "temperature" to settings.temperature,
                            "maxTokens" to settings.maxOutputTokens,
                            "top_k" to settings.topK,
                            "top_p" to settings.topP,
                        ),
                    )
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Server completion finished",
                        details = "replyChars=${reply.length}",
                    )
                    reply
                }
            }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "AI completion request finished",
                details = structuredDetails(
                    "provider" to settings.providerType.name,
                    "model" to settings.activeEndpointConfig.model,
                    "replyChars" to reply.length,
                ),
            )
            reply
        } catch (throwable: Throwable) {
            logger.log(
                AppLogSeverity.ERROR,
                TAG,
                "Completion failed",
                details = throwable.toStructuredLogDetails(
                    "provider" to settings.providerType.name,
                    "baseUrl" to settings.activeEndpointConfig.baseUrl,
                    "model" to settings.activeEndpointConfig.model,
                    "messageCount" to messages.size,
                    "stream" to settings.streamResponses,
                ),
            )
            throw throwable
        }
    }

    override fun stream(
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): Flow<String> = flow {
        aiRequestMutex.withLock {
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "AI streaming request started",
                details = buildString {
                    appendLine("provider=${settings.providerType}")
                    appendLine("baseUrl=${settings.activeEndpointConfig.baseUrl}")
                    appendLine("model=${settings.activeEndpointConfig.model}")
                    appendLine("messageCount=${messages.size}")
                    appendLine("stream=${settings.streamResponses}")
                    appendLine("lastUserMessage=${messages.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()}")
                },
            )
            when (settings.providerType) {
                ProviderType.CLOUD -> {
                    api.stream(
                        settings.activeEndpointConfig,
                        messages.toOpenAiRequest(
                            model = settings.activeEndpointConfig.model,
                            stream = true,
                            settings = settings,
                        ),
                    )
                        .collect { token ->
                            logger.log(
                                AppLogSeverity.DEBUG,
                                TAG,
                                "Streaming chunk received",
                                details = "chars=${token.text.length}\nfinished=${token.finished}",
                            )
                            emit(token.text)
                        }
                }

                ProviderType.LOCAL -> {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Streaming with local on-device model",
                        details = buildString {
                            appendLine("localModel=${settings.localModel}")
                            appendLine("executionBackend=${settings.localExecutionBackend.name}")
                        },
                    )
                    localModelService.stream(messages, settings).collect { emit(it) }
                }

                ProviderType.SERVER -> {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Streaming with server-side model via VPS",
                        details = buildString {
                            appendLine("baseUrl=${settings.activeEndpointConfig.baseUrl}")
                            appendLine("provider=${settings.providerType}")
                        },
                    )
                    val serverMessages = messages.toServerMessages()
                    val conversationId = messages.findConversationId()
                    serverChatApi.stream(
                        messages = serverMessages,
                        deviceId = "android",
                        conversationId = conversationId,
                        options = mapOf(
                            "temperature" to settings.temperature,
                            "maxTokens" to settings.maxOutputTokens,
                            "top_k" to settings.topK,
                            "top_p" to settings.topP,
                        ),
                    ).collect { token ->
                        logger.log(
                            AppLogSeverity.DEBUG,
                            TAG,
                            "Server streaming chunk received",
                            details = "chars=${token.text.length}\nfinished=${token.finished}",
                        )
                        emit(token.text)
                    }
                }
            }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "AI streaming request finished",
                details = structuredDetails(
                    "provider" to settings.providerType.name,
                    "model" to settings.activeEndpointConfig.model,
                )
            )
        }
    }

    private companion object {
        const val TAG = "ChatRepository"
    }
}

private fun List<ChatMessage>.toServerMessages(): List<ServerMessage> =
    map { msg ->
        val role = when (msg.role) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
        }
        val imageAttachments = msg.attachments.filter { it.kind == com.example.smartphonapptest001.data.model.AttachmentKind.IMAGE && !it.dataUrl.isNullOrBlank() }
        if (imageAttachments.isEmpty() && msg.content.isNotBlank()) {
            ServerMessage.text(role, msg.content)
        } else {
            val contentArray = mutableListOf<kotlinx.serialization.json.JsonElement>()
            if (msg.content.isNotBlank()) {
                contentArray.add(JsonPrimitive(msg.content))
            }
            for (attachment in imageAttachments) {
                contentArray.add(
                    buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", attachment.dataUrl!!)
                        })
                    }
                )
            }
            ServerMessage(role = role, content = JsonArray(contentArray))
        }
    }

private fun List<ChatMessage>.findConversationId(): String {
    val userMsg = firstOrNull { it.role == ChatRole.USER }
    return userMsg?.id?.take(36) ?: UUID.randomUUID().toString()
}

private fun String.toOpenAiChatCompletionsUrlForLog(): String {
    val normalizedBase = trim().trimEnd('/')
    val apiBase = if (normalizedBase.endsWith("/v1")) normalizedBase else "$normalizedBase/v1"
    return "$apiBase/chat/completions"
}


