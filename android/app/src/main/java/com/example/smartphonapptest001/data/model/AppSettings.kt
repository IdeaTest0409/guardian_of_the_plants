package com.example.smartphonapptest001.data.model

data class AppSettings(
    val providerType: ProviderType = DEFAULT_PROVIDER_TYPE,
    val plantSpecies: PlantSpecies = PlantSpecies.default(),
    val companionRole: CompanionRole = CompanionRole.default(),
    val guardianAngelPersonality: GuardianAngelPersonality = GuardianAngelPersonality.default(),
    val avatarPresentationMode: AvatarPresentationMode = AvatarPresentationMode.default(),
    val avatarExpressionEnabled: Boolean = DEFAULT_AVATAR_EXPRESSION_ENABLED,
    val chatStatusBarVisible: Boolean = DEFAULT_CHAT_STATUS_BAR_VISIBLE,
    val mrAvatarMotionEnabled: Boolean = DEFAULT_MR_AVATAR_MOTION_ENABLED,
    val mrModelControlsVisible: Boolean = DEFAULT_MR_MODEL_CONTROLS_VISIBLE,
    val mrModelOffsetX: Float = DEFAULT_MR_MODEL_OFFSET_X,
    val mrModelOffsetY: Float = DEFAULT_MR_MODEL_OFFSET_Y,
    val mrModelScale: Float = DEFAULT_MR_MODEL_SCALE,
    val mrModelCameraDistance: Float = DEFAULT_MR_MODEL_CAMERA_DISTANCE,
    val mrModelYawDegrees: Float = DEFAULT_MR_MODEL_YAW_DEGREES,
    val mrModelTiltDegrees: Float = DEFAULT_MR_MODEL_TILT_DEGREES,
    val aiImageQuality: AiImageQuality = AiImageQuality.default(),
    val localBaseUrl: String = DEFAULT_LOCAL_BASE_URL,
    val localModel: String = DEFAULT_LOCAL_MODEL,
    val localExecutionBackend: LocalExecutionBackend = LocalExecutionBackend.default(),
    val cloudBaseUrl: String = DEFAULT_CLOUD_BASE_URL,
    val cloudModel: String = DEFAULT_CLOUD_MODEL,
    val cloudApiKey: String = "",
    val ollamaCloudBaseUrl: String = DEFAULT_OLLAMA_CLOUD_BASE_URL,
    val ollamaCloudModel: String = DEFAULT_OLLAMA_CLOUD_MODEL,
    val ollamaCloudApiKey: String = DEFAULT_OLLAMA_CLOUD_API_KEY,
    val streamResponses: Boolean = false,
    val speakAssistantReplies: Boolean = DEFAULT_SPEAK_ASSISTANT_REPLIES,
    val ttsVoiceProfile: TtsVoiceProfile = TtsVoiceProfile.default(),
    val ttsSpeechRateMultiplier: Double = DEFAULT_TTS_SPEECH_RATE_MULTIPLIER,
    val autoSmallTalkInterval: AutoSmallTalkInterval = AutoSmallTalkInterval.default(),
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val topK: Int = DEFAULT_TOP_K,
    val topP: Double = DEFAULT_TOP_P,
    val temperature: Double = DEFAULT_TEMPERATURE,
    val thinkingEnabled: Boolean = DEFAULT_THINKING_ENABLED,
) {
    val activeProvider: AIProvider
        get() = when (providerType) {
            ProviderType.LOCAL -> AIProvider.Local(
                endpointConfig = activeEndpointConfig,
                modelConfig = ModelConfig(
                    id = resolvedLocalModel,
                    displayName = resolvedLocalModel,
                ),
            )

            ProviderType.CLOUD -> AIProvider.Cloud(
                endpointConfig = activeEndpointConfig,
                modelConfig = ModelConfig(
                    id = resolvedCloudModel,
                    displayName = resolvedCloudModel,
                ),
            )

            ProviderType.OLLAMA_CLOUD -> AIProvider.Cloud(
                type = ProviderType.OLLAMA_CLOUD,
                endpointConfig = activeEndpointConfig,
                modelConfig = ModelConfig(
                    id = resolvedOllamaCloudModel,
                    displayName = resolvedOllamaCloudModel,
                ),
            )
        }

    val plantDisplayName: String
        get() = plantSpecies.label

    val companionDisplayName: String
        get() = companionRole.label

    val guardianAngelDisplayName: String
        get() = guardianAngelPersonality.label

    val activeEndpointConfig: EndpointConfig
        get() = when (providerType) {
            ProviderType.LOCAL -> EndpointConfig(
                baseUrl = "on-device",
                model = resolvedLocalModel,
                apiKey = null,
                streamResponses = streamResponses,
            )

            ProviderType.CLOUD -> EndpointConfig(
                baseUrl = cloudBaseUrl.trim(),
                model = resolvedCloudModel,
                apiKey = cloudApiKey.trim().ifBlank { null },
                streamResponses = streamResponses,
            )

            ProviderType.OLLAMA_CLOUD -> EndpointConfig(
                baseUrl = ollamaCloudBaseUrl.trim().ifBlank { DEFAULT_OLLAMA_CLOUD_BASE_URL },
                model = resolvedOllamaCloudModel,
                apiKey = ollamaCloudApiKey.trim().ifBlank { null },
                streamResponses = streamResponses,
            )
        }

    private val resolvedLocalModel: String
        get() = localModel.trim().ifBlank { DEFAULT_LOCAL_MODEL }

    private val resolvedCloudModel: String
        get() = cloudModel.trim().ifBlank { DEFAULT_CLOUD_MODEL }

    private val resolvedOllamaCloudModel: String
        get() = ollamaCloudModel.trim()
            .takeIf { it in ENABLED_OLLAMA_CLOUD_MODELS }
            ?: DEFAULT_OLLAMA_CLOUD_MODEL

    companion object {
        const val DEFAULT_LOCAL_BASE_URL = "on-device"
        val DEFAULT_PROVIDER_TYPE = ProviderType.OLLAMA_CLOUD
        const val DEFAULT_LOCAL_MODEL = "gemma-4-E2B-it"
        const val DEFAULT_CLOUD_BASE_URL = "http://192.168.0.11:1234"
        const val DEFAULT_CLOUD_MODEL = "gemma-4-e2b"
        const val DEFAULT_OLLAMA_CLOUD_BASE_URL = "https://ollama.com/v1"
        const val DEFAULT_OLLAMA_CLOUD_MODEL = "gemma4:31b-cloud"
        const val DEFAULT_OLLAMA_CLOUD_API_KEY = ""
        val ENABLED_OLLAMA_CLOUD_MODELS = setOf(
            "gemma4:31b-cloud",
        )
        val OLLAMA_CLOUD_MODELS = listOf(
            "gemma4:31b-cloud",
            "kimi-k2.6:cloud",
            "qwen3.6:35b",
        )
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2000
        const val DEFAULT_TOP_K = 64
        const val DEFAULT_TOP_P = 0.95
        const val DEFAULT_TEMPERATURE = 1.0
        const val DEFAULT_THINKING_ENABLED = false
        const val DEFAULT_SPEAK_ASSISTANT_REPLIES = true
        const val DEFAULT_TTS_SPEECH_RATE_MULTIPLIER = 1.2
        const val DEFAULT_AVATAR_EXPRESSION_ENABLED = false
        const val DEFAULT_CHAT_STATUS_BAR_VISIBLE = false
        const val DEFAULT_MR_AVATAR_MOTION_ENABLED = false
        const val DEFAULT_MR_MODEL_CONTROLS_VISIBLE = false
        const val DEFAULT_MR_MODEL_OFFSET_X = -0.34f
        const val DEFAULT_MR_MODEL_OFFSET_Y = -0.32f
        const val DEFAULT_MR_MODEL_SCALE = 1.14f
        const val DEFAULT_MR_MODEL_CAMERA_DISTANCE = 0.43f
        const val DEFAULT_MR_MODEL_YAW_DEGREES = 0.0f
        const val DEFAULT_MR_MODEL_TILT_DEGREES = 40.0f
    }
}
