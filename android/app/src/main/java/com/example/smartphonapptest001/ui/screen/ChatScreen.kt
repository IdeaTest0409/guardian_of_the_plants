package com.example.smartphonapptest001.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import com.example.smartphonapptest001.R
import com.example.smartphonapptest001.data.local.LocalModelDownloadState
import com.example.smartphonapptest001.data.local.LocalModelDownloadStatus
import com.example.smartphonapptest001.data.local.LocalModelRuntimeState
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.structuredDetails
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AttachmentKind
import com.example.smartphonapptest001.data.model.AvatarPresentationMode
import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.data.model.LocalExecutionBackend
import com.example.smartphonapptest001.data.model.PlantImageSelectionMode
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.ui.component.CameraChatBackground
import com.example.smartphonapptest001.ui.component.GuardianAvatar3DStage
import com.example.smartphonapptest001.ui.component.GuardianMrBackgroundMode
import com.example.smartphonapptest001.ui.component.GuardianMixedRealityStage
import com.example.smartphonapptest001.ui.model.GuardianAvatarMood
import com.example.smartphonapptest001.viewmodel.ChatUiState
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import java.util.Locale

@Composable
fun ChatScreen(
    state: ChatUiState,
    appLogger: AppLogger,
    cameraPermissionGranted: Boolean,
    arCoreSupported: Boolean,
    providerType: ProviderType,
    chatStatusBarVisible: Boolean,
    localExecutionBackend: LocalExecutionBackend,
    avatarPresentationMode: AvatarPresentationMode,
    avatarExpressionEnabled: Boolean,
    mrAvatarMotionEnabled: Boolean,
    mrModelControlsVisible: Boolean,
    mrModelOffsetX: Float,
    mrModelOffsetY: Float,
    mrModelScale: Float,
    mrModelCameraDistance: Float,
    mrModelYawDegrees: Float,
    mrModelTiltDegrees: Float,
    localRuntimeState: LocalModelRuntimeState,
    localDownloadState: LocalModelDownloadState,
    selectedPlantImage: Attachment?,
    onRealtimePlantImageCaptured: (Attachment) -> Unit,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onResetChat: () -> Unit,
    onMrModelOffsetXChange: (Float) -> Unit,
    onMrModelOffsetYChange: (Float) -> Unit,
    onMrModelScaleChange: (Float) -> Unit,
    onMrModelCameraDistanceChange: (Float) -> Unit,
    onMrModelYawDegreesChange: (Float) -> Unit,
    onMrModelTiltDegreesChange: (Float) -> Unit,
    onMrModelPlacementReset: () -> Unit,
    onMrModelPlacementSetDefault: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedImage = selectedPlantImage?.takeIf { it.kind == AttachmentKind.IMAGE }
    val latestUserMessage = state.messages.lastOrNull { it.role == ChatRole.USER }?.content
    var inputExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (recognizedText.isNotBlank()) {
                onMessageChange(recognizedText)
                inputExpanded = true
                appLogger.log(
                    AppLogSeverity.INFO,
                    "ChatScreen",
                    "Speech input recognized",
                    details = structuredDetails(
                        "chars" to recognizedText.length,
                    ),
                )
            }
        }
    }

    fun startSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "植物へのメッセージ")
        }
        runCatching {
            speechLauncher.launch(intent)
        }.onFailure { error ->
            Toast.makeText(context, "音声入力を開始できませんでした", Toast.LENGTH_SHORT).show()
            appLogger.log(
                AppLogSeverity.WARN,
                "ChatScreen",
                "Speech input launch failed",
                details = structuredDetails(
                    "message" to error.message.orEmpty(),
                ),
            )
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 0.dp),
    ) {
        val effectiveAvatarPresentationMode = avatarPresentationMode
        val stageHeight = when (effectiveAvatarPresentationMode) {
            AvatarPresentationMode.THREE_D, AvatarPresentationMode.AR -> maxHeight * 0.58f
            else -> maxHeight * 0.52f
        }
        val conversationHeight = when {
            inputExpanded -> 96.dp
            state.messages.isEmpty() -> 116.dp
            else -> 132.dp
        }
        val statusText = if (providerType == ProviderType.LOCAL) {
            "Local • ${compactLocalStatus(localRuntimeState, localExecutionBackend)}"
        } else if (providerType == ProviderType.SERVER) {
            "Server VPS • ${state.activeProviderSummary}"
        } else {
            "Cloud • ${state.activeProviderSummary}"
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            RowHeader(onResetChat = onResetChat)

            if (chatStatusBarVisible) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (effectiveAvatarPresentationMode == AvatarPresentationMode.AR) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stageHeight),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                ) {
                    val avatarMood = resolveAvatarMood(
                        isSending = state.isSending,
                        errorMessage = state.errorMessage,
                        avatarMoodOverride = state.avatarMoodOverride,
                    )
                    if (state.plantImageSelectionMode == PlantImageSelectionMode.REALTIME_CAPTURE) {
                        GuardianMixedRealityStage(
                            companionRole = state.activeCompanionRole,
                            mood = avatarMood,
                            enabled = cameraPermissionGranted,
                            logger = appLogger,
                            blinkAndMouthEnabled = mrAvatarMotionEnabled,
                            controlsVisible = mrModelControlsVisible,
                            offsetX = mrModelOffsetX,
                            offsetY = mrModelOffsetY,
                            modelScale = mrModelScale,
                            cameraDistance = mrModelCameraDistance,
                            yawDegrees = mrModelYawDegrees,
                            tiltDegrees = mrModelTiltDegrees,
                            onOffsetXChange = onMrModelOffsetXChange,
                            onOffsetYChange = onMrModelOffsetYChange,
                            onModelScaleChange = onMrModelScaleChange,
                            onCameraDistanceChange = onMrModelCameraDistanceChange,
                            onYawDegreesChange = onMrModelYawDegreesChange,
                            onTiltDegreesChange = onMrModelTiltDegreesChange,
                            onPlacementReset = onMrModelPlacementReset,
                            onPlacementSetDefault = onMrModelPlacementSetDefault,
                            onFrameCaptured = onRealtimePlantImageCaptured,
                            modifier = Modifier.fillMaxSize(),
                            backgroundMode = GuardianMrBackgroundMode.REALTIME_CAMERA,
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (selectedImage != null) {
                                AttachmentPreview(
                                    attachment = selectedImage,
                                    context = context,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = when (state.plantImageSelectionMode) {
                                            PlantImageSelectionMode.CAMERA_PHOTO -> "植物の写真を撮影してください。"
                                            PlantImageSelectionMode.FILE_UPLOAD -> "植物の画像ファイルを選択してください。"
                                            PlantImageSelectionMode.REALTIME_CAPTURE -> "Camera preview unavailable"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            AnimatedGuardianAvatar(
                                companionRole = state.activeCompanionRole,
                                mood = avatarMood,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                            )
                        }
                        LaunchedEffect(state.plantImageSelectionMode, selectedImage?.displayName) {
                            appLogger.log(
                                AppLogSeverity.INFO,
                                "ChatScreen",
                                "MR static image fallback active",
                                details = structuredDetails(
                                    "plantImageSelectionMode" to state.plantImageSelectionMode.name,
                                    "selectedImagePresent" to (selectedImage != null),
                                    "reason" to "Scene ImageNode static background can crash Filament on device",
                                ),
                            )
                        }
                    }
                }
            } else if (effectiveAvatarPresentationMode == AvatarPresentationMode.THREE_D) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stageHeight),
                ) {
                    val paneWidth = (maxWidth - 20.dp) / 2
                    val paneHeight = this.maxHeight
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(paneWidth)
                                .height(paneHeight),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (state.plantImageSelectionMode == PlantImageSelectionMode.REALTIME_CAPTURE) {
                                    CameraChatBackground(
                                        modifier = Modifier.fillMaxSize(),
                                        enabled = cameraPermissionGranted,
                                        onFrameCaptured = onRealtimePlantImageCaptured,
                                    )
                                } else if (selectedImage != null) {
                                    AttachmentPreview(
                                        attachment = selectedImage,
                                        context = context,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Stage 1 で植物画像を選んでください。",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .width(paneWidth)
                                .height(paneHeight),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                        ) {
                            val avatarMood = resolveAvatarMood(
                                isSending = state.isSending,
                                errorMessage = state.errorMessage,
                                avatarMoodOverride = state.avatarMoodOverride,
                            )
                            GuardianAvatar3DStage(
                            companionRole = state.activeCompanionRole,
                            mood = avatarMood,
                            lastUserMessage = latestUserMessage,
                            logger = appLogger,
                            expressionEnabled = avatarExpressionEnabled,
                            modifier = Modifier.fillMaxSize(),
                        )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stageHeight),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (state.plantImageSelectionMode == PlantImageSelectionMode.REALTIME_CAPTURE) {
                            CameraChatBackground(
                                modifier = Modifier.fillMaxSize(),
                                enabled = cameraPermissionGranted,
                                onFrameCaptured = onRealtimePlantImageCaptured,
                            )
                        } else if (selectedImage != null) {
                            AttachmentPreview(
                                attachment = selectedImage,
                                context = context,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Stage 1 で植物画像を選んでください。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        val avatarMood = resolveAvatarMood(
                            isSending = state.isSending,
                            errorMessage = state.errorMessage,
                            avatarMoodOverride = state.avatarMoodOverride,
                        )
                        AnimatedGuardianAvatar(
                            companionRole = state.activeCompanionRole,
                            mood = avatarMood,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp),
                        )
                    }
                }
            }

            if (state.errorMessage != null) {
                AssistChip(
                    onClick = { },
                    label = { Text(state.errorMessage, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(conversationHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                if (state.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (selectedImage != null) {
                                "Type a message to start chatting."
                            } else {
                                "植物画像を選択するとチャットできます。"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            ChatBubble(
                                message = message,
                                companionRoleName = state.activeCompanionRoleName,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            MessageInputBar(
                value = state.messageDraft,
                expanded = inputExpanded,
                isSending = state.isSending,
                onExpandedChange = { inputExpanded = it },
                onValueChange = onMessageChange,
                onSendMessage = onSendMessage,
                onSpeechInput = ::startSpeechInput,
            )

            QuickReplyPresets(
                onPresetTap = onMessageChange,
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    expanded: Boolean,
    isSending: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSpeechInput: () -> Unit,
) {
    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                label = { Text("Type a message") },
                minLines = 1,
                maxLines = 1,
            )
            IconButton(onClick = onSpeechInput) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Speech input",
                )
            }
            IconButton(onClick = { onExpandedChange(false) }) {
                Icon(
                    imageVector = Icons.Filled.ExpandLess,
                    contentDescription = "Collapse message input",
                )
            }
            IconButton(
                onClick = onSendMessage,
                enabled = !isSending,
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send message",
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = { onExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = "Expand message input",
                    )
                }
                Text(
                    text = value.ifBlank { "短縮ボタン・音声入力・手入力" },
                    modifier = Modifier.weight(1f),
                    color = if (value.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onSpeechInput) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Speech input",
                    )
                }
                IconButton(
                    onClick = onSendMessage,
                    enabled = !isSending,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send message",
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickReplyPresets(
    onPresetTap: (String) -> Unit,
) {
    val presets = listOf(
        "こんにちは",
        "元気？",
        "葉っぱ大丈夫？",
        "水足たりてる？",
        "触って良い？",
        "色は大丈夫？",
        "今の季節大丈夫？",
        "植物は何と言っている？",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        presets.chunked(4).forEach { rowPresets ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rowPresets.forEach { preset ->
                    MiniPresetButton(text = preset, onClick = { onPresetTap(preset) })
                }
            }
        }
    }
}

@Composable
private fun MiniPresetButton(
    text: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 1,
            )
        },
        modifier = Modifier
            .height(24.dp),
    )
}

@Composable
private fun RowHeader(
    onResetChat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Plant Guardian",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onResetChat) {
            Text("Reset")
        }
    }
}

