package com.example.smartphonapptest001.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.smartphonapptest001.data.SettingsRepository
import com.example.smartphonapptest001.data.local.LocalModelDownloadState
import com.example.smartphonapptest001.data.local.LocalModelService
import com.example.smartphonapptest001.data.local.LocalModelServiceState
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.model.AiImageQuality
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.AvatarPresentationMode
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.data.model.GuardianAngelPersonality
import com.example.smartphonapptest001.data.model.LocalExecutionBackend
import com.example.smartphonapptest001.data.model.LocalModelPreset
import com.example.smartphonapptest001.data.model.PlantImageSelectionMode
import com.example.smartphonapptest001.data.model.PlantSpecies
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AutoSmallTalkInterval
import com.example.smartphonapptest001.data.model.TtsVoiceProfile
import com.example.smartphonapptest001.data.model.VoiceVoxSpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SettingsUiState(
    val persistedSettings: AppSettings = AppSettings(),
    val providerType: ProviderType = AppSettings.DEFAULT_PROVIDER_TYPE,
    val plantSpecies: PlantSpecies = PlantSpecies.default(),
    val companionRole: CompanionRole = CompanionRole.default(),
    val guardianAngelPersonality: GuardianAngelPersonality = GuardianAngelPersonality.default(),
    val avatarPresentationMode: AvatarPresentationMode = AvatarPresentationMode.default(),
    val avatarExpressionEnabled: Boolean = AppSettings.DEFAULT_AVATAR_EXPRESSION_ENABLED,
    val chatStatusBarVisible: Boolean = AppSettings.DEFAULT_CHAT_STATUS_BAR_VISIBLE,
    val mrAvatarMotionEnabled: Boolean = AppSettings.DEFAULT_MR_AVATAR_MOTION_ENABLED,
    val mrModelControlsVisible: Boolean = AppSettings.DEFAULT_MR_MODEL_CONTROLS_VISIBLE,
    val mrModelOffsetX: Float = AppSettings.DEFAULT_MR_MODEL_OFFSET_X,
    val mrModelOffsetY: Float = AppSettings.DEFAULT_MR_MODEL_OFFSET_Y,
    val mrModelScale: Float = AppSettings.DEFAULT_MR_MODEL_SCALE,
    val mrModelCameraDistance: Float = AppSettings.DEFAULT_MR_MODEL_CAMERA_DISTANCE,
    val mrModelYawDegrees: Float = AppSettings.DEFAULT_MR_MODEL_YAW_DEGREES,
    val mrModelTiltDegrees: Float = AppSettings.DEFAULT_MR_MODEL_TILT_DEGREES,
    val aiImageQuality: AiImageQuality = AiImageQuality.default(),
    val localExecutionBackend: LocalExecutionBackend = LocalExecutionBackend.default(),
    val localModel: String = AppSettings.DEFAULT_LOCAL_MODEL,
    val cloudBaseUrl: String = AppSettings.DEFAULT_CLOUD_BASE_URL,
    val cloudModel: String = AppSettings.DEFAULT_CLOUD_MODEL,
    val cloudApiKey: String = "",
    val streamResponses: Boolean = false,
    val speakAssistantReplies: Boolean = false,
    val ttsVoiceProfile: TtsVoiceProfile = TtsVoiceProfile.default(),
    val ttsSpeechRateMultiplier: Double = AppSettings.DEFAULT_TTS_SPEECH_RATE_MULTIPLIER,
    val voiceVoxEnabled: Boolean = AppSettings.DEFAULT_VOICEVOX_ENABLED,
    val voiceVoxSpeaker: VoiceVoxSpeaker = VoiceVoxSpeaker.default(),
    val autoSmallTalkInterval: AutoSmallTalkInterval = AutoSmallTalkInterval.default(),
    val maxOutputTokens: Int = AppSettings.DEFAULT_MAX_OUTPUT_TOKENS,
    val topK: Int = AppSettings.DEFAULT_TOP_K,
    val topP: Double = AppSettings.DEFAULT_TOP_P,
    val temperature: Double = AppSettings.DEFAULT_TEMPERATURE,
    val thinkingEnabled: Boolean = AppSettings.DEFAULT_THINKING_ENABLED,
    val plantImageSelectionMode: PlantImageSelectionMode = PlantImageSelectionMode.default(),
    val selectedPlantImage: Attachment? = null,
    val localModelServiceState: LocalModelServiceState = LocalModelServiceState(),
    val isSaving: Boolean = false,
    val isDownloadingLocalModel: Boolean = false,
    val isImportingLocalModel: Boolean = false,
    val isStartingLocalModel: Boolean = false,
    val statusMessage: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val localModelService: LocalModelService,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var autoStartAttempted = false

    init {
        viewModelScope.launch {
            repository.settings.collectLatest { settings ->
                logger.log(
                    AppLogSeverity.INFO,
                    TAG,
                    "Settings loaded",
                    details = buildString {
                        appendLine("provider=${settings.providerType}")
                        appendLine("localModel=${settings.localModel}")
                        appendLine("cloudBaseUrl=${settings.cloudBaseUrl}")
                        appendLine("cloudModel=${settings.cloudModel}")
                        appendLine("streamResponses=${settings.streamResponses}")
                        appendLine("speakAssistantReplies=${settings.speakAssistantReplies}")
                        appendLine("ttsVoiceProfile=${settings.ttsVoiceProfile.name}")
                        appendLine("ttsSpeechRateMultiplier=${settings.ttsSpeechRateMultiplier}")
                        appendLine("autoSmallTalkInterval=${settings.autoSmallTalkInterval.name}")
                        appendLine("avatarExpressionEnabled=${settings.avatarExpressionEnabled}")
                        appendLine("chatStatusBarVisible=${settings.chatStatusBarVisible}")
                        appendLine("mrAvatarMotionEnabled=${settings.mrAvatarMotionEnabled}")
                        appendLine("mrModelControlsVisible=${settings.mrModelControlsVisible}")
                        appendLine("mrModelOffsetX=${settings.mrModelOffsetX}")
                        appendLine("mrModelOffsetY=${settings.mrModelOffsetY}")
                        appendLine("mrModelScale=${settings.mrModelScale}")
                        appendLine("mrModelCameraDistance=${settings.mrModelCameraDistance}")
                        appendLine("mrModelYawDegrees=${settings.mrModelYawDegrees}")
                        appendLine("mrModelTiltDegrees=${settings.mrModelTiltDegrees}")
                        appendLine("aiImageQuality=${settings.aiImageQuality.name}")
                        appendLine("companionRole=${settings.companionRole.label}")
                    },
                )
                _uiState.update {
                    it.copy(
                        persistedSettings = settings,
                        providerType = settings.providerType,
                        plantSpecies = settings.plantSpecies,
                        companionRole = settings.companionRole,
                        guardianAngelPersonality = settings.guardianAngelPersonality,
                        avatarPresentationMode = settings.avatarPresentationMode,
                        avatarExpressionEnabled = settings.avatarExpressionEnabled,
                        chatStatusBarVisible = settings.chatStatusBarVisible,
                        mrAvatarMotionEnabled = settings.mrAvatarMotionEnabled,
                        mrModelControlsVisible = settings.mrModelControlsVisible,
                        mrModelOffsetX = settings.mrModelOffsetX,
                        mrModelOffsetY = settings.mrModelOffsetY,
                        mrModelScale = settings.mrModelScale,
                        mrModelCameraDistance = settings.mrModelCameraDistance,
                        mrModelYawDegrees = settings.mrModelYawDegrees,
                        mrModelTiltDegrees = settings.mrModelTiltDegrees,
                        aiImageQuality = settings.aiImageQuality,
                        localExecutionBackend = settings.localExecutionBackend,
                        localModel = settings.localModel,
                        cloudBaseUrl = settings.cloudBaseUrl.ifBlank { AppSettings.DEFAULT_CLOUD_BASE_URL },
                        cloudModel = settings.cloudModel.ifBlank { AppSettings.DEFAULT_CLOUD_MODEL },
                        cloudApiKey = settings.cloudApiKey,
                        streamResponses = settings.streamResponses,
                        speakAssistantReplies = settings.speakAssistantReplies,
                        ttsVoiceProfile = settings.ttsVoiceProfile,
                        ttsSpeechRateMultiplier = settings.ttsSpeechRateMultiplier,
                        voiceVoxEnabled = settings.voiceVoxEnabled,
                        voiceVoxSpeaker = settings.voiceVoxSpeaker,
                        autoSmallTalkInterval = settings.autoSmallTalkInterval,
                        maxOutputTokens = settings.maxOutputTokens,
                        topK = settings.topK,
                        topP = settings.topP,
                        temperature = settings.temperature,
                        thinkingEnabled = settings.thinkingEnabled,
                    )
                }
                maybeAutoStartLocalModel(settings)
            }
        }

        viewModelScope.launch {
            localModelService.state.collectLatest { serviceState ->
                _uiState.update { it.copy(localModelServiceState = serviceState) }
            }
        }
    }

    private fun maybeAutoStartLocalModel(settings: AppSettings) {
        if (autoStartAttempted) return
        if (settings.providerType != ProviderType.LOCAL) return
        autoStartAttempted = true
        logger.log(
            AppLogSeverity.WARN,
            TAG,
            "Local auto-start is disabled; keeping text chat on cloud for stability",
            details = "modelId=${settings.localModel}\nbackend=${settings.localExecutionBackend.name}",
        )
    }

    fun onProviderTypeChange(providerType: ProviderType) {
        _uiState.update { it.copy(providerType = providerType, statusMessage = null) }
    }

    fun onPlantSpeciesChange(plantSpecies: PlantSpecies) {
        _uiState.update { it.copy(plantSpecies = plantSpecies) }
    }

    fun onCompanionRoleChange(role: CompanionRole) {
        _uiState.update {
            it.copy(
                companionRole = role,
                ttsVoiceProfile = recommendedTtsVoiceProfile(
                    companionRole = role,
                    personality = it.guardianAngelPersonality,
                ),
            )
        }
    }

    fun onGuardianAngelPersonalityChange(personality: GuardianAngelPersonality) {
        _uiState.update {
            it.copy(
                guardianAngelPersonality = personality,
                ttsVoiceProfile = recommendedTtsVoiceProfile(
                    companionRole = it.companionRole,
                    personality = personality,
                ),
            )
        }
    }

    fun onAvatarPresentationModeChange(mode: AvatarPresentationMode) {
        _uiState.update { it.copy(avatarPresentationMode = mode) }
    }

    fun onAvatarExpressionEnabledChange(value: Boolean) {
        _uiState.update { it.copy(avatarExpressionEnabled = value) }
    }

    fun onChatStatusBarVisibleChange(value: Boolean) {
        _uiState.update { it.copy(chatStatusBarVisible = value) }
    }

    fun onMrAvatarMotionEnabledChange(value: Boolean) {
        _uiState.update { it.copy(mrAvatarMotionEnabled = value) }
    }

    fun onMrModelControlsVisibleChange(value: Boolean) {
        _uiState.update { it.copy(mrModelControlsVisible = value) }
    }

    fun onMrModelOffsetXChange(value: Float) {
        _uiState.update { it.copy(mrModelOffsetX = value.coerceIn(-0.85f, 0.85f)) }
    }

    fun onMrModelOffsetYChange(value: Float) {
        _uiState.update { it.copy(mrModelOffsetY = value.coerceIn(-0.85f, 1.2f)) }
    }

    fun onMrModelScaleChange(value: Float) {
        _uiState.update { it.copy(mrModelScale = value.coerceIn(0.2f, 1.5f)) }
    }

    fun onMrModelCameraDistanceChange(value: Float) {
        _uiState.update { it.copy(mrModelCameraDistance = value.coerceIn(0.22f, 1.6f)) }
    }

    fun onMrModelYawDegreesChange(value: Float) {
        _uiState.update { it.copy(mrModelYawDegrees = value.coerceIn(-180f, 180f)) }
    }

    fun onMrModelTiltDegreesChange(value: Float) {
        _uiState.update { it.copy(mrModelTiltDegrees = value.coerceIn(-45f, 45f)) }
    }

    fun resetMrModelPlacement() {
        _uiState.update {
            it.copy(
                mrModelOffsetX = AppSettings.DEFAULT_MR_MODEL_OFFSET_X,
                mrModelOffsetY = AppSettings.DEFAULT_MR_MODEL_OFFSET_Y,
                mrModelScale = AppSettings.DEFAULT_MR_MODEL_SCALE,
                mrModelCameraDistance = AppSettings.DEFAULT_MR_MODEL_CAMERA_DISTANCE,
                mrModelYawDegrees = AppSettings.DEFAULT_MR_MODEL_YAW_DEGREES,
                mrModelTiltDegrees = AppSettings.DEFAULT_MR_MODEL_TILT_DEGREES,
                statusMessage = "MR model placement reset",
            )
        }
    }

    fun onAiImageQualityChange(quality: AiImageQuality) {
        _uiState.update { it.copy(aiImageQuality = quality) }
    }

    fun onLocalExecutionBackendChange(backend: LocalExecutionBackend) {
        _uiState.update { it.copy(localExecutionBackend = backend) }
    }

    fun onLocalModelChange(value: String) {
        _uiState.update { it.copy(localModel = value) }
    }

    fun onCloudBaseUrlChange(value: String) {
        _uiState.update { it.copy(cloudBaseUrl = value) }
    }

    fun onCloudModelChange(value: String) {
        _uiState.update { it.copy(cloudModel = value) }
    }

    fun onCloudApiKeyChange(value: String) {
        _uiState.update { it.copy(cloudApiKey = value) }
    }

    fun onStreamResponsesChange(value: Boolean) {
        _uiState.update { it.copy(streamResponses = value) }
    }

    fun onSpeakAssistantRepliesChange(value: Boolean) {
        _uiState.update { it.copy(speakAssistantReplies = value) }
    }

    fun onTtsVoiceProfileChange(value: TtsVoiceProfile) {
        _uiState.update { it.copy(ttsVoiceProfile = value) }
    }

    fun onTtsSpeechRateMultiplierChange(value: Double) {
        _uiState.update { it.copy(ttsSpeechRateMultiplier = value.roundToSpeechRateStep()) }
    }

    fun onVoiceVoxEnabledChange(value: Boolean) {
        _uiState.update { it.copy(voiceVoxEnabled = value) }
    }

    fun onVoiceVoxSpeakerChange(value: VoiceVoxSpeaker) {
        _uiState.update { it.copy(voiceVoxSpeaker = value) }
    }

    fun onAutoSmallTalkIntervalChange(value: AutoSmallTalkInterval) {
        _uiState.update { it.copy(autoSmallTalkInterval = value) }
    }

    fun onMaxOutputTokensChange(value: Int) {
        _uiState.update { it.copy(maxOutputTokens = value.coerceAtLeast(1)) }
    }

    fun onTopKChange(value: Int) {
        _uiState.update { it.copy(topK = value.coerceIn(1, 128)) }
    }

    fun onTopPChange(value: Double) {
        _uiState.update { it.copy(topP = value.coerceIn(0.0, 1.0)) }
    }

    fun onTemperatureChange(value: Double) {
        _uiState.update { it.copy(temperature = value.coerceIn(0.0, 2.0)) }
    }

    fun onThinkingEnabledChange(value: Boolean) {
        _uiState.update { it.copy(thinkingEnabled = value) }
    }

    fun onPlantImageSelectionModeChange(mode: PlantImageSelectionMode) {
        _uiState.update {
            it.copy(
                plantImageSelectionMode = mode,
                selectedPlantImage = if (mode == PlantImageSelectionMode.REALTIME_CAPTURE) null else it.selectedPlantImage,
                statusMessage = when (mode) {
                    PlantImageSelectionMode.REALTIME_CAPTURE -> "Realtime camera preview enabled"
                    PlantImageSelectionMode.CAMERA_PHOTO -> "Camera photo mode selected"
                    PlantImageSelectionMode.FILE_UPLOAD -> "File upload mode selected"
                },
            )
        }
    }

    fun onPlantImageCaptured(attachment: Attachment) {
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Plant image captured",
            details = buildString {
                appendLine("displayName=${attachment.displayName}")
                appendLine("mimeType=${attachment.mimeType}")
                appendLine("kind=${attachment.kind}")
                appendLine("hasDataUrl=${!attachment.dataUrl.isNullOrBlank()}")
                appendLine("dataUrlChars=${attachment.dataUrl?.length ?: 0}")
            },
        )
        _uiState.update { it.copy(selectedPlantImage = attachment, statusMessage = "Plant image selected") }
    }

    fun onRealtimePlantImageCaptured(attachment: Attachment) {
        _uiState.update { it.copy(selectedPlantImage = attachment) }
    }

    fun clearPlantImage() {
        logger.log(AppLogSeverity.INFO, TAG, "Plant image cleared")
        _uiState.update { it.copy(selectedPlantImage = null, statusMessage = "Plant image cleared") }
    }

    fun downloadLocalModel() {
        val selectedPreset = LocalModelPreset.fromModelId(_uiState.value.localModel)
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingLocalModel = true, statusMessage = null) }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Downloading local model",
                details = buildString {
                    appendLine("modelId=${selectedPreset.modelId}")
                    appendLine("sourceUrl=${selectedPreset.downloadUrl}")
                },
            )
            runCatching { localModelService.downloadModel(selectedPreset) }
                .onSuccess { file ->
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Local model downloaded",
                        details = buildString {
                            appendLine("modelId=${selectedPreset.modelId}")
                            appendLine("path=${file.absolutePath}")
                            appendLine("sizeBytes=${file.length()}")
                        },
                    )
                    _uiState.update {
                        it.copy(
                            isDownloadingLocalModel = false,
                            statusMessage = "Downloaded ${selectedPreset.label}",
                        )
                    }
                }
                .onFailure { error ->
                    logger.log(
                        AppLogSeverity.ERROR,
                        TAG,
                        "Local model download failed",
                        details = error.toStructuredLogDetails(
                            "phase" to "download",
                            "modelId" to selectedPreset.modelId,
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isDownloadingLocalModel = false,
                            statusMessage = error.message ?: "Download failed",
                        )
                    }
                }
        }
    }

    fun importLocalModel(sourceUri: Uri) {
        val selectedPreset = LocalModelPreset.fromModelId(_uiState.value.localModel)
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingLocalModel = true, statusMessage = null) }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Importing local model file",
                details = buildString {
                    appendLine("modelId=${selectedPreset.modelId}")
                    appendLine("sourceUri=${sourceUri}")
                },
            )
            runCatching { localModelService.importModelFile(selectedPreset, sourceUri) }
                .onSuccess { file ->
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Local model file imported",
                        details = buildString {
                            appendLine("modelId=${selectedPreset.modelId}")
                            appendLine("path=${file.absolutePath}")
                            appendLine("sizeBytes=${file.length()}")
                        },
                    )
                    _uiState.update {
                        it.copy(
                            isImportingLocalModel = false,
                            statusMessage = "Imported ${selectedPreset.label}",
                        )
                    }
                }
                .onFailure { error ->
                    logger.log(
                        AppLogSeverity.ERROR,
                        TAG,
                        "Local model file import failed",
                        details = error.toStructuredLogDetails(
                            "phase" to "import",
                            "modelId" to selectedPreset.modelId,
                            "sourceUri" to sourceUri.toString(),
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isImportingLocalModel = false,
                            statusMessage = error.message ?: "Import failed",
                        )
                    }
                }
        }
    }

    fun startLocalModel() {
        val selectedPreset = LocalModelPreset.fromModelId(_uiState.value.localModel)
        val state = _uiState.value
        val next = AppSettings(
            providerType = ProviderType.LOCAL,
            plantSpecies = state.plantSpecies,
            companionRole = state.companionRole,
            guardianAngelPersonality = state.guardianAngelPersonality,
            avatarPresentationMode = state.avatarPresentationMode,
            avatarExpressionEnabled = false,
            chatStatusBarVisible = state.chatStatusBarVisible,
            mrAvatarMotionEnabled = state.mrAvatarMotionEnabled,
            mrModelControlsVisible = state.mrModelControlsVisible,
            mrModelOffsetX = state.mrModelOffsetX,
            mrModelOffsetY = state.mrModelOffsetY,
            mrModelScale = state.mrModelScale,
            mrModelCameraDistance = state.mrModelCameraDistance,
            mrModelYawDegrees = state.mrModelYawDegrees,
            mrModelTiltDegrees = state.mrModelTiltDegrees,
            aiImageQuality = state.aiImageQuality,
            localExecutionBackend = state.localExecutionBackend,
            localBaseUrl = state.persistedSettings.localBaseUrl,
            localModel = state.localModel,
            cloudBaseUrl = state.cloudBaseUrl.ifBlank { AppSettings.DEFAULT_CLOUD_BASE_URL },
            cloudModel = state.cloudModel.ifBlank { AppSettings.DEFAULT_CLOUD_MODEL },
            cloudApiKey = state.cloudApiKey,
            streamResponses = state.streamResponses,
            speakAssistantReplies = state.speakAssistantReplies,
            ttsVoiceProfile = state.ttsVoiceProfile,
            ttsSpeechRateMultiplier = state.ttsSpeechRateMultiplier,
            voiceVoxEnabled = state.voiceVoxEnabled,
            voiceVoxSpeaker = state.voiceVoxSpeaker,
            autoSmallTalkInterval = state.autoSmallTalkInterval,
            maxOutputTokens = state.maxOutputTokens,
            topK = state.topK,
            topP = state.topP,
            temperature = state.temperature,
            thinkingEnabled = state.thinkingEnabled,
        )
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    providerType = ProviderType.LOCAL,
                    isStartingLocalModel = true,
                    statusMessage = "Starting ${selectedPreset.label}...",
                )
            }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Saving settings before local start",
                details = next.toSafeLogString(),
            )
            runCatching { repository.save(next) }
                .onSuccess {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Local start requested",
                        details = buildString {
                            appendLine("modelId=${selectedPreset.modelId}")
                            appendLine("backend=${state.localExecutionBackend.name}")
                            appendLine("downloadState=${selectedLocalModelDownloadState.status}")
                            appendLine("runtimeRunning=${state.localModelServiceState.runtime.isRunning}")
                            appendLine("runtimeActive=${state.localModelServiceState.runtime.activeModelId}")
                        },
                    )
                    startSelectedModel(selectedPreset, state.localExecutionBackend, next)
                }
                .onFailure { error ->
                    logger.log(
                        AppLogSeverity.ERROR,
                        TAG,
                        "Saving settings before local start failed",
                        details = error.toStructuredLogDetails(
                            "phase" to "save_before_local_start",
                            "modelId" to selectedPreset.modelId,
                            "backend" to state.localExecutionBackend.name,
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isStartingLocalModel = false,
                            statusMessage = error.message ?: "Start failed",
                        )
                    }
                }
        }
    }

    private fun startSelectedModel(
        preset: LocalModelPreset,
        backend: LocalExecutionBackend,
        settings: AppSettings,
    ) {
        viewModelScope.launch {
            runCatching { localModelService.startModel(preset, backend, settings) }
                .onSuccess {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Local model started",
                        details = "modelId=${preset.modelId}\nbackend=${backend.name}",
                    )
                    _uiState.update {
                        it.copy(
                            isStartingLocalModel = false,
                            statusMessage = "Started ${preset.label} (${backend.label})",
                        )
                    }
                }
                .onFailure { error ->
                    val shouldFallback = backend != LocalExecutionBackend.DEFAULT
                    if (shouldFallback) {
                        logger.log(
                            AppLogSeverity.WARN,
                            TAG,
                            "Local model start failed, retrying with default backend",
                            details = error.toStructuredLogDetails(
                                "phase" to "start_retry",
                                "modelId" to preset.modelId,
                                "selectedBackend" to backend.name,
                                "fallbackBackend" to LocalExecutionBackend.DEFAULT.name,
                            ),
                        )
                        runCatching { localModelService.startModel(preset, LocalExecutionBackend.DEFAULT, settings) }
                            .onSuccess {
                                logger.log(
                                    AppLogSeverity.INFO,
                                    TAG,
                                    "Local model started with fallback backend",
                                    details = "modelId=${preset.modelId}\nbackend=${LocalExecutionBackend.DEFAULT.name}",
                                )
                                _uiState.update {
                                    it.copy(
                                        isStartingLocalModel = false,
                                        statusMessage = "Started ${preset.label} (${LocalExecutionBackend.DEFAULT.label})",
                                    )
                                }
                            }
                            .onFailure { fallbackError ->
                                logger.log(
                                    AppLogSeverity.ERROR,
                                    TAG,
                                    "Local model start failed",
                                    details = fallbackError.toStructuredLogDetails(
                                        "phase" to "start_fallback",
                                        "modelId" to preset.modelId,
                                        "backend" to LocalExecutionBackend.DEFAULT.name,
                                    ),
                                )
                                _uiState.update {
                                    it.copy(
                                        isStartingLocalModel = false,
                                        statusMessage = localStartFailureMessage(preset.modelId, fallbackError),
                                    )
                                }
                            }
                    } else {
                        logger.log(
                            AppLogSeverity.ERROR,
                            TAG,
                            "Local model start failed",
                            details = error.toStructuredLogDetails(
                                "phase" to "start",
                                "modelId" to preset.modelId,
                                "backend" to backend.name,
                            ),
                        )
                        _uiState.update {
                            it.copy(
                                isStartingLocalModel = false,
                                statusMessage = localStartFailureMessage(preset.modelId, error),
                            )
                        }
                    }
                }
        }
    }

    fun stopLocalModel() {
        viewModelScope.launch {
            runCatching { localModelService.stopModel() }
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Stopped local model") }
                }
                .onFailure { error ->
                    logger.log(
                        AppLogSeverity.ERROR,
                        TAG,
                        "Local model stop failed",
                        details = error.toStructuredLogDetails(
                            "phase" to "stop",
                            "modelId" to _uiState.value.localModel,
                            "backend" to _uiState.value.localExecutionBackend.name,
                        ),
                    )
                    _uiState.update {
                        it.copy(statusMessage = error.message ?: "Stop failed")
                    }
                }
        }
    }

    fun save() {
        val state = _uiState.value
        val next = AppSettings(
            providerType = state.providerType,
            plantSpecies = state.plantSpecies,
            companionRole = state.companionRole,
            guardianAngelPersonality = state.guardianAngelPersonality,
            avatarPresentationMode = state.avatarPresentationMode,
            avatarExpressionEnabled = false,
            chatStatusBarVisible = state.chatStatusBarVisible,
            mrAvatarMotionEnabled = state.mrAvatarMotionEnabled,
            mrModelControlsVisible = state.mrModelControlsVisible,
            mrModelOffsetX = state.mrModelOffsetX,
            mrModelOffsetY = state.mrModelOffsetY,
            mrModelScale = state.mrModelScale,
            mrModelCameraDistance = state.mrModelCameraDistance,
            mrModelYawDegrees = state.mrModelYawDegrees,
            mrModelTiltDegrees = state.mrModelTiltDegrees,
            aiImageQuality = state.aiImageQuality,
            localExecutionBackend = state.localExecutionBackend,
            localBaseUrl = state.persistedSettings.localBaseUrl,
            localModel = state.localModel,
            cloudBaseUrl = state.cloudBaseUrl.ifBlank { AppSettings.DEFAULT_CLOUD_BASE_URL },
            cloudModel = state.cloudModel.ifBlank { AppSettings.DEFAULT_CLOUD_MODEL },
            cloudApiKey = state.cloudApiKey,
            streamResponses = state.streamResponses,
            speakAssistantReplies = state.speakAssistantReplies,
            ttsVoiceProfile = state.ttsVoiceProfile,
            ttsSpeechRateMultiplier = state.ttsSpeechRateMultiplier,
            voiceVoxEnabled = state.voiceVoxEnabled,
            voiceVoxSpeaker = state.voiceVoxSpeaker,
            autoSmallTalkInterval = state.autoSmallTalkInterval,
            maxOutputTokens = state.maxOutputTokens,
            topK = state.topK,
            topP = state.topP,
            temperature = state.temperature,
            thinkingEnabled = state.thinkingEnabled,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Saving settings",
                details = buildString {
                    appendLine("provider=${next.providerType}")
                    appendLine("plant=${next.plantSpecies.name}")
                    appendLine("companionRole=${next.companionRole.name}")
                    appendLine("personality=${next.guardianAngelPersonality.name}")
                    appendLine("avatarExpressionEnabled=${next.avatarExpressionEnabled}")
                    appendLine("chatStatusBarVisible=${next.chatStatusBarVisible}")
                    appendLine("mrAvatarMotionEnabled=${next.mrAvatarMotionEnabled}")
                    appendLine("mrModelControlsVisible=${next.mrModelControlsVisible}")
                    appendLine("mrModelOffsetX=${next.mrModelOffsetX}")
                    appendLine("mrModelOffsetY=${next.mrModelOffsetY}")
                    appendLine("mrModelScale=${next.mrModelScale}")
                    appendLine("mrModelCameraDistance=${next.mrModelCameraDistance}")
                    appendLine("mrModelYawDegrees=${next.mrModelYawDegrees}")
                    appendLine("mrModelTiltDegrees=${next.mrModelTiltDegrees}")
                    appendLine("aiImageQuality=${next.aiImageQuality.name}")
                    appendLine("localExecutionBackend=${next.localExecutionBackend.name}")
                    appendLine("localModel=${next.localModel}")
                    appendLine("cloudBaseUrl=${next.cloudBaseUrl}")
                    appendLine("cloudModel=${next.cloudModel}")
                    appendLine("streamResponses=${next.streamResponses}")
                    appendLine("speakAssistantReplies=${next.speakAssistantReplies}")
                    appendLine("ttsVoiceProfile=${next.ttsVoiceProfile.name}")
                    appendLine("ttsSpeechRateMultiplier=${next.ttsSpeechRateMultiplier}")
                    appendLine("autoSmallTalkInterval=${next.autoSmallTalkInterval.name}")
                },
            )
            runCatching { repository.save(next) }
                .onSuccess {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Settings saved",
                        details = next.toSafeLogString(),
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            persistedSettings = next,
                            statusMessage = "Saved",
                        )
                    }
                }
                .onFailure { error ->
                    logger.log(
                        AppLogSeverity.ERROR,
                        TAG,
                        "Settings save failed",
                        details = error.toStructuredLogDetails(
                            "phase" to "save",
                            "provider" to state.providerType.name,
                            "modelId" to state.localModel,
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = error.message ?: "Save failed",
                        )
                    }
                }
        }
    }

    val selectedLocalModelPreset: LocalModelPreset
        get() = LocalModelPreset.fromModelId(_uiState.value.localModel)

    val selectedLocalModelDownloadState: LocalModelDownloadState
        get() = _uiState.value.localModelServiceState.downloads[selectedLocalModelPreset.modelId]
            ?: LocalModelDownloadState()

    private fun localStartFailureMessage(modelId: String, error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            modelId.equals("gemma-4-e2b", ignoreCase = true) &&
                message.contains("SentencePiece tokenizer is not found", ignoreCase = true) -> {
                "gemma-4-E2B cannot start on this runtime. Use gemma-4-E2B-it."
            }
            else -> error.message ?: "Start failed"
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

private fun AppSettings.toSafeLogString(): String {
    return buildString {
        append("AppSettings(")
        append("providerType=$providerType")
        append(", plantSpecies=$plantSpecies")
        append(", companionRole=$companionRole")
        append(", guardianAngelPersonality=$guardianAngelPersonality")
        append(", avatarPresentationMode=$avatarPresentationMode")
        append(", avatarExpressionEnabled=$avatarExpressionEnabled")
        append(", chatStatusBarVisible=$chatStatusBarVisible")
        append(", mrAvatarMotionEnabled=$mrAvatarMotionEnabled")
        append(", mrModelControlsVisible=$mrModelControlsVisible")
        append(", mrModelOffsetX=$mrModelOffsetX")
        append(", mrModelOffsetY=$mrModelOffsetY")
        append(", mrModelScale=$mrModelScale")
        append(", mrModelCameraDistance=$mrModelCameraDistance")
        append(", mrModelYawDegrees=$mrModelYawDegrees")
        append(", mrModelTiltDegrees=$mrModelTiltDegrees")
        append(", aiImageQuality=$aiImageQuality")
        append(", localBaseUrl=$localBaseUrl")
        append(", localModel=$localModel")
        append(", localExecutionBackend=$localExecutionBackend")
        append(", cloudBaseUrl=$cloudBaseUrl")
        append(", cloudModel=$cloudModel")
        append(", cloudApiKeyPresent=${cloudApiKey.isNotBlank()}")
        append(", cloudApiKeyChars=${cloudApiKey.length}")
        append(", streamResponses=$streamResponses")
        append(", speakAssistantReplies=$speakAssistantReplies")
        append(", ttsVoiceProfile=$ttsVoiceProfile")
        append(", ttsSpeechRateMultiplier=$ttsSpeechRateMultiplier")
        append(", autoSmallTalkInterval=$autoSmallTalkInterval")
        append(", maxOutputTokens=$maxOutputTokens")
        append(", topK=$topK")
        append(", topP=$topP")
        append(", temperature=$temperature")
        append(", thinkingEnabled=$thinkingEnabled")
        append(")")
    }
}

private fun recommendedTtsVoiceProfile(
    companionRole: CompanionRole,
    personality: GuardianAngelPersonality,
): TtsVoiceProfile {
    return when (companionRole) {
        CompanionRole.BUTLER -> when (personality) {
            GuardianAngelPersonality.GENTLE -> TtsVoiceProfile.CALM_MALE
            GuardianAngelPersonality.WISE -> TtsVoiceProfile.BUTLER_MALE
            GuardianAngelPersonality.CHEERFUL -> TtsVoiceProfile.COOL_MALE
            GuardianAngelPersonality.PROTECTIVE -> TtsVoiceProfile.BUTLER_MALE
        }

        CompanionRole.ANGEL -> when (personality) {
            GuardianAngelPersonality.GENTLE -> TtsVoiceProfile.SOFT_FEMALE
            GuardianAngelPersonality.WISE -> TtsVoiceProfile.SOFT_FEMALE
            GuardianAngelPersonality.CHEERFUL -> TtsVoiceProfile.BRIGHT_FEMALE
            GuardianAngelPersonality.PROTECTIVE -> TtsVoiceProfile.CUTE_FEMALE
        }
    }
}

private fun Double.roundToSpeechRateStep(): Double {
    return ((coerceIn(1.0, 2.0) * 5.0).roundToInt() / 5.0).coerceIn(1.0, 2.0)
}
