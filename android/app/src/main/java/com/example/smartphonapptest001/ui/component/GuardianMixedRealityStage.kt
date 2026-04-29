package com.example.smartphonapptest001.ui.component

import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.structuredDetails
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.ui.model.GuardianAvatarMood
import com.google.android.filament.Renderer
import com.google.android.filament.View as FilamentView
import io.github.sceneview.Scene
import io.github.sceneview.SurfaceType
import io.github.sceneview.environment.Environment as SceneEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberView
import java.util.Locale
import kotlin.math.sin
import kotlinx.coroutines.delay

enum class GuardianMrBackgroundMode {
    REALTIME_CAMERA,
    STATIC_IMAGE,
    PLACEHOLDER,
}

@Composable
fun GuardianMixedRealityStage(
    companionRole: CompanionRole,
    mood: GuardianAvatarMood,
    enabled: Boolean,
    logger: AppLogger,
    blinkAndMouthEnabled: Boolean,
    controlsVisible: Boolean,
    offsetX: Float,
    offsetY: Float,
    modelScale: Float,
    cameraDistance: Float,
    yawDegrees: Float,
    tiltDegrees: Float,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onModelScaleChange: (Float) -> Unit,
    onCameraDistanceChange: (Float) -> Unit,
    onYawDegreesChange: (Float) -> Unit,
    onTiltDegreesChange: (Float) -> Unit,
    onPlacementReset: () -> Unit,
    onPlacementSetDefault: () -> Unit,
    onFrameCaptured: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: (@Composable () -> Unit)? = null,
    backgroundMode: GuardianMrBackgroundMode = if (backgroundContent == null) {
        GuardianMrBackgroundMode.REALTIME_CAMERA
    } else {
        GuardianMrBackgroundMode.STATIC_IMAGE
    },
) {
    Box(modifier = modifier) {
        val context = LocalContext.current
        val modelBasePosition = remember(offsetX, offsetY) {
            Position(
                x = offsetX,
                y = offsetY,
                z = 0f,
            )
        }
        val modelAssetPath = remember(companionRole) {
            when (companionRole) {
                CompanionRole.ANGEL -> "models/angel_egna.glb"
                CompanionRole.BUTLER -> "models/guardian_butler.glb"
            }
        }
        val fallbackModelAssetPath = remember(companionRole) {
            when (companionRole) {
                CompanionRole.ANGEL -> "models/guardian_angel.glb"
                CompanionRole.BUTLER -> "models/guardian_butler.glb"
            }
        }
        val resolvedModelAssetPath = remember(modelAssetPath, fallbackModelAssetPath) {
            when {
                context.assetExists(modelAssetPath) -> modelAssetPath
                context.assetExists(fallbackModelAssetPath) -> fallbackModelAssetPath
                else -> modelAssetPath
            }
        }
        val placementDebugDetails = remember(
            resolvedModelAssetPath,
            companionRole,
            mood,
            blinkAndMouthEnabled,
            backgroundMode,
            offsetX,
            offsetY,
            modelScale,
            cameraDistance,
            yawDegrees,
            tiltDegrees,
        ) {
            mrPlacementDebugDetails(
                modelAssetPath = resolvedModelAssetPath,
                modelAssetExists = context.assetExists(resolvedModelAssetPath),
                companionRole = companionRole,
                mood = mood,
                blinkAndMouthEnabled = blinkAndMouthEnabled,
                backgroundMode = backgroundMode,
                offsetX = offsetX,
                offsetY = offsetY,
                modelScale = modelScale,
                cameraDistance = cameraDistance,
                yawDegrees = yawDegrees,
                tiltDegrees = tiltDegrees,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (backgroundMode) {
                GuardianMrBackgroundMode.REALTIME_CAMERA -> {
                    CameraChatBackground(
                        modifier = Modifier.fillMaxSize(),
                        enabled = enabled,
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE,
                        onFrameCaptured = onFrameCaptured,
                    )
                }

                GuardianMrBackgroundMode.PLACEHOLDER -> {
                    backgroundContent?.invoke()
                }

                GuardianMrBackgroundMode.STATIC_IMAGE -> {
                    backgroundContent?.invoke()
                }
            }

            key(modelScale, cameraDistance) {
                Guardian3dMixedRealityOverlay(
                    companionRole = companionRole,
                    mood = mood,
                    modelAssetPath = resolvedModelAssetPath,
                    basePosition = modelBasePosition,
                    logger = logger,
                    blinkAndMouthEnabled = blinkAndMouthEnabled,
                    backgroundMode = backgroundMode,
                    modelScale = modelScale,
                    cameraDistance = cameraDistance,
                    yawDegrees = yawDegrees,
                    tiltDegrees = tiltDegrees,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (controlsVisible) {
                MrPlacementControls(
                    offsetX = offsetX,
                    offsetY = offsetY,
                    modelScale = modelScale,
                    cameraDistance = cameraDistance,
                    yawDegrees = yawDegrees,
                    tiltDegrees = tiltDegrees,
                    onOffsetXChange = onOffsetXChange,
                    onOffsetYChange = onOffsetYChange,
                    onModelScaleChange = onModelScaleChange,
                    onCameraDistanceChange = onCameraDistanceChange,
                    onYawDegreesChange = onYawDegreesChange,
                    onTiltDegreesChange = onTiltDegreesChange,
                    onPlacementReset = onPlacementReset,
                    onPlacementSetDefault = onPlacementSetDefault,
                    debugText = placementDebugDetails,
                    onPlacementSnapshot = {
                        logger.log(
                            AppLogSeverity.INFO,
                            "GuardianMixedRealityStage",
                            "MR placement snapshot",
                            details = placementDebugDetails,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun Guardian3dMixedRealityOverlay(
    companionRole: CompanionRole,
    mood: GuardianAvatarMood,
    modelAssetPath: String,
    basePosition: Position,
    logger: AppLogger,
    blinkAndMouthEnabled: Boolean,
    backgroundMode: GuardianMrBackgroundMode,
    modelScale: Float,
    cameraDistance: Float,
    yawDegrees: Float,
    tiltDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val filamentView = rememberView(engine).apply {
        blendMode = FilamentView.BlendMode.TRANSLUCENT
    }
    val renderer = rememberRenderer(engine).apply {
        clearOptions = Renderer.ClearOptions().apply {
            clear = false
            discard = false
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
    }
    val cameraHomePosition = remember(mood, cameraDistance) {
        when (mood) {
            GuardianAvatarMood.LISTENING -> Position(0f, 0.98f, -cameraDistance)
            GuardianAvatarMood.SPEAKING -> Position(0f, 1.00f, -cameraDistance)
            GuardianAvatarMood.THINKING -> Position(0f, 0.99f, -cameraDistance)
            GuardianAvatarMood.ERROR -> Position(0f, 0.96f, -cameraDistance)
            else -> Position(0f, 0.98f, -cameraDistance)
        }
    }
    val cameraTargetPosition = remember(mood) {
        when (mood) {
            GuardianAvatarMood.LISTENING -> Position(0f, 0.94f, 0f)
            GuardianAvatarMood.SPEAKING -> Position(0f, 0.96f, 0f)
            GuardianAvatarMood.THINKING -> Position(0f, 0.94f, 0f)
            GuardianAvatarMood.ERROR -> Position(0f, 0.92f, 0f)
            else -> Position(0f, 0.94f, 0f)
        }
    }
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = cameraHomePosition,
        targetPosition = cameraTargetPosition,
    )

    LaunchedEffect(
        modelAssetPath,
        companionRole,
        modelScale,
        cameraDistance,
        yawDegrees,
        tiltDegrees,
        blinkAndMouthEnabled,
        backgroundMode,
    ) {
        logger.log(
            AppLogSeverity.INFO,
            "GuardianMixedRealityStage",
            "MR 3D scene setup",
            details = structuredDetails(
                "backgroundMode" to backgroundMode.name,
                "cameraImplementationMode" to if (backgroundMode == GuardianMrBackgroundMode.REALTIME_CAMERA) {
                    "PERFORMANCE"
                } else {
                    "not_used"
                },
                "surfaceType" to "TextureSurface",
                "isOpaque" to false,
                "blendMode" to "TRANSLUCENT",
                "clear" to false,
                "discard" to false,
                "environment" to "empty",
                "modelAsset" to modelAssetPath,
                "modelAssetExists" to context.assetExists(modelAssetPath),
                "companionRole" to companionRole.name,
                "mood" to mood.name,
                "positionMode" to "fixed_bottom_end",
                "scaleToUnits" to modelScale.format2(),
                "centerOrigin" to "(0.00, 1.08, 0.00)",
                "cameraHome" to cameraHomePosition.toDebugString(),
                "cameraTarget" to cameraTargetPosition.toDebugString(),
                "cameraDistance" to cameraDistance.format2(),
                "yawDegrees" to yawDegrees.format2(),
                "tiltDegrees" to tiltDegrees.format2(),
                "blinkAndMouthEnabled" to blinkAndMouthEnabled,
            ),
        )
    }

    LaunchedEffect(basePosition) {
        logger.log(
            AppLogSeverity.DEBUG,
            "GuardianMixedRealityStage",
            "MR 3D model target updated",
            details = structuredDetails(
                "x" to basePosition.x.format2(),
                "y" to basePosition.y.format2(),
                "z" to basePosition.z.format2(),
            ),
        )
    }

    LaunchedEffect(backgroundMode) {
        logger.log(
            AppLogSeverity.INFO,
            "GuardianMixedRealityStage",
            "MR background mode",
            details = structuredDetails(
                "backgroundMode" to backgroundMode.name,
                "usesCameraX" to (backgroundMode == GuardianMrBackgroundMode.REALTIME_CAMERA),
                "usesSceneImageBackground" to false,
            ),
        )
    }

    Scene(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
        view = filamentView,
        renderer = renderer,
        environment = SceneEnvironment(),
        modelLoader = modelLoader,
        cameraManipulator = cameraManipulator,
    ) {
        rememberModelInstance(modelLoader, modelAssetPath)?.let { modelInstance ->
            LaunchedEffect(modelInstance, modelAssetPath) {
                logger.log(
                    AppLogSeverity.INFO,
                    "GuardianMixedRealityStage",
                    "MR 3D model loaded",
                    details = structuredDetails(
                        "modelAsset" to modelAssetPath,
                        "companionRole" to companionRole.name,
                    ),
                )
            }
            ModelNode(
                modelInstance = modelInstance,
                autoAnimate = false,
                scaleToUnits = modelScale,
                centerOrigin = Position(0f, 1.08f, 0f),
            ) {
                Guardian3dFixedPoseDriver(
                    modelNode = parentNode as ModelNode,
                    basePosition = basePosition,
                    yawDegrees = yawDegrees,
                    tiltDegrees = tiltDegrees,
                )
                if (blinkAndMouthEnabled) {
                    Guardian3dBlinkMouthDriver(
                        modelNode = parentNode as ModelNode,
                        mood = mood,
                        logger = logger,
                    )
                } else {
                    DisabledMrBlinkMouthLogger(logger)
                }
            }
        }
    }
}

@Composable
private fun MrPlacementControls(
    offsetX: Float,
    offsetY: Float,
    modelScale: Float,
    cameraDistance: Float,
    yawDegrees: Float,
    tiltDegrees: Float,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onModelScaleChange: (Float) -> Unit,
    onCameraDistanceChange: (Float) -> Unit,
    onYawDegreesChange: (Float) -> Unit,
    onTiltDegreesChange: (Float) -> Unit,
    onPlacementReset: () -> Unit,
    onPlacementSetDefault: () -> Unit,
    debugText: String,
    onPlacementSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(210.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "MR camera",
                style = MaterialTheme.typography.labelMedium,
            )
            CompactAdjustRow(
                label = "X",
                value = offsetX,
                step = 0.04f,
                onValueChange = onOffsetXChange,
            )
            CompactAdjustRow(
                label = "Y",
                value = offsetY,
                step = 0.04f,
                onValueChange = onOffsetYChange,
            )
            CompactAdjustRow(
                label = "Size",
                value = modelScale,
                step = 0.04f,
                onValueChange = onModelScaleChange,
            )
            CompactAdjustRow(
                label = "Dist",
                value = cameraDistance,
                step = 0.04f,
                onValueChange = onCameraDistanceChange,
            )
            CompactAdjustRow(
                label = "Yaw",
                value = yawDegrees,
                step = 5f,
                onValueChange = onYawDegreesChange,
            )
            CompactAdjustRow(
                label = "Tilt",
                value = tiltDegrees,
                step = 5f,
                onValueChange = onTiltDegreesChange,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPlacementReset) {
                    Text("Reset")
                }
                Button(onClick = onPlacementSetDefault) {
                    Text("Set default")
                }
            }
            Button(onClick = onPlacementSnapshot) {
                Text("Log values")
            }
            Text(
                text = debugText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactAdjustRow(
    label: String,
    value: Float,
    step: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label ${value.format2()}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
        )
        TextButton(onClick = { onValueChange(value - step) }) {
            Text("-")
        }
        TextButton(onClick = { onValueChange(value + step) }) {
            Text("+")
        }
    }
}

@Composable
private fun DisabledMrBlinkMouthLogger(logger: AppLogger) {
    LaunchedEffect(Unit) {
        logger.log(
            AppLogSeverity.INFO,
            "GuardianMixedRealityStage",
            "MR blink and mouth disabled",
            details = structuredDetails(
                "reason" to "Setting is off by default because morph targets can crash on some devices",
            ),
        )
    }
}

@Composable
private fun Guardian3dBlinkMouthDriver(
    modelNode: ModelNode,
    mood: GuardianAvatarMood,
    logger: AppLogger,
) {
    val faceNode = remember(modelNode) {
        modelNode.renderableNodes.firstOrNull { it.morphTargetNames.isNotEmpty() }
    }
    val targetIndices = remember(faceNode) {
        faceNode?.morphTargetNames
            ?.mapIndexedNotNull { index, name ->
                normalizeLimitedMorphName(name)?.let { normalized -> normalized to index }
            }
            ?.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
            .orEmpty()
    }

    LaunchedEffect(faceNode, targetIndices) {
        logger.log(
            AppLogSeverity.INFO,
            "GuardianMixedRealityStage",
            "MR blink and mouth driver prepared",
            details = structuredDetails(
                "faceNodePresent" to (faceNode != null),
                "faceMorphTargetCount" to (faceNode?.morphTargetNames?.size ?: 0),
                "limitedMorphTargetCount" to targetIndices.size,
                "targetMorphNames" to targetIndices.keys.joinToString(separator = ","),
            ),
        )
    }

    LaunchedEffect(faceNode, mood, targetIndices) {
        if (faceNode == null || targetIndices.isEmpty()) {
            logger.log(
                AppLogSeverity.WARN,
                "GuardianMixedRealityStage",
                "MR blink and mouth driver unavailable",
                details = structuredDetails(
                    "faceNodePresent" to (faceNode != null),
                    "faceMorphTargetCount" to (faceNode?.morphTargetNames?.size ?: 0),
                    "limitedMorphTargetCount" to targetIndices.size,
                )
            )
            return@LaunchedEffect
        }

        fun applyWeights(weights: Map<String, Float>) {
            val morphWeights = FloatArray(faceNode.morphTargetNames.size)
            weights.forEach { (name, value) ->
                targetIndices[name]?.forEach { index ->
                    morphWeights[index] = value.coerceIn(0f, 1f)
                }
            }
            runCatching {
                modelNode.setMorphWeights(morphWeights)
            }.onFailure { error ->
                logger.log(
                    AppLogSeverity.ERROR,
                    "GuardianMixedRealityStage",
                    "MR blink and mouth apply failed",
                    details = error.toStructuredLogDetails(
                        "mood" to mood.name,
                        "faceMorphTargetCount" to faceNode.morphTargetNames.size,
                        "limitedMorphTargetCount" to targetIndices.size,
                    ),
                )
            }
        }

        while (true) {
            val now = SystemClock.uptimeMillis() / 1000f
            val blink = blinkPulse(now)
            val mouth = limitedMouthPulse(now, mood)
            applyWeights(
                mapOf(
                    "blink" to blink,
                    "blink_l" to blink * 0.85f,
                    "blink_r" to blink * 0.85f,
                    "a" to mouth * 0.75f,
                    "i" to mouth * 0.18f,
                    "u" to mouth * 0.10f,
                    "e" to mouth * 0.24f,
                    "o" to mouth * 0.30f,
                ),
            )
            delay(33L)
        }
    }
}

@Composable
private fun Guardian3dFixedPoseDriver(
    modelNode: ModelNode,
    basePosition: Position,
    yawDegrees: Float,
    tiltDegrees: Float,
) {
    LaunchedEffect(modelNode, basePosition, yawDegrees, tiltDegrees) {
        while (true) {
            val now = SystemClock.uptimeMillis() / 1000f
            modelNode.position = Position(
                x = basePosition.x,
                y = basePosition.y,
                z = basePosition.z,
            )
            modelNode.rotation = Position(
                x = tiltDegrees + sin(now * 0.55f) * 1.8f,
                y = yawDegrees + sin(now * 0.45f) * 2.5f,
                z = sin(now * 0.65f) * 1.4f,
            )
            delay(33L)
        }
    }
}

private fun android.content.Context.assetExists(assetPath: String): Boolean {
    return runCatching {
        assets.open(assetPath).use { }
        true
    }.getOrDefault(false)
}

private fun Float.format2(): String {
    return String.format(Locale.US, "%.2f", this)
}

private fun Position.toDebugString(): String {
    return "(${x.format2()}, ${y.format2()}, ${z.format2()})"
}

private fun mrPlacementDebugDetails(
    modelAssetPath: String,
    modelAssetExists: Boolean,
    companionRole: CompanionRole,
    mood: GuardianAvatarMood,
    blinkAndMouthEnabled: Boolean,
    backgroundMode: GuardianMrBackgroundMode,
    offsetX: Float,
    offsetY: Float,
    modelScale: Float,
    cameraDistance: Float,
    yawDegrees: Float,
    tiltDegrees: Float,
): String {
    val cameraHomeY = when (mood) {
        GuardianAvatarMood.SPEAKING -> 1.00f
        GuardianAvatarMood.ERROR -> 0.96f
        else -> 0.98f
    }
    val cameraTargetY = when (mood) {
        GuardianAvatarMood.SPEAKING -> 0.96f
        GuardianAvatarMood.ERROR -> 0.92f
        else -> 0.94f
    }
    return structuredDetails(
        "uiOffsetX" to offsetX.format2(),
        "uiOffsetY" to offsetY.format2(),
        "uiModelScale" to modelScale.format2(),
        "uiCameraDistance" to cameraDistance.format2(),
        "uiYawDegrees" to yawDegrees.format2(),
        "uiTiltDegrees" to tiltDegrees.format2(),
        "modelTarget" to Position(offsetX, offsetY, 0f).toDebugString(),
        "backgroundMode" to backgroundMode.name,
        "positionMode" to "fixed_bottom_end",
        "scaleToUnits" to modelScale.format2(),
        "centerOrigin" to "(0.00, 1.08, 0.00)",
        "cameraHome" to Position(0f, cameraHomeY, -cameraDistance).toDebugString(),
        "cameraTarget" to Position(0f, cameraTargetY, 0f).toDebugString(),
        "cameraDistance" to cameraDistance.format2(),
        "modelAsset" to modelAssetPath,
        "modelAssetExists" to modelAssetExists,
        "companionRole" to companionRole.name,
        "mood" to mood.name,
        "blinkAndMouthEnabled" to blinkAndMouthEnabled,
    )
}

private fun blinkPulse(nowSeconds: Float): Float {
    val cycle = (nowSeconds % 4.2f) / 4.2f
    return when {
        cycle < 0.03f -> 0.0f
        cycle < 0.05f -> 0.65f
        cycle < 0.07f -> 0.98f
        cycle < 0.10f -> 0.74f
        cycle < 0.12f -> 0.18f
        else -> 0.0f
    }
}

private fun limitedMouthPulse(nowSeconds: Float, mood: GuardianAvatarMood): Float {
    val rate = when (mood) {
        GuardianAvatarMood.SPEAKING -> 9.0f
        GuardianAvatarMood.LISTENING -> 4.0f
        else -> return 0.0f
    }
    return ((sin((nowSeconds * rate).toDouble()) + 1.0) / 2.0).toFloat()
}

private fun normalizeLimitedMorphName(name: String): String? {
    val normalized = name.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "blink", "fcl_eye_blink", "fcl_eye_close" -> "blink"
        "blink_l", "fcl_eye_blink_l", "fcl_eye_close_l" -> "blink_l"
        "blink_r", "fcl_eye_blink_r", "fcl_eye_close_r" -> "blink_r"
        "a", "fcl_mth_a" -> "a"
        "i", "fcl_mth_i" -> "i"
        "u", "fcl_mth_u" -> "u"
        "e", "fcl_mth_e" -> "e"
        "o", "fcl_mth_o" -> "o"
        else -> null
    }
}