private fun compactLocalStatus(
    runtimeState: LocalModelRuntimeState,
    backend: LocalExecutionBackend,
): String {
    val modelText = runtimeState.activeModelId?.takeIf { it.isNotBlank() }
    val backendText = backend.label
    return if (modelText.isNullOrBlank()) {
        "Local / $backendText"
    } else {
        "$modelText / $backendText"
    }
}

@Composable
private fun AnimatedGuardianAvatar(
    companionRole: CompanionRole,
    mood: GuardianAvatarMood,
    modifier: Modifier = Modifier,
) {
    var frameIndex by remember(companionRole, mood) { mutableIntStateOf(0) }
    LaunchedEffect(companionRole, mood) {
        frameIndex = 0
        if (mood == GuardianAvatarMood.SPEAKING) {
            while (true) {
                delay(180L)
                frameIndex = (frameIndex + 1) % 2
            }
        }
    }
    val targetRes = resolveGuardianAvatarRes(companionRole, mood, frameIndex)
    val scale by animateFloatAsState(
        targetValue = when (mood) {
            GuardianAvatarMood.LISTENING -> 1.01f
            GuardianAvatarMood.SPEAKING -> if (frameIndex % 2 == 0) 1.04f else 1.01f
            GuardianAvatarMood.THINKING -> 1.01f
            GuardianAvatarMood.ERROR -> 0.98f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "guardianAvatarScale",
    )
    val breatheOffset = when (mood) {
        GuardianAvatarMood.LISTENING -> 0.3f
        GuardianAvatarMood.SPEAKING -> if (frameIndex % 2 == 0) -1.2f else 0.2f
        GuardianAvatarMood.THINKING -> 0.4f
        GuardianAvatarMood.ERROR -> -0.2f
        else -> 0f
    }
    val tiltDegrees = when (mood) {
        GuardianAvatarMood.LISTENING -> if (frameIndex % 2 == 0) 1.0f else -1.0f
        GuardianAvatarMood.SPEAKING -> if (frameIndex % 2 == 0) 1.4f else -1.4f
        GuardianAvatarMood.THINKING -> 2.0f
        GuardianAvatarMood.ERROR -> if (frameIndex % 2 == 0) -2.0f else 2.0f
        else -> 0.0f
    }
    val horizontalJitter = when (mood) {
        GuardianAvatarMood.LISTENING -> if (frameIndex % 2 == 0) -0.2f else 0.2f
        GuardianAvatarMood.SPEAKING -> if (frameIndex % 2 == 0) -0.6f else 0.6f
        GuardianAvatarMood.THINKING -> 0.2f
        GuardianAvatarMood.ERROR -> 0f
        else -> 0f
    }
    val contentAlpha = when (mood) {
        GuardianAvatarMood.ERROR -> 0.95f
        else -> 1f
    }

    Surface(
        modifier = modifier.width(96.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Crossfade(
            targetState = targetRes,
            animationSpec = tween(durationMillis = if (mood == GuardianAvatarMood.SPEAKING) 100 else 160),
            label = "guardianAvatarCrossfade",
        ) { resId ->
            val painter: Painter = painterResource(id = resId)
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .size(128.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = breatheOffset
                        translationX = horizontalJitter
                        rotationZ = tiltDegrees
                        this.alpha = contentAlpha
                    },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun resolveGuardianAvatarRes(
    companionRole: CompanionRole,
    mood: GuardianAvatarMood,
    frameIndex: Int,
): Int {
    return when (companionRole) {
        CompanionRole.ANGEL -> when (mood) {
            GuardianAvatarMood.LISTENING -> R.drawable.guardian_angel_listening
            GuardianAvatarMood.SPEAKING -> if (frameIndex % 2 == 0) {
                R.drawable.guardian_angel_speaking
            } else {
                R.drawable.guardian_angel_speaking_alt
            }
            GuardianAvatarMood.THINKING -> R.drawable.guardian_angel_thinking
            GuardianAvatarMood.ERROR -> R.drawable.guardian_angel_error
            else -> R.drawable.guardian_angel_idle
        }
        CompanionRole.BUTLER -> when (mood) {
            GuardianAvatarMood.LISTENING -> R.drawable.guardian_butler_listening
            GuardianAvatarMood.SPEAKING -> if (frameIndex % 2 == 0) {
                R.drawable.guardian_butler_speaking
            } else {
                R.drawable.guardian_butler_speaking_alt
            }
            GuardianAvatarMood.THINKING -> R.drawable.guardian_butler_thinking
            GuardianAvatarMood.ERROR -> R.drawable.guardian_butler_error
            else -> R.drawable.guardian_butler_idle
        }
    }
}

private fun resolveAvatarMood(
    isSending: Boolean,
    errorMessage: String?,
    avatarMoodOverride: GuardianAvatarMood?,
): GuardianAvatarMood {
    return when {
        !errorMessage.isNullOrBlank() -> GuardianAvatarMood.ERROR
        avatarMoodOverride != null -> avatarMoodOverride
        isSending -> GuardianAvatarMood.LISTENING
        else -> GuardianAvatarMood.IDLE
    }
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

@Composable
private fun ChatBubble(
    message: ChatMessage,
    companionRoleName: String,
) {
    val isUser = message.role == ChatRole.USER
    val container = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = MaterialTheme.shapes.large,
            color = container.copy(alpha = 0.92f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when (message.role) {
                        ChatRole.USER -> "You"
                        ChatRole.ASSISTANT -> companionRoleName
                        ChatRole.SYSTEM -> "System"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content.ifBlank { if (message.isPending) "..." else "" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
