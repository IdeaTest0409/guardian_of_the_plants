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
import com.example.smartphonapptest001.data.network.KtorOllamaNativeChatApi
import com.example.smartphonapptest001.data.network.KtorOpenAiCompatibleChatApi
import com.example.smartphonapptest001.data.network.toOllamaNativeRequest
import com.example.smartphonapptest001.data.network.toOpenAiRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultChatRepository(
    private val api: KtorOpenAiCompatibleChatApi,
    private val ollamaNativeApi: KtorOllamaNativeChatApi,
    private val localModelService: LocalModelService,
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

                ProviderType.OLLAMA_CLOUD -> {
                    val ollamaApiMode = settings.ollamaApiMode()
                    when (ollamaApiMode) {
                        OllamaApiMode.OPENAI_COMPATIBLE -> {
                            logger.log(
                                AppLogSeverity.INFO,
                                TAG,
                                "Ollama Cloud completion request configuration",
                                details = structuredDetails(
                                    "ollamaApiMode" to ollamaApiMode.logValue,
                                    "baseUrl" to settings.activeEndpointConfig.baseUrl,
                                    "endpoint" to settings.activeEndpointConfig.baseUrl.toOpenAiChatCompletionsUrlForLog(),
                                    "model" to settings.activeEndpointConfig.model,
                                    "apiKeyPresent" to !settings.activeEndpointConfig.apiKey.isNullOrBlank(),
                                    "apiKeyChars" to (settings.activeEndpointConfig.apiKey?.length ?: 0),
                                    "stream" to false,
                                    "messageCount" to messages.size,
                                ),
                            )
                            val response = api.complete(
                                settings.activeEndpointConfig,
                                messages.toOpenAiRequest(model = settings.activeEndpointConfig.model, settings = settings),
                            )
                            val reply = response.choices.firstOrNull()?.message?.content.orEmpty()
                            logger.log(
                                AppLogSeverity.INFO,
                                TAG,
                                "Ollama Cloud completion response received",
                                details = structuredDetails(
                                    "ollamaApiMode" to ollamaApiMode.logValue,
                                    "model" to settings.activeEndpointConfig.model,
                                    "choiceCount" to response.choices.size,
                                    "finishReason" to response.choices.firstOrNull()?.finishReason.orEmpty(),
                                    "replyChars" to reply.length,
                                    "replyPreview" to reply.take(240),
                                ),
                            )
                            logger.log(
                                AppLogSeverity.INFO,
                                TAG,
                                "Completion finished",
                                details = "replyChars=${reply.length}\nreply=$reply",
                            )
                            reply
                        }

                        OllamaApiMode.NATIVE_CHAT -> {
                            logger.log(
                                AppLogSeverity.INFO,
                                TAG,
                                "Ollama Cloud completion request configuration",
                                details = structuredDetails(
                                    "ollamaApiMode" to ollamaApiMode.logValue,
                                    "baseUrl" to settings.activeEndpointConfig.baseUrl,
                                    "endpoint" to settings.activeEndpointConfig.baseUrl.toOllamaNativeChatUrlForLog(),
                                    "model" to settings.activeEndpointConfig.model,
                                    "apiKeyPresent" to !settings.activeEndpointConfig.apiKey.isNullOrBlank(),
                                    "apiKeyChars" to (settings.activeEndpointConfig.apiKey?.length ?: 0),
                                    "stream" to false,
                                    "messageCount" to messages.size,
                                ),
                            )
                            val response = ollamaNativeApi.complete(
                                settings.activeEndpointConfig,
                                messages.toOllamaNativeRequest(
                                    model = settings.activeEndpointConfig.model,
                                    settings = settings,
                                ),
                            )
                            val reply = response.message?.content.orEmpty()
                            logger.log(
                                AppLogSeverity.INFO,
                                TAG,
                                "Ollama Cloud completion response received",
                                details = structuredDetails(
                                    "ollamaApiMode" to ollamaApiMode.logValue,
                                    "model" to settings.activeEndpointConfig.model,
                                    "done" to response.done,
                                    "doneReason" to response.doneReason.orEmpty(),
                                    "replyChars" to reply.length,
                                    "replyPreview" to reply.take(240),
                                ),
                            )
                            logger.log(
                                AppLogSeverity.INFO,
                                TAG,
                                "Completion finished",
                                details = "replyChars=${reply.length}\nreply=$reply",
                            )
                            reply
                        }
                    }
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

                ProviderType.OLLAMA_CLOUD -> {
                    val ollamaApiMode = settings.ollamaApiMode()
                    if (ollamaApiMode == OllamaApiMode.OPENAI_COMPATIBLE) {
                        logger.log(
                            AppLogSeverity.INFO,
                            TAG,
                            "Ollama Cloud streaming request configuration",
                            details = structuredDetails(
                                "ollamaApiMode" to ollamaApiMode.logValue,
                                "baseUrl" to settings.activeEndpointConfig.baseUrl,
                                "endpoint" to settings.activeEndpointConfig.baseUrl.toOpenAiChatCompletionsUrlForLog(),
                                "model" to settings.activeEndpointConfig.model,
                                "apiKeyPresent" to !settings.activeEndpointConfig.apiKey.isNullOrBlank(),
                                "apiKeyChars" to (settings.activeEndpointConfig.apiKey?.length ?: 0),
                                "stream" to true,
                                "messageCount" to messages.size,
                            ),
                        )
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
                    } else {
                        logger.log(
                            AppLogSeverity.INFO,
                            TAG,
                            "Ollama Cloud streaming request configuration",
                            details = structuredDetails(
                                "ollamaApiMode" to ollamaApiMode.logValue,
                                "baseUrl" to settings.activeEndpointConfig.baseUrl,
                                "endpoint" to settings.activeEndpointConfig.baseUrl.toOllamaNativeChatUrlForLog(),
                                "model" to settings.activeEndpointConfig.model,
                                "apiKeyPresent" to !settings.activeEndpointConfig.apiKey.isNullOrBlank(),
                                "apiKeyChars" to (settings.activeEndpointConfig.apiKey?.length ?: 0),
                                "stream" to true,
                                "messageCount" to messages.size,
                            ),
                        )
                        ollamaNativeApi.stream(
                            settings.activeEndpointConfig,
                            messages.toOllamaNativeRequest(
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

private fun String.toOpenAiChatCompletionsUrlForLog(): String {
    val normalizedBase = trim().trimEnd('/')
    val apiBase = if (normalizedBase.endsWith("/v1")) normalizedBase else "$normalizedBase/v1"
    return "$apiBase/chat/completions"
}

private fun String.toOllamaNativeChatUrlForLog(): String {
    val normalizedBase = trim().trimEnd('/')
    val apiBase = if (normalizedBase.endsWith("/v1")) normalizedBase.removeSuffix("/v1") else normalizedBase
    return "$apiBase/api/chat"
}

private enum class OllamaApiMode(val logValue: String) {
    OPENAI_COMPATIBLE("openai_compatible"),
    NATIVE_CHAT("native_chat"),
}

private fun AppSettings.ollamaApiMode(): OllamaApiMode {
    val model = activeEndpointConfig.model.trim().lowercase()
    return if (model.startsWith("gemma4:31b-cloud")) {
        OllamaApiMode.OPENAI_COMPATIBLE
    } else {
        OllamaApiMode.NATIVE_CHAT
    }
}
