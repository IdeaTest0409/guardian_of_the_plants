package com.example.smartphonapptest001.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import android.net.Uri
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.smartphonapptest001.data.logging.AppLogEntry
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.local.LocalModelDownloadState
import com.example.smartphonapptest001.data.model.AiImageQuality
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AutoSmallTalkInterval
import com.example.smartphonapptest001.data.model.AvatarPresentationMode
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.data.model.GuardianAngelPersonality
import com.example.smartphonapptest001.data.model.LocalExecutionBackend
import com.example.smartphonapptest001.data.model.PlantImageSelectionMode
import com.example.smartphonapptest001.data.model.PlantSpecies
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.data.model.TtsVoiceProfile
import com.example.smartphonapptest001.data.model.VoiceVoxSpeaker
import com.example.smartphonapptest001.data.network.AudioResult
import com.example.smartphonapptest001.data.network.ServerTtsApi
import com.example.smartphonapptest001.ui.screen.ChatScreen
import com.example.smartphonapptest001.ui.screen.LogsScreen
import com.example.smartphonapptest001.ui.screen.SettingsScreen
import com.example.smartphonapptest001.viewmodel.ChatUiState
import com.example.smartphonapptest001.viewmodel.SettingsUiState

private sealed class AppDestination(val route: String, val label: String) {
    data object Chat : AppDestination("chat", "Chat")
    data object Settings : AppDestination("settings", "Settings")
    data object Logs : AppDestination("logs", "Logs")
}

