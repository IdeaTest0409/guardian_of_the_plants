package com.example.smartphonapptest001

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.smartphonapptest001.data.DefaultAppContainer
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.isFilamentMorphTargetCrash
import com.example.smartphonapptest001.data.logging.toDisplayText
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.AvatarPresentationMode
import com.example.smartphonapptest001.ui.SmartphoneChatApp
import com.example.smartphonapptest001.ui.theme.SmartphoneAppTheme
import com.example.smartphonapptest001.viewmodel.ChatViewModel
import com.example.smartphonapptest001.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = DefaultAppContainer(applicationContext)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            container.crashReporter.recordUncaughtCrash(thread.name, throwable)
            container.appLogger.log(
                AppLogSeverity.ERROR,
                "UncaughtException",
                "Thread ${thread.name} crashed",
                details = throwable.toStructuredLogDetails(
                    "phase" to "uncaught_exception",
                    "threadName" to thread.name,
                    "processName" to applicationContext.packageName,
                ),
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
        container.appLogger.log(
            AppLogSeverity.INFO,
            "MainActivity",
            "App launched",
            details = "savedInstanceState=${savedInstanceState != null}",
        )
        lifecycleScope.launch {
            container.appStartReporter.report(applicationContext)
        }
        fun disableAvatarExpressionAfterCrash(source: String) {
            lifecycleScope.launch {
                runCatching {
                    container.settingsRepository.setAvatarExpressionEnabled(false)
                    container.settingsRepository.setMrAvatarMotionEnabled(false)
                }
                    .onSuccess {
                        container.appLogger.log(
                            AppLogSeverity.WARN,
                            "CrashRecovery",
                            "3D morph settings disabled after Filament morph crash",
                            details = "source=$source",
                        )
                    }
                    .onFailure { error ->
                        container.appLogger.log(
                            AppLogSeverity.ERROR,
                            "CrashRecovery",
                            "Failed to disable 3D expression setting after crash",
                            details = error.toStructuredLogDetails("source" to source),
                        )
                    }
            }
        }

        container.crashReporter.consumeLastCrashReport()?.let { report ->
            container.appLogger.log(
                AppLogSeverity.ERROR,
                "CrashRecovery",
                "Previous crash report recovered",
                details = report.toDisplayText(),
            )
            if (report.isFilamentMorphTargetCrash()) {
                disableAvatarExpressionAfterCrash("last_crash_report")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val historicalReport = container.crashReporter.recordHistoricalExitIfAny { text ->
                container.appLogger.log(
                    AppLogSeverity.WARN,
                    "CrashRecovery",
                    "Historical process exit detected",
                    details = text,
                )
            }
            if (historicalReport?.isFilamentMorphTargetCrash() == true) {
                disableAvatarExpressionAfterCrash("historical_exit")
            }
        }
        setContent {
            SmartphoneAppTheme {
                val context = LocalContext.current
                val cameraPermissionGrantedState = remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                    )
                }
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    cameraPermissionGrantedState.value = granted
                }
                val hasRequestedCameraPermission = rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(cameraPermissionGrantedState.value) {
                    if (!cameraPermissionGrantedState.value && !hasRequestedCameraPermission.value) {
                        hasRequestedCameraPermission.value = true
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
                val factory = remember(container) { AppViewModelFactory(container) }
                val chatViewModel: ChatViewModel = viewModel(factory = factory)
                val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                val logEntries by container.appLogger.entries.collectAsStateWithLifecycle()

                val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(settingsState) {
                    chatViewModel.onSettingsChanged(
                        AppSettings(
                            providerType = settingsState.providerType,
                            plantSpecies = settingsState.plantSpecies,
                            companionRole = settingsState.companionRole,
                            guardianAngelPersonality = settingsState.guardianAngelPersonality,
                            avatarPresentationMode = settingsState.avatarPresentationMode,
                            avatarExpressionEnabled = settingsState.avatarExpressionEnabled,
                            chatStatusBarVisible = settingsState.chatStatusBarVisible,
                            mrAvatarMotionEnabled = settingsState.mrAvatarMotionEnabled,
                            mrModelControlsVisible = settingsState.mrModelControlsVisible,
                            mrModelOffsetX = settingsState.mrModelOffsetX,
                            mrModelOffsetY = settingsState.mrModelOffsetY,
                            mrModelScale = settingsState.mrModelScale,
                            mrModelCameraDistance = settingsState.mrModelCameraDistance,
                            mrModelYawDegrees = settingsState.mrModelYawDegrees,
                            mrModelTiltDegrees = settingsState.mrModelTiltDegrees,
                            aiImageQuality = settingsState.aiImageQuality,
                            localExecutionBackend = settingsState.localExecutionBackend,
                            localModel = settingsState.localModel,
                            cloudBaseUrl = settingsState.cloudBaseUrl,
                            cloudModel = settingsState.cloudModel,
                            cloudApiKey = settingsState.cloudApiKey,
                            ollamaCloudBaseUrl = settingsState.ollamaCloudBaseUrl,
                            ollamaCloudModel = settingsState.ollamaCloudModel,
                            ollamaCloudApiKey = settingsState.ollamaCloudApiKey,
                            streamResponses = settingsState.streamResponses,
                            speakAssistantReplies = settingsState.speakAssistantReplies,
                            ttsVoiceProfile = settingsState.ttsVoiceProfile,
                            ttsSpeechRateMultiplier = settingsState.ttsSpeechRateMultiplier,
                            autoSmallTalkInterval = settingsState.autoSmallTalkInterval,
                            maxOutputTokens = settingsState.maxOutputTokens,
                            topK = settingsState.topK,
                            topP = settingsState.topP,
                            temperature = settingsState.temperature,
                            thinkingEnabled = settingsState.thinkingEnabled,
                        ),
                    )
                    chatViewModel.onPlantImageChanged(
                        attachment = settingsState.selectedPlantImage,
                        mode = settingsState.plantImageSelectionMode,
                    )
                }

                SmartphoneChatApp(
                    chatState = chatState,
                    settingsState = settingsState,
                    logEntries = logEntries,
                    appLogger = container.appLogger,
                    cameraPermissionGranted = cameraPermissionGrantedState.value,
                    arCoreSupported = false,
                    onMessageChange = chatViewModel::onMessageChange,
                    onAttachmentCaptured = chatViewModel::onAttachmentCaptured,
                    onClearAttachment = chatViewModel::clearPendingAttachment,
                    onSendMessage = chatViewModel::sendMessage,
                    onResetChat = chatViewModel::resetConversation,
                    onProviderTypeChange = settingsViewModel::onProviderTypeChange,
                    onPlantSpeciesChange = settingsViewModel::onPlantSpeciesChange,
                    onCompanionRoleChange = settingsViewModel::onCompanionRoleChange,
                    onGuardianAngelPersonalityChange = settingsViewModel::onGuardianAngelPersonalityChange,
                    onAvatarPresentationModeChange = settingsViewModel::onAvatarPresentationModeChange,
                    onAvatarExpressionEnabledChange = settingsViewModel::onAvatarExpressionEnabledChange,
                    onChatStatusBarVisibleChange = settingsViewModel::onChatStatusBarVisibleChange,
                    onMrAvatarMotionEnabledChange = settingsViewModel::onMrAvatarMotionEnabledChange,
                    onMrModelControlsVisibleChange = settingsViewModel::onMrModelControlsVisibleChange,
                    onMrModelOffsetXChange = settingsViewModel::onMrModelOffsetXChange,
                    onMrModelOffsetYChange = settingsViewModel::onMrModelOffsetYChange,
                    onMrModelScaleChange = settingsViewModel::onMrModelScaleChange,
                    onMrModelCameraDistanceChange = settingsViewModel::onMrModelCameraDistanceChange,
                    onMrModelYawDegreesChange = settingsViewModel::onMrModelYawDegreesChange,
                    onMrModelTiltDegreesChange = settingsViewModel::onMrModelTiltDegreesChange,
                    onMrModelPlacementReset = settingsViewModel::resetMrModelPlacement,
                    onAiImageQualityChange = settingsViewModel::onAiImageQualityChange,
                    onPlantImageSelectionModeChange = settingsViewModel::onPlantImageSelectionModeChange,
                    onPlantImageCaptured = settingsViewModel::onPlantImageCaptured,
                    onRealtimePlantImageCaptured = settingsViewModel::onRealtimePlantImageCaptured,
                    onPlantImageClear = settingsViewModel::clearPlantImage,
                    onLocalExecutionBackendChange = settingsViewModel::onLocalExecutionBackendChange,
                    onLocalModelChange = settingsViewModel::onLocalModelChange,
                    onImportLocalModel = settingsViewModel::importLocalModel,
                    onCloudBaseUrlChange = settingsViewModel::onCloudBaseUrlChange,
                    onCloudModelChange = settingsViewModel::onCloudModelChange,
                    onCloudApiKeyChange = settingsViewModel::onCloudApiKeyChange,
                    onOllamaCloudBaseUrlChange = settingsViewModel::onOllamaCloudBaseUrlChange,
                    onOllamaCloudModelChange = settingsViewModel::onOllamaCloudModelChange,
                    onOllamaCloudApiKeyChange = settingsViewModel::onOllamaCloudApiKeyChange,
                    onStreamResponsesChange = settingsViewModel::onStreamResponsesChange,
                    onSpeakAssistantRepliesChange = settingsViewModel::onSpeakAssistantRepliesChange,
                    onTtsVoiceProfileChange = settingsViewModel::onTtsVoiceProfileChange,
                    onTtsSpeechRateMultiplierChange = settingsViewModel::onTtsSpeechRateMultiplierChange,
                    onAutoSmallTalkIntervalChange = settingsViewModel::onAutoSmallTalkIntervalChange,
                    onMaxOutputTokensChange = settingsViewModel::onMaxOutputTokensChange,
                    onTopKChange = settingsViewModel::onTopKChange,
                    onTopPChange = settingsViewModel::onTopPChange,
                    onTemperatureChange = settingsViewModel::onTemperatureChange,
                    onThinkingEnabledChange = settingsViewModel::onThinkingEnabledChange,
                    onDownloadLocalModel = settingsViewModel::downloadLocalModel,
                    onStartLocalModel = settingsViewModel::startLocalModel,
                    onStopLocalModel = settingsViewModel::stopLocalModel,
                    onSaveSettings = settingsViewModel::save,
                    onClearLogs = container.appLogger::clear,
                )
            }
        }
    }
}

class AppViewModelFactory(
    private val container: DefaultAppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(
                repository = container.chatRepository,
                settingsRepository = container.settingsRepository,
                plantProfileRepository = container.plantProfileRepository,
                plantKnowledgeRepository = container.plantKnowledgeRepository,
                logger = container.appLogger,
            ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                repository = container.settingsRepository,
                localModelService = container.localModelService,
                logger = container.appLogger,
            ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
