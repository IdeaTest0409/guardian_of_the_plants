package com.example.smartphonapptest001.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.example.smartphonapptest001.data.local.LocalModelDownloadState
import com.example.smartphonapptest001.data.local.LocalModelDownloadStatus
import com.example.smartphonapptest001.data.model.AiImageQuality
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AttachmentKind
import com.example.smartphonapptest001.data.model.AvatarPresentationMode
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.data.model.GuardianAngelPersonality
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.AutoSmallTalkInterval
import com.example.smartphonapptest001.data.model.LocalExecutionBackend
import com.example.smartphonapptest001.data.model.LocalModelPreset
import com.example.smartphonapptest001.data.model.PlantImageSelectionMode
import com.example.smartphonapptest001.data.model.PlantSpecies
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.viewmodel.SettingsUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    arCoreSupported: Boolean,
    onProviderTypeChange: (ProviderType) -> Unit,
    onPlantSpeciesChange: (PlantSpecies) -> Unit,
    onCompanionRoleChange: (CompanionRole) -> Unit,
    onGuardianAngelPersonalityChange: (GuardianAngelPersonality) -> Unit,
    onAvatarPresentationModeChange: (AvatarPresentationMode) -> Unit,
    onAvatarExpressionEnabledChange: (Boolean) -> Unit,
    onChatStatusBarVisibleChange: (Boolean) -> Unit,
    onMrAvatarMotionEnabledChange: (Boolean) -> Unit,
    onMrModelControlsVisibleChange: (Boolean) -> Unit,
    onAiImageQualityChange: (AiImageQuality) -> Unit,
    onPlantImageSelectionModeChange: (PlantImageSelectionMode) -> Unit,
    onPlantImageCaptured: (Attachment) -> Unit,
    onRealtimePlantImageCaptured: (Attachment) -> Unit,
    onPlantImageClear: () -> Unit,
    onLocalExecutionBackendChange: (LocalExecutionBackend) -> Unit,
    onLocalModelChange: (String) -> Unit,
    onImportLocalModel: (Uri) -> Unit,
    onCloudBaseUrlChange: (String) -> Unit,
    onCloudModelChange: (String) -> Unit,
    onCloudApiKeyChange: (String) -> Unit,
    onOllamaCloudBaseUrlChange: (String) -> Unit,
    onOllamaCloudModelChange: (String) -> Unit,
    onOllamaCloudApiKeyChange: (String) -> Unit,
    onStreamResponsesChange: (Boolean) -> Unit,
    onSpeakAssistantRepliesChange: (Boolean) -> Unit,
    onTtsVoiceProfileChange: (com.example.smartphonapptest001.data.model.TtsVoiceProfile) -> Unit,
    onTtsSpeechRateMultiplierChange: (Double) -> Unit,
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
) {
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var plantMenuExpanded by remember { mutableStateOf(false) }
    var companionMenuExpanded by remember { mutableStateOf(false) }
    var personalityMenuExpanded by remember { mutableStateOf(false) }
    var avatarPresentationMenuExpanded by remember { mutableStateOf(false) }
    var aiImageQualityMenuExpanded by remember { mutableStateOf(false) }
    var plantImageMenuExpanded by remember { mutableStateOf(false) }
    var localModelMenuExpanded by remember { mutableStateOf(false) }
    var localBackendMenuExpanded by remember { mutableStateOf(false) }
    var ollamaCloudModelMenuExpanded by remember { mutableStateOf(false) }
    var autoSmallTalkMenuExpanded by remember { mutableStateOf(false) }
    var aiConfigDialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            onPlantImageSelectionModeChange(PlantImageSelectionMode.CAMERA_PHOTO)
            onPlantImageCaptured(bitmap.toSelectedPlantAttachment())
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                onPlantImageSelectionModeChange(PlantImageSelectionMode.FILE_UPLOAD)
                onPlantImageCaptured(uri.toSelectedPlantAttachment(context))
            }
        }
    }

    val modelFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching { onImportLocalModel(uri) }
        }
    }

    val selectedPreset = LocalModelPreset.fromModelId(state.localModel)
    val localDownloadState = state.localModelServiceState.downloads[selectedPreset.modelId]
        ?: LocalModelDownloadState()
    val runtimeState = state.localModelServiceState.runtime
    val isLocalRunning = runtimeState.isRunning && runtimeState.activeModelId == selectedPreset.modelId
    val isLocalDownloaded = localDownloadState.status == LocalModelDownloadStatus.DOWNLOADED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Plant Guardian Settings",
            style = MaterialTheme.typography.headlineMedium,
        )
    Text(
        text = "Choose the plant, the guardian angel personality, and the AI backend you want to use.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

        Text(
            text = when (state.providerType) {
                ProviderType.LOCAL -> "Current mode: Local / on-device"
                ProviderType.CLOUD -> "Current mode: Cloud / LM Studio"
                ProviderType.OLLAMA_CLOUD -> "Current mode: Cloud / Ollama"
                ProviderType.SERVER -> "Current mode: Server / VPS"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                state.providerType == ProviderType.LOCAL && runtimeState.isStarting ->
                    "Local runtime: Starting | ${state.localExecutionBackend.label} | ${selectedPreset.label}"
                state.providerType == ProviderType.LOCAL && isLocalRunning ->
                    "Local runtime: Running | ${state.localExecutionBackend.label} | ${selectedPreset.label}"
                state.providerType == ProviderType.LOCAL ->
                    "Local runtime: ${runtimeState.message ?: "Stopped"} | ${state.localExecutionBackend.label} | ${selectedPreset.label}"
                state.providerType == ProviderType.CLOUD ->
                    "Cloud runtime: ${state.cloudBaseUrl.ifBlank { "unset" }} | ${state.cloudModel.ifBlank { "unset model" }}"
                state.providerType == ProviderType.OLLAMA_CLOUD ->
                    "Ollama Cloud: ${state.ollamaCloudBaseUrl.ifBlank { "unset" }} | ${state.ollamaCloudModel.ifBlank { "unset model" }}"
                state.providerType == ProviderType.SERVER ->
                    "Server VPS endpoint configured via local.properties"
                else -> "Runtime: unknown"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Stage 1: Plant image")
        SelectionField(
            label = "Plant image source",
            value = state.plantImageSelectionMode.label,
            expanded = plantImageMenuExpanded,
            onExpandedChange = { plantImageMenuExpanded = it },
            onDismiss = { plantImageMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(PlantImageSelectionMode.CAMERA_PHOTO.label) },
                onClick = {
                    plantImageMenuExpanded = false
                    onPlantImageSelectionModeChange(PlantImageSelectionMode.CAMERA_PHOTO)
                    captureLauncher.launch(null)
                },
            )
            DropdownMenuItem(
                text = { Text(PlantImageSelectionMode.FILE_UPLOAD.label) },
                onClick = {
                    plantImageMenuExpanded = false
                    onPlantImageSelectionModeChange(PlantImageSelectionMode.FILE_UPLOAD)
                    filePickerLauncher.launch("image/*")
                },
            )
            DropdownMenuItem(
                text = { Text(PlantImageSelectionMode.REALTIME_CAPTURE.label) },
                onClick = {
                    plantImageMenuExpanded = false
                    onPlantImageSelectionModeChange(PlantImageSelectionMode.REALTIME_CAPTURE)
                },
            )
        }
        Text(
            text = when (state.plantImageSelectionMode) {
                PlantImageSelectionMode.REALTIME_CAPTURE -> "The Chat screen will show the live camera preview. Send uses the current frame."
                else -> "Choose one image source for the plant."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionField(
            label = "AI image size",
            value = state.aiImageQuality.label,
            expanded = aiImageQualityMenuExpanded,
            onExpandedChange = { aiImageQualityMenuExpanded = it },
            onDismiss = { aiImageQualityMenuExpanded = false },
        ) {
            AiImageQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    text = { Text(quality.label) },
                    onClick = {
                        onAiImageQualityChange(quality)
                        aiImageQualityMenuExpanded = false
                    },
                )
            }
        }
        Text(
            text = "Only the image sent to AI is resized. The camera preview stays large.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.selectedPlantImage != null) {
            AttachmentPreview(
                attachment = state.selectedPlantImage!!,
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Button(
                onClick = onPlantImageClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear plant image")
            }
        }

        SelectionField(
            label = "AI provider",
            value = when (state.providerType) {
                ProviderType.LOCAL -> "Local on-device"
                ProviderType.CLOUD -> "Cloud LM Studio"
                ProviderType.OLLAMA_CLOUD -> "Cloud Ollama"
                ProviderType.SERVER -> "Server VPS"
            },
            expanded = providerMenuExpanded,
            onExpandedChange = { providerMenuExpanded = it },
            onDismiss = { providerMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Cloud LM Studio") },
                onClick = {
                    onProviderTypeChange(ProviderType.CLOUD)
                    providerMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Local on-device") },
                onClick = {
                    onProviderTypeChange(ProviderType.LOCAL)
                    providerMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Cloud Ollama") },
                onClick = {
                    onProviderTypeChange(ProviderType.OLLAMA_CLOUD)
                    providerMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Server VPS") },
                onClick = {
                    onProviderTypeChange(ProviderType.SERVER)
                    providerMenuExpanded = false
                },
            )
        }

        SelectionField(
            label = "Plant species",
            value = state.plantSpecies.label,
            expanded = plantMenuExpanded,
            onExpandedChange = { plantMenuExpanded = it },
            onDismiss = { plantMenuExpanded = false },
        ) {
            PlantSpecies.available().forEach { species ->
                DropdownMenuItem(
                    text = { Text(species.label) },
                    onClick = {
                        onPlantSpeciesChange(species)
                        plantMenuExpanded = false
                    },
                )
            }
        }
        if (state.plantSpecies == PlantSpecies.NONE) {
            Text(
                text = "守護対象の植物はありません。ここには私の守護対象はいないと伝えます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SelectionField(
            label = "Companion role",
            value = state.companionRole.label,
            expanded = companionMenuExpanded,
            onExpandedChange = { companionMenuExpanded = it },
            onDismiss = { companionMenuExpanded = false },
        ) {
            CompanionRole.available().forEach { role ->
                DropdownMenuItem(
                    text = { Text(role.label) },
                    onClick = {
                        onCompanionRoleChange(role)
                        companionMenuExpanded = false
                    },
                )
            }
        }

        SelectionField(
            label = "Guardian personality",
            value = state.guardianAngelPersonality.label,
            expanded = personalityMenuExpanded,
            onExpandedChange = { personalityMenuExpanded = it },
            onDismiss = { personalityMenuExpanded = false },
        ) {
            GuardianAngelPersonality.available().forEach { personality ->
                DropdownMenuItem(
                    text = { Text(personality.label) },
                    onClick = {
                        onGuardianAngelPersonalityChange(personality)
                        personalityMenuExpanded = false
                    },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Speak guardian replies",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Read the guardian's response aloud with device TTS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.speakAssistantReplies,
                onCheckedChange = onSpeakAssistantRepliesChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Speech speed",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatOneDecimal(state.ttsSpeechRateMultiplier)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = state.ttsSpeechRateMultiplier.toFloat(),
            onValueChange = { value ->
                onTtsSpeechRateMultiplierChange(value.toDouble().roundToSpeechRateStep())
            },
            valueRange = 1.0f..2.0f,
            steps = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        SelectionField(
            label = "Auto small talk",
            value = state.autoSmallTalkInterval.label,
            expanded = autoSmallTalkMenuExpanded,
            onExpandedChange = { autoSmallTalkMenuExpanded = it },
            onDismiss = { autoSmallTalkMenuExpanded = false },
        ) {
            AutoSmallTalkInterval.entries.forEach { interval ->
                DropdownMenuItem(
                    text = { Text(interval.label) },
                    onClick = {
                        onAutoSmallTalkIntervalChange(interval)
                        autoSmallTalkMenuExpanded = false
                    },
                )
            }
        }
        Text(
            text = "If there is no chat interaction for the selected time, the guardian starts a short topic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SelectionField(
            label = "Avatar presentation",
            value = state.avatarPresentationMode.label,
            expanded = avatarPresentationMenuExpanded,
            onExpandedChange = { avatarPresentationMenuExpanded = it },
            onDismiss = { avatarPresentationMenuExpanded = false },
        ) {
            AvatarPresentationMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onAvatarPresentationModeChange(mode)
                        avatarPresentationMenuExpanded = false
                    },
                )
            }
        }
        Text(
            text = when (state.avatarPresentationMode) {
                AvatarPresentationMode.TWO_D -> "Use the current animated portrait cards."
                AvatarPresentationMode.THREE_D -> "Show the 3D model stage in the Chat screen."
                AvatarPresentationMode.AR -> "Show the realtime camera with a floating guardian overlay. No ARCore required."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chat status bar",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Show AI mode and model details at the top of Chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.chatStatusBarVisible,
                onCheckedChange = onChatStatusBarVisibleChange,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "3D expression driver",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Off by default. Turn on only when testing morph target expressions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.avatarExpressionEnabled,
                onCheckedChange = onAvatarExpressionEnabledChange,
                enabled = state.avatarPresentationMode == AvatarPresentationMode.THREE_D,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MR blink and mouth",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Off by default. Uses only blink and mouth morph targets in MR camera mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.mrAvatarMotionEnabled,
                onCheckedChange = onMrAvatarMotionEnabledChange,
                enabled = state.avatarPresentationMode == AvatarPresentationMode.AR,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MR camera controls",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Show compact placement controls over the MR camera in Chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.mrModelControlsVisible,
                onCheckedChange = onMrModelControlsVisibleChange,
                enabled = state.avatarPresentationMode == AvatarPresentationMode.AR,
            )
        }

        if (state.providerType == ProviderType.LOCAL) {
            SectionLabel("Local on-device AI")
            SelectionField(
                label = "Local model",
                value = selectedPreset.label,
                expanded = localModelMenuExpanded,
                onExpandedChange = { localModelMenuExpanded = it },
                onDismiss = { localModelMenuExpanded = false },
            ) {
                LocalModelPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label) },
                        onClick = {
                            onLocalModelChange(preset.modelId)
                            localModelMenuExpanded = false
                        },
                    )
                }
            }

            SelectionField(
                label = "Local execution",
                value = state.localExecutionBackend.label,
                expanded = localBackendMenuExpanded,
                onExpandedChange = { localBackendMenuExpanded = it },
                onDismiss = { localBackendMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(LocalExecutionBackend.DEFAULT.label) },
                    onClick = {
                        onLocalExecutionBackendChange(LocalExecutionBackend.DEFAULT)
                        localBackendMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(LocalExecutionBackend.CPU.label) },
                    onClick = {
                        onLocalExecutionBackendChange(LocalExecutionBackend.CPU)
                        localBackendMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(LocalExecutionBackend.GPU_NPU.label) },
                    onClick = {
                        onLocalExecutionBackendChange(LocalExecutionBackend.GPU_NPU)
                        localBackendMenuExpanded = false
                    },
                )
            }

            Text(
                text = when (localDownloadState.status) {
                    LocalModelDownloadStatus.NOT_DOWNLOADED -> "Download state: not downloaded"
                    LocalModelDownloadStatus.DOWNLOADING -> "Download state: downloading"
                    LocalModelDownloadStatus.DOWNLOADED -> "Download state: downloaded"
                    LocalModelDownloadStatus.FAILED -> "Download state: failed"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedPreset == LocalModelPreset.GEMMA_4_E2B) {
            Text(
                text = "This bundle currently fails on the on-device runtime. Use gemma-4-E2B-it for local execution.",
                color = MaterialTheme.colorScheme.error,
            )
            }
            if (selectedPreset == LocalModelPreset.GEMMA_4_E4B_IT) {
            Text(
                text = "This bundle is larger and may take more time and memory to start on some devices.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            if (localDownloadState.status == LocalModelDownloadStatus.DOWNLOADING && localDownloadState.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { localDownloadState.bytesDownloaded.toFloat() / localDownloadState.totalBytes.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!localDownloadState.message.isNullOrBlank()) {
            Text(
                text = localDownloadState.message.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            Text(
                text = when {
                    runtimeState.isStarting -> "Runtime: starting"
                    isLocalRunning -> "Runtime: running"
                    runtimeState.activeModelId.isNullOrBlank() -> "Runtime: stopped"
                    else -> "Runtime: ${runtimeState.message ?: "idle"}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!runtimeState.message.isNullOrBlank()) {
            Text(
                text = runtimeState.message.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            Button(
                onClick = onDownloadLocalModel,
                enabled = !state.isDownloadingLocalModel && !state.isImportingLocalModel && !state.isStartingLocalModel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isDownloadingLocalModel) "Downloading..." else "Download model")
            }
            Button(
                onClick = { aiConfigDialogOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("AI configurations")
            }
            Button(
                onClick = { modelFilePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isDownloadingLocalModel && !state.isImportingLocalModel && !state.isStartingLocalModel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isImportingLocalModel) "Importing..." else "Choose model file")
            }
            Button(
                onClick = onStartLocalModel,
                enabled = isLocalDownloaded && !state.isDownloadingLocalModel && !state.isImportingLocalModel && !state.isStartingLocalModel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isStartingLocalModel) "Starting..." else "Start model")
            }
            TextButton(
                onClick = onStopLocalModel,
                enabled = isLocalRunning || runtimeState.isStarting,
            ) {
                Text("Stop model")
            }
        }

        if (state.providerType == ProviderType.CLOUD) {
            SectionLabel("Cloud AI")
            OutlinedTextField(
                value = state.cloudBaseUrl,
                onValueChange = onCloudBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloud base URL") },
                supportingText = { Text("Current cloud endpoint") },
            )
            OutlinedTextField(
                value = state.cloudModel,
                onValueChange = onCloudModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloud model") },
                supportingText = { Text("Cloud model stays unchanged") },
            )
            OutlinedTextField(
                value = state.cloudApiKey,
                onValueChange = onCloudApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloud API key") },
            )
        }

        if (state.providerType == ProviderType.OLLAMA_CLOUD) {
            SectionLabel("Ollama Cloud")
            OutlinedTextField(
                value = state.ollamaCloudBaseUrl,
                onValueChange = onOllamaCloudBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ollama Cloud base URL") },
                supportingText = { Text("OpenAI-compatible endpoint. Default: https://ollama.com/v1") },
            )
            SelectionField(
                label = "Ollama Cloud model",
                value = state.ollamaCloudModel.ifBlank { AppSettings.DEFAULT_OLLAMA_CLOUD_MODEL },
                expanded = ollamaCloudModelMenuExpanded,
                onExpandedChange = { ollamaCloudModelMenuExpanded = it },
                onDismiss = { ollamaCloudModelMenuExpanded = false },
            ) {
                AppSettings.OLLAMA_CLOUD_MODELS.forEach { model ->
                    val enabled = model in AppSettings.ENABLED_OLLAMA_CLOUD_MODELS
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (enabled) model else "$model (unavailable)",
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        enabled = enabled,
                        onClick = {
                            onOllamaCloudModelChange(model)
                            ollamaCloudModelMenuExpanded = false
                        },
                    )
                }
            }
            Text(
                text = "Only gemma4:31b-cloud is selectable for now. Other models are shown for reference.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.ollamaCloudApiKey,
                onValueChange = onOllamaCloudApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ollama API key") },
                supportingText = { Text("Required for Ollama Cloud. Stored on this device.") },
            )
        }

        if (state.providerType == ProviderType.SERVER) {
            SectionLabel("Server VPS")
            Text(
                text = "AI requests are sent through your VPS server. The server handles AI provider selection and API keys. No configuration needed on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Configure guardian.api.baseUrl in android/local.properties to set the server endpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilterChip(
            selected = state.streamResponses,
            onClick = { onStreamResponsesChange(!state.streamResponses) },
            label = { Text("Enable streaming replies") },
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onSaveSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Saving..." else "Save settings")
        }

        if (!state.statusMessage.isNullOrBlank()) {
            Text(
                text = state.statusMessage.orEmpty(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (aiConfigDialogOpen) {
        AiConfigurationDialog(
            currentMaxOutputTokens = state.maxOutputTokens,
            currentTopK = state.topK,
            currentTopP = state.topP,
            currentTemperature = state.temperature,
            currentBackend = state.localExecutionBackend,
            currentThinkingEnabled = state.thinkingEnabled,
            onDismiss = { aiConfigDialogOpen = false },
            onConfirm = { maxTokens, topK, topP, temperature, backend, thinkingEnabled ->
                onMaxOutputTokensChange(maxTokens)
                onTopKChange(topK)
                onTopPChange(topP)
                onTemperatureChange(temperature)
                onLocalExecutionBackendChange(backend)
                onThinkingEnabledChange(thinkingEnabled)
                aiConfigDialogOpen = false
            },
        )
    }
}

@Composable
private fun AiConfigurationDialog(
    currentMaxOutputTokens: Int,
    currentTopK: Int,
    currentTopP: Double,
    currentTemperature: Double,
    currentBackend: LocalExecutionBackend,
    currentThinkingEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Double, Double, LocalExecutionBackend, Boolean) -> Unit,
) {
    var maxOutputTokensText by remember(currentMaxOutputTokens) { mutableStateOf(currentMaxOutputTokens.toString()) }
    var topKText by remember(currentTopK) { mutableStateOf(currentTopK.toString()) }
    var topPText by remember(currentTopP) { mutableStateOf(formatDecimal(currentTopP)) }
    var temperatureText by remember(currentTemperature) { mutableStateOf(formatDecimal(currentTemperature)) }
    var backend by remember(currentBackend) { mutableStateOf(currentBackend) }
    var thinkingEnabled by remember(currentThinkingEnabled) { mutableStateOf(currentThinkingEnabled) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Configurations",
                    style = MaterialTheme.typography.headlineSmall,
                )

                LabeledSliderField(
                    label = "Max tokens",
                    valueText = maxOutputTokensText,
                    onValueTextChange = { newValue ->
                        if (newValue.isBlank() || newValue.all { it.isDigit() }) {
                            maxOutputTokensText = newValue
                        }
                    },
                    sliderValue = maxOutputTokensText.toIntOrNull()?.coerceIn(256, 4000)?.toFloat()
                        ?: currentMaxOutputTokens.toFloat(),
                    onSliderChange = { value ->
                        maxOutputTokensText = value.toInt().coerceIn(256, 4000).toString()
                    },
                    sliderRange = 256f..4000f,
                    valueHint = "256 - 4000",
                )

                LabeledSliderField(
                    label = "TopK",
                    valueText = topKText,
                    onValueTextChange = { newValue ->
                        if (newValue.isBlank() || newValue.all { it.isDigit() }) {
                            topKText = newValue
                        }
                    },
                    sliderValue = topKText.toIntOrNull()?.coerceIn(1, 128)?.toFloat()
                        ?: currentTopK.toFloat(),
                    onSliderChange = { value ->
                        topKText = value.toInt().coerceIn(1, 128).toString()
                    },
                    sliderRange = 1f..128f,
                    valueHint = "1 - 128",
                )

                LabeledSliderField(
                    label = "TopP",
                    valueText = topPText,
                    onValueTextChange = { newValue ->
                        if (newValue.isBlank() || newValue.all { it.isDigit() || it == '.' }) {
                            topPText = newValue
                        }
                    },
                    sliderValue = topPText.toDoubleOrNull()?.coerceIn(0.0, 1.0)?.toFloat()
                        ?: currentTopP.toFloat(),
                    onSliderChange = { value ->
                        topPText = formatDecimal(value.toDouble().coerceIn(0.0, 1.0))
                    },
                    sliderRange = 0f..1f,
                    valueHint = "0.00 - 1.00",
                )

                LabeledSliderField(
                    label = "Temperature",
                    valueText = temperatureText,
                    onValueTextChange = { newValue ->
                        if (newValue.isBlank() || newValue.all { it.isDigit() || it == '.' }) {
                            temperatureText = newValue
                        }
                    },
                    sliderValue = temperatureText.toDoubleOrNull()?.coerceIn(0.0, 2.0)?.toFloat()
                        ?: currentTemperature.toFloat(),
                    onSliderChange = { value ->
                        temperatureText = formatDecimal(value.toDouble().coerceIn(0.0, 2.0))
                    },
                    sliderRange = 0f..2f,
                    valueHint = "0.00 - 2.00",
                )

                Text(
                    text = "Accelerator",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalExecutionBackend.entries.forEach { candidate ->
                        FilterChip(
                            selected = backend == candidate,
                            onClick = { backend = candidate },
                            label = { Text(candidate.label) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Enable thinking",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { thinkingEnabled = it },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onConfirm(
                                maxOutputTokensText.toIntOrNull()?.coerceIn(256, 4000)
                                    ?: currentMaxOutputTokens,
                                topKText.toIntOrNull()?.coerceIn(1, 128)
                                    ?: currentTopK,
                                topPText.toDoubleOrNull()?.coerceIn(0.0, 1.0)
                                    ?: currentTopP,
                                temperatureText.toDoubleOrNull()?.coerceIn(0.0, 2.0)
                                    ?: currentTemperature,
                                backend,
                                thinkingEnabled,
                            )
                        },
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSliderField(
    label: String,
    valueText: String,
    onValueTextChange: (String) -> Unit,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    sliderRange: ClosedFloatingPointRange<Float>,
    valueHint: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                valueRange = sliderRange,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueTextChange,
                modifier = Modifier.width(112.dp),
                singleLine = true,
            )
        }
    }
}

private fun formatDecimal(value: Double): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun formatOneDecimal(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun Double.roundToSpeechRateStep(): Double {
    return ((coerceIn(1.0, 2.0) * 5.0).roundToInt() / 5.0).coerceIn(1.0, 2.0)
}

@Composable
private fun SelectionField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            trailingIcon = {
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text("Choose")
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            menuContent()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AttachmentPreview(
    attachment: Attachment,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(attachment.displayName, attachment.dataUrl, attachment.uri) {
        attachment.toBitmap(context)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = attachment.displayName,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

private fun Attachment.toBitmap(context: Context): Bitmap? {
    dataUrl?.let { encoded ->
        val commaIndex = encoded.indexOf(',')
        val payload = if (commaIndex >= 0) encoded.substring(commaIndex + 1) else encoded
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    uri?.let { selectedUri ->
        return runCatching {
            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                val bytes = input.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
    return null
}

private fun Bitmap.toSelectedPlantAttachment(): Attachment {
    val scaled = scaleDown(maxDimension = 1024)
    val stream = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    return Attachment(
        uri = null,
        displayName = "plant-${java.util.UUID.randomUUID().toString().take(8)}.jpg",
        mimeType = "image/jpeg",
        kind = AttachmentKind.IMAGE,
        dataUrl = "data:image/jpeg;base64,$base64",
    )
}

private fun Uri.toSelectedPlantAttachment(context: Context): Attachment {
    val mimeType = context.contentResolver.getType(this) ?: "image/jpeg"
    val displayName = lastPathSegment?.takeIf { it.isNotBlank() } ?: "selected-image.jpg"
    val rawBytes = context.contentResolver.openInputStream(this)?.use { input ->
        input.readBytes()
    } ?: throw IllegalStateException("Could not read selected image file.")

    val bitmap = runCatching { BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) }.getOrNull()
    val encodedBytes = if (bitmap != null) {
        val scaled = bitmap.scaleDown(maxDimension = 1400)
        val stream = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        stream.toByteArray()
    } else {
        rawBytes
    }
    val base64 = Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
    return Attachment(
        uri = this,
        displayName = displayName,
        mimeType = mimeType,
        kind = AttachmentKind.IMAGE,
        dataUrl = "data:$mimeType;base64,$base64",
    )
}

private fun decodeScaledBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null

    val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    }
}

private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
    val longest = maxOf(width, height).toFloat()
    val scale = if (longest <= maxDimension) 1f else maxDimension / longest
    if (scale >= 1f) return this
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxDimension || currentHeight > maxDimension) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