@Composable
fun SmartphoneChatApp(
    chatState: ChatUiState,
    settingsState: SettingsUiState,
    logEntries: List<AppLogEntry>,
    appLogger: AppLogger,
    cameraPermissionGranted: Boolean,
    arCoreSupported: Boolean,
    onMessageChange: (String) -> Unit,
    onAttachmentCaptured: (Attachment) -> Unit,
    onClearAttachment: () -> Unit,
    onSendMessage: () -> Unit,
    onResetChat: () -> Unit,
    onProviderTypeChange: (ProviderType) -> Unit,
    onPlantSpeciesChange: (PlantSpecies) -> Unit,
    onCompanionRoleChange: (CompanionRole) -> Unit,
    onGuardianAngelPersonalityChange: (GuardianAngelPersonality) -> Unit,
    onAvatarPresentationModeChange: (AvatarPresentationMode) -> Unit,
    onAvatarExpressionEnabledChange: (Boolean) -> Unit,
    onChatStatusBarVisibleChange: (Boolean) -> Unit,
    onMrAvatarMotionEnabledChange: (Boolean) -> Unit,
    onMrModelControlsVisibleChange: (Boolean) -> Unit,
    onMrModelOffsetXChange: (Float) -> Unit,
    onMrModelOffsetYChange: (Float) -> Unit,
    onMrModelScaleChange: (Float) -> Unit,
    onMrModelCameraDistanceChange: (Float) -> Unit,
    onMrModelYawDegreesChange: (Float) -> Unit,
    onMrModelTiltDegreesChange: (Float) -> Unit,
    onMrModelPlacementReset: () -> Unit,
    onAiImageQualityChange: (AiImageQuality) -> Unit,
    onPlantImageSelectionModeChange: (PlantImageSelectionMode) -> Unit,
    onPlantImageCaptured: (Attachment) -> Unit,
    onRealtimePlantImageCaptured: (Attachment) -> Unit,
    onUsePreviousApprovedPlantImageChange: (Boolean) -> Unit,
    onPlantImageClear: () -> Unit,
    onLocalExecutionBackendChange: (LocalExecutionBackend) -> Unit,
    onLocalModelChange: (String) -> Unit,
    onImportLocalModel: (Uri) -> Unit,
    onCloudBaseUrlChange: (String) -> Unit,
    onCloudModelChange: (String) -> Unit,
    onCloudApiKeyChange: (String) -> Unit,
    onStreamResponsesChange: (Boolean) -> Unit,
    onSpeakAssistantRepliesChange: (Boolean) -> Unit,
    onTtsVoiceProfileChange: (TtsVoiceProfile) -> Unit,
    onTtsSpeechRateMultiplierChange: (Double) -> Unit,
    onVoiceVoxEnabledChange: (Boolean) -> Unit,
    onVoiceVoxSpeakerChange: (VoiceVoxSpeaker) -> Unit,
    onAutoSmallTalkIntervalChange: (AutoSmallTalkInterval) -> Unit,
    onMaxOutputTokensChange: (Int) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Double) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onDownloadLocalModel: () -> Unit,
    onStartLocalModel: () -> Unit,
    onStopLocalModel: () -> Unit,
    onSaveSettings: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val navController = rememberNavController()
    val destinations = listOf(AppDestination.Chat, AppDestination.Settings, AppDestination.Logs)
    GuardianReplySpeechEffect(
        chatState = chatState,
        enabled = settingsState.speakAssistantReplies,
        voiceProfile = settingsState.ttsVoiceProfile,
        speechRateMultiplier = settingsState.ttsSpeechRateMultiplier,
        voiceVoxEnabled = settingsState.voiceVoxEnabled && settingsState.providerType == ProviderType.SERVER,
        voiceVoxSpeaker = settingsState.voiceVoxSpeaker,
        appLogger = appLogger,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = { Text(destination.label) },
                        icon = {
                            when (destination) {
                                AppDestination.Chat -> androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = destination.label,
                                )

                                AppDestination.Settings -> androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = destination.label,
                                )

                                AppDestination.Logs -> androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = destination.label,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Chat.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Chat.route) {
                    ChatScreen(
                        state = chatState,
                        appLogger = appLogger,
                        cameraPermissionGranted = cameraPermissionGranted,
                        arCoreSupported = arCoreSupported,
                        providerType = settingsState.providerType,
                        chatStatusBarVisible = settingsState.chatStatusBarVisible,
                        localExecutionBackend = settingsState.localExecutionBackend,
                        avatarPresentationMode = settingsState.avatarPresentationMode,
                        avatarExpressionEnabled = settingsState.avatarExpressionEnabled,
                        mrAvatarMotionEnabled = settingsState.mrAvatarMotionEnabled,
                        mrModelControlsVisible = settingsState.mrModelControlsVisible,
                        mrModelOffsetX = settingsState.mrModelOffsetX,
                        mrModelOffsetY = settingsState.mrModelOffsetY,
                        mrModelScale = settingsState.mrModelScale,
                        mrModelCameraDistance = settingsState.mrModelCameraDistance,
                        mrModelYawDegrees = settingsState.mrModelYawDegrees,
                        mrModelTiltDegrees = settingsState.mrModelTiltDegrees,
                        localRuntimeState = settingsState.localModelServiceState.runtime,
                        localDownloadState = settingsState.localModelServiceState.downloads[settingsState.localModel]
                            ?: LocalModelDownloadState(),
                        selectedPlantImage = settingsState.selectedPlantImage,
                        onUsePreviousApprovedPlantImageChange = onUsePreviousApprovedPlantImageChange,
                        onRealtimePlantImageCaptured = onRealtimePlantImageCaptured,
                        onMessageChange = onMessageChange,
                        onSendMessage = onSendMessage,
                        onResetChat = onResetChat,
                        onMrModelOffsetXChange = onMrModelOffsetXChange,
                        onMrModelOffsetYChange = onMrModelOffsetYChange,
                        onMrModelScaleChange = onMrModelScaleChange,
                        onMrModelCameraDistanceChange = onMrModelCameraDistanceChange,
                        onMrModelYawDegreesChange = onMrModelYawDegreesChange,
                        onMrModelTiltDegreesChange = onMrModelTiltDegreesChange,
                        onMrModelPlacementReset = onMrModelPlacementReset,
                        onMrModelPlacementSetDefault = onSaveSettings,
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    state = settingsState,
                    arCoreSupported = arCoreSupported,
                    onProviderTypeChange = onProviderTypeChange,
                    onPlantSpeciesChange = onPlantSpeciesChange,
                    onCompanionRoleChange = onCompanionRoleChange,
                    onGuardianAngelPersonalityChange = onGuardianAngelPersonalityChange,
                    onAvatarPresentationModeChange = onAvatarPresentationModeChange,
                    onAvatarExpressionEnabledChange = onAvatarExpressionEnabledChange,
                    onChatStatusBarVisibleChange = onChatStatusBarVisibleChange,
                    onMrAvatarMotionEnabledChange = onMrAvatarMotionEnabledChange,
                    onMrModelControlsVisibleChange = onMrModelControlsVisibleChange,
                    onAiImageQualityChange = onAiImageQualityChange,
                    onPlantImageSelectionModeChange = onPlantImageSelectionModeChange,
                    onPlantImageCaptured = onPlantImageCaptured,
                    onRealtimePlantImageCaptured = onRealtimePlantImageCaptured,
                    onPlantImageClear = onPlantImageClear,
                    onLocalExecutionBackendChange = onLocalExecutionBackendChange,
                    onLocalModelChange = onLocalModelChange,
                    onImportLocalModel = onImportLocalModel,
                    onCloudBaseUrlChange = onCloudBaseUrlChange,
                    onCloudModelChange = onCloudModelChange,
                    onCloudApiKeyChange = onCloudApiKeyChange,
                    onStreamResponsesChange = onStreamResponsesChange,
                    onSpeakAssistantRepliesChange = onSpeakAssistantRepliesChange,
                    onTtsVoiceProfileChange = onTtsVoiceProfileChange,
                    onTtsSpeechRateMultiplierChange = onTtsSpeechRateMultiplierChange,
                    onVoiceVoxEnabledChange = onVoiceVoxEnabledChange,
                    onVoiceVoxSpeakerChange = onVoiceVoxSpeakerChange,
                    onAutoSmallTalkIntervalChange = onAutoSmallTalkIntervalChange,
                    onMaxOutputTokensChange = onMaxOutputTokensChange,
                    onTopKChange = onTopKChange,
                    onTopPChange = onTopPChange,
                    onTemperatureChange = onTemperatureChange,
                    onThinkingEnabledChange = onThinkingEnabledChange,
                    onDownloadLocalModel = onDownloadLocalModel,
                    onStartLocalModel = onStartLocalModel,
                    onStopLocalModel = onStopLocalModel,
                    onSaveSettings = onSaveSettings,
                )
            }
            composable(AppDestination.Logs.route) {
                LogsScreen(
                    entries = logEntries,
                    onClearLogs = onClearLogs,
                )
            }
        }
    }
}

@Composable
private fun GuardianReplySpeechEffect(
    chatState: ChatUiState,
    enabled: Boolean,
    voiceProfile: TtsVoiceProfile,
    speechRateMultiplier: Double,
    voiceVoxEnabled: Boolean,
    voiceVoxSpeaker: VoiceVoxSpeaker,
    appLogger: AppLogger,
) {
    val context = LocalContext.current
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    val readyHolder = remember { mutableStateOf(false) }
    val lastSpokenMessageId = rememberSaveable { mutableStateOf<String?>(null) }
    val latestAssistantMessage = remember(chatState.messages) {
        chatState.messages.lastOrNull { it.role == com.example.smartphonapptest001.data.model.ChatRole.ASSISTANT }
    }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            val ready = status == TextToSpeech.SUCCESS
            readyHolder.value = ready
            if (!ready) {
                appLogger.log(
                    AppLogSeverity.WARN,
                    "VoiceOutput",
                    "TextToSpeech initialization failed",
                    details = "status=$status",
                )
            }
        }
        ttsHolder.value = tts
        onDispose {
            runCatching {
                tts.stop()
                tts.shutdown()
            }
            ttsHolder.value = null
            readyHolder.value = false
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            ttsHolder.value?.stop()
        }
    }

    LaunchedEffect(readyHolder.value) {
        val tts = ttsHolder.value ?: return@LaunchedEffect
        if (!readyHolder.value) return@LaunchedEffect
        val languageResult = tts.setLanguage(Locale.JAPANESE)
        appLogger.log(
            AppLogSeverity.INFO,
            "VoiceOutput",
            "TextToSpeech initialized",
            details = buildString {
                appendLine("ready=${readyHolder.value}")
                appendLine("languageResult=$languageResult")
            },
        )
    }

    LaunchedEffect(enabled, readyHolder.value, latestAssistantMessage?.id, latestAssistantMessage?.isPending, latestAssistantMessage?.content, voiceProfile, speechRateMultiplier, voiceVoxEnabled, voiceVoxSpeaker) {
        val message = latestAssistantMessage ?: return@LaunchedEffect
        if (!enabled || message.isPending || message.content.isBlank()) return@LaunchedEffect
        if (lastSpokenMessageId.value == message.id) return@LaunchedEffect

        if (voiceVoxEnabled) {
            val baseUrl = com.example.smartphonapptest001.BuildConfig.GUARDIAN_API_BASE_URL.trim().trimEnd('/')
            if (baseUrl.isBlank()) {
                appLogger.log(AppLogSeverity.ERROR, "VoiceVox", "TTS base URL is not configured")
                return@LaunchedEffect
            }
            val serverTtsApi = ServerTtsApi(baseUrl, appLogger)
            val speakText = message.content
            val utteranceId = "assistant-${message.id}"
            appLogger.log(
                AppLogSeverity.INFO,
                "VoiceVox",
                "Synthesizing with VoiceVox",
                details = buildString {
                    appendLine("messageId=${message.id}")
                    appendLine("chars=${speakText.length}")
                    appendLine("speaker=${voiceVoxSpeaker.label}")
                },
            )
            runCatching {
                suspend fun playAudio(audio: AudioResult): Boolean {
                    if (audio.bytes.isEmpty()) return false
                    val tempFile = java.io.File.createTempFile("voicevox_tts_", ".${audio.extension}", context.cacheDir)
                    var mp: android.media.MediaPlayer? = null
                    var immediatePlaybackError = false
                    try {
                        tempFile.writeBytes(audio.bytes)
                        mp = android.media.MediaPlayer()
                        mp.setDataSource(tempFile.absolutePath)
                        mp.setOnCompletionListener {
                            runCatching { it.release() }
                            runCatching { tempFile.delete() }
                        }
                        mp.setOnErrorListener { player, what, extra ->
                            immediatePlaybackError = true
                            runCatching { player.release() }
                            runCatching { tempFile.delete() }
                            appLogger.log(
                                AppLogSeverity.ERROR,
                                "VoiceVox",
                                "VoiceVox playback error",
                                details = "what=$what extra=$extra format=${audio.format} file=${tempFile.name}",
                            )
                            true
                        }
                        mp.prepare()
                        mp.start()
                        kotlinx.coroutines.delay(250L)
                        if (immediatePlaybackError) {
                            throw IllegalStateException("MediaPlayer playback error for ${audio.format}")
                        }
                        appLogger.log(
                            AppLogSeverity.INFO,
                            "VoiceVox",
                            "VoiceVox playback started",
                            details = "messageId=${message.id} format=${audio.format} bytes=${audio.bytes.size} file=${tempFile.name}",
                        )
                        return true
                    } catch (e: Exception) {
                        runCatching { mp?.release() }
                        runCatching { tempFile.delete() }
                        appLogger.log(
                            AppLogSeverity.WARN,
                            "VoiceVox",
                            "VoiceVox playback start failed",
                            details = buildString {
                                appendLine("messageId=${message.id}")
                                appendLine("format=${audio.format}")
                                appendLine("bytes=${audio.bytes.size}")
                                appendLine("type=${e::class.java.simpleName}")
                                appendLine("message=${e.message.orEmpty()}")
                            },
                        )
                        return false
                    }
                }

                val audio = serverTtsApi.synthesize(speakText, voiceVoxSpeaker.speakerId, preferredFormat = "aac")
                val played = if (audio != null) {
                    playAudio(audio)
                } else {
                    appLogger.log(
                        AppLogSeverity.WARN,
                        "VoiceVox",
                        "VoiceVox returned empty audio",
                        details = "messageId=${message.id}",
                    )
                    false
                }
                val fallbackPlayed = if (!played && audio?.format?.lowercase() != "wav") {
                    appLogger.log(
                        AppLogSeverity.INFO,
                        "VoiceVox",
                        "Retrying VoiceVox playback with WAV",
                        details = "messageId=${message.id}",
                    )
                    val wavAudio = serverTtsApi.synthesize(speakText, voiceVoxSpeaker.speakerId, preferredFormat = "wav")
                    wavAudio?.let { playAudio(it) } == true
                } else {
                    false
                }
                if (played || fallbackPlayed) {
                    lastSpokenMessageId.value = message.id
                }
            }.onFailure { error ->
                appLogger.log(
                    AppLogSeverity.ERROR,
                    "VoiceVox",
                    "VoiceVox synthesis failed",
                    details = error.message.orEmpty(),
                )
            }
        } else {
            val tts = ttsHolder.value ?: return@LaunchedEffect
            if (!readyHolder.value) return@LaunchedEffect

            val speakText = message.content
            val utteranceId = "assistant-${message.id}"
            appLogger.log(
                AppLogSeverity.INFO,
                "VoiceOutput",
                "Speaking assistant reply",
                details = buildString {
                    appendLine("messageId=${message.id}")
                    appendLine("chars=${speakText.length}")
                    appendLine("enabled=$enabled")
                    appendLine("voiceProfile=${voiceProfile.name}")
                    appendLine("pitch=${voiceProfile.pitch}")
                    appendLine("speechRate=${voiceProfile.speechRate}")
                    appendLine("speechRateMultiplier=$speechRateMultiplier")
                    appendLine("effectiveSpeechRate=${voiceProfile.speechRate * speechRateMultiplier.toFloat()}")
                },
            )
            runCatching {
                tts.stop()
                tts.setPitch(voiceProfile.pitch)
                tts.setSpeechRate((voiceProfile.speechRate * speechRateMultiplier.toFloat()).coerceIn(0.1f, 4.0f))
                val speakResult = tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult == TextToSpeech.ERROR) {
                    appLogger.log(
                        AppLogSeverity.WARN,
                        "VoiceOutput",
                        "TextToSpeech speak returned error",
                        details = "messageId=${message.id}",
                    )
                }
                lastSpokenMessageId.value = message.id
            }.onFailure { error ->
                appLogger.log(
                    AppLogSeverity.ERROR,
                    "VoiceOutput",
                    "Speaking assistant reply failed",
                    details = error.message.orEmpty(),
                )
            }
        }
    }
}
