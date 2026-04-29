package com.example.smartphonapptest001.ui.component

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.structuredDetails
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.ui.model.GuardianAvatarMood
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.RenderableNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun GuardianAvatar3DStage(
    companionRole: CompanionRole,
    mood: GuardianAvatarMood,
    lastUserMessage: String?,
    logger: AppLogger,
    expressionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferredModelAssetPath = remember(companionRole) {
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
    val modelAssetPath = remember(preferredModelAssetPath, fallbackModelAssetPath) {
        when {
            context.assetExists(preferredModelAssetPath) -> preferredModelAssetPath
            context.assetExists(fallbackModelAssetPath) -> fallbackModelAssetPath
            else -> preferredModelAssetPath
        }
    }
    val hasModelAsset = remember(modelAssetPath) { context.assetExists(modelAssetPath) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraHomePosition = remember(companionRole, mood) {
        when (mood) {
            GuardianAvatarMood.LISTENING -> Position(0f, 0.98f, -0.35f)
            GuardianAvatarMood.SPEAKING -> Position(0f, 1.00f, -0.31f)
            GuardianAvatarMood.THINKING -> Position(0f, 0.99f, -0.35f)
            GuardianAvatarMood.ERROR -> Position(0f, 0.96f, -0.37f)
            else -> Position(0f, 0.98f, -0.35f)
        }
    }
    val cameraTargetPosition = remember(companionRole, mood) {
        when (mood) {
            GuardianAvatarMood.LISTENING -> Position(0f, 0.94f, 0f)
            GuardianAvatarMood.SPEAKING -> Position(0f, 0.96f, 0f)
            GuardianAvatarMood.THINKING -> Position(0f, 0.94f, 0f)
            GuardianAvatarMood.ERROR -> Position(0f, 0.92f, 0f)
            else -> Position(0f, 0.94f, 0f)
        }
    }
    val cameraDistance = remember(cameraHomePosition, cameraTargetPosition) {
        distanceBetween(cameraHomePosition, cameraTargetPosition)
    }
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = cameraHomePosition,
        targetPosition = cameraTargetPosition,
    )
    val debugDetails = remember(modelAssetPath, cameraHomePosition, cameraTargetPosition, cameraDistance, mood, expressionEnabled) {
        buildString {
            appendLine("modelAsset=$modelAssetPath")
            appendLine("mood=${mood.name}")
            appendLine("autoAnimate=false")
            appendLine("scaleToUnits=1.06")
            appendLine("centerOrigin=(0.00, 1.08, 0.00)")
            appendLine("cameraHome=${cameraHomePosition.toDebugString()}")
            appendLine("cameraTarget=${cameraTargetPosition.toDebugString()}")
            appendLine("cameraDistance=${cameraDistance.toFormattedString()}")
            appendLine("expressionEnabled=$expressionEnabled")
        }
    }

    LaunchedEffect(debugDetails) {
        logger.log(
            AppLogSeverity.INFO,
            "GuardianAvatar3DStage",
            "3D stage parameters",
            details = debugDetails,
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasModelAsset) {
                Scene(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                    engine = engine,
                    modelLoader = modelLoader,
                    cameraManipulator = cameraManipulator,
                ) {
                    rememberModelInstance(modelLoader, modelAssetPath)?.let { modelInstance ->
                        ModelNode(
                            modelInstance = modelInstance,
                            autoAnimate = false,
                            scaleToUnits = 1.06f,
                            centerOrigin = Position(0f, 1.08f, 0f),
                        ) {
                            if (expressionEnabled) {
                                GuardianAvatarExpressionDriver(
                                    modelNode = parentNode as ModelNode,
                                    mood = mood,
                                    lastUserMessage = lastUserMessage,
                                    logger = logger,
                                )
                            } else {
                                DisabledGuardianAvatarExpressionDriverLogger(logger)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "3D model not found",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (companionRole == CompanionRole.ANGEL) {
                                "Put angel_egna.glb under assets/models/"
                            } else {
                                "Put a GLB under assets/models/"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun DisabledGuardianAvatarExpressionDriverLogger(logger: AppLogger) {
    LaunchedEffect(Unit) {
        logger.log(
            AppLogSeverity.WARN,
            "GuardianAvatar3DStage",
            "3D expression driver disabled",
            details = structuredDetails(
                "reason" to "Avoid native crash while reading Filament morph targets",
            ),
        )
    }
}

@Composable
private fun GuardianAvatarExpressionDriver(
    modelNode: ModelNode,
    mood: GuardianAvatarMood,
    lastUserMessage: String?,
    logger: AppLogger,
) {
    val faceNode = remember(modelNode) {
        modelNode.renderableNodes.firstOrNull { it.morphTargetNames.isNotEmpty() }
    }
    val renderableNodeSummary = remember(modelNode) {
        modelNode.renderableNodes.mapIndexed { index, node ->
            buildString {
                append("node[$index]")
                append(" morphCount=")
                append(node.morphTargetNames.size)
                append(" names=")
                append(node.morphTargetNames.joinToString(separator = ","))
            }
        }
    }
    val targetIndices = remember(faceNode) {
        faceNode?.morphTargetNames
            ?.mapIndexedNotNull { index, name ->
                normalizeMorphName(name)?.let { normalized -> normalized to index }
            }
            ?.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
            .orEmpty()
    }
    val isGreetingLike = remember(lastUserMessage) {
        lastUserMessage.isGreetingLike()
    }
    val driverStartDetails = remember(faceNode, targetIndices, renderableNodeSummary, isGreetingLike) {
        buildString {
            appendLine("renderableNodeCount=${renderableNodeSummary.size}")
            renderableNodeSummary.forEach { appendLine(it) }
            appendLine("faceNodePresent=${faceNode != null}")
            appendLine("faceMorphTargetCount=${faceNode?.morphTargetNames?.size ?: 0}")
            appendLine("normalizedMorphTargetCount=${targetIndices.size}")
            appendLine("isGreetingLike=$isGreetingLike")
        }
    }

    LaunchedEffect(driverStartDetails) {
        logger.log(
            AppLogSeverity.INFO,
            "GuardianAvatar3DStage",
            "3D expression driver prepared",
            details = driverStartDetails,
        )
    }

    LaunchedEffect(faceNode, mood, isGreetingLike) {
        if (faceNode == null || targetIndices.isEmpty()) {
            logger.log(
                AppLogSeverity.WARN,
                "GuardianAvatar3DStage",
                "3D expression driver unavailable",
                details = structuredDetails(
                    "faceNodePresent" to (faceNode != null),
                    "renderableNodeCount" to modelNode.renderableNodes.size,
                    "faceMorphTargetCount" to (faceNode?.morphTargetNames?.size ?: 0),
                    "normalizedMorphTargetCount" to targetIndices.size,
                    "mood" to mood.name,
                    "isGreetingLike" to isGreetingLike,
                ),
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
                    "GuardianAvatar3DStage",
                    "3D expression apply failed",
                    details = error.toStructuredLogDetails(
                        "mood" to mood.name,
                        "isGreetingLike" to isGreetingLike,
                        "faceMorphTargetCount" to faceNode.morphTargetNames.size,
                        "normalizedMorphTargetCount" to targetIndices.size,
                        "activeMorphWeights" to summarizeMorphWeights(weights),
                    ),
                )
            }
        }

        fun applyNeutral() {
            applyWeights(
                mapOf(
                    "neutral" to 0.25f,
                    "joy" to 0.20f,
                    "blink" to 0.0f,
                    "blink_l" to 0.0f,
                    "blink_r" to 0.0f,
                    "a" to 0.0f,
                    "i" to 0.0f,
                    "u" to 0.0f,
                    "e" to 0.0f,
                    "o" to 0.0f,
                    "angry" to 0.0f,
                    "sorrow" to 0.0f,
                    "surprised" to 0.0f,
                    "fun" to 0.0f,
                ),
            )
        }

        applyNeutral()
        logger.log(
            AppLogSeverity.DEBUG,
            "GuardianAvatar3DStage",
            "3D expression driver started",
            details = structuredDetails(
                "mood" to mood.name,
                "isGreetingLike" to isGreetingLike,
                "faceMorphTargetCount" to faceNode.morphTargetNames.size,
                "normalizedMorphTargetCount" to targetIndices.size,
                "targetMorphNames" to targetIndices.keys.joinToString(separator = ","),
            ),
        )
        try {
            var lastLoggedSignature: String? = null
            var lastLoggedAtMs = 0L
            while (true) {
                val now = SystemClock.uptimeMillis().toFloat() / 1000f
                val blinkPulse = blinkPulse(now)
                val mouthPulse = mouthPulse(now, mood)
                val expression = buildExpressionWeights(
                    mood = mood,
                    greetingLike = isGreetingLike,
                    blinkPulse = blinkPulse,
                    mouthPulse = mouthPulse,
                )
                applyWeights(expression)
                val signature = buildExpressionStateSignature(
                    mood = mood,
                    greetingLike = isGreetingLike,
                )
                val nowMs = SystemClock.uptimeMillis()
                if (signature != lastLoggedSignature || nowMs - lastLoggedAtMs >= 1_500L) {
                    logger.log(
                        AppLogSeverity.DEBUG,
                        "GuardianAvatar3DStage",
                        "3D expression frame applied",
                        details = buildString {
                            appendLine("mood=${mood.name}")
                            appendLine("isGreetingLike=$isGreetingLike")
                            appendLine("blinkPulse=${formatFloat(blinkPulse)}")
                            appendLine("mouthPulse=${formatFloat(mouthPulse)}")
                            appendLine("signature=$signature")
                            appendLine("nonZeroWeights=${summarizeMorphWeights(expression)}")
                        },
                    )
                    lastLoggedSignature = signature
                    lastLoggedAtMs = nowMs
                }
                kotlinx.coroutines.delay(33L)
            }
        } finally {
            applyNeutral()
            logger.log(
                AppLogSeverity.INFO,
                "GuardianAvatar3DStage",
                "3D expression driver stopped",
                details = structuredDetails(
                    "mood" to mood.name,
                    "isGreetingLike" to isGreetingLike,
                ),
            )
        }
    }
}

private fun buildExpressionWeights(
    mood: GuardianAvatarMood,
    greetingLike: Boolean,
    blinkPulse: Float,
    mouthPulse: Float,
): Map<String, Float> {
    val bigSmile = if (greetingLike && mood == GuardianAvatarMood.LISTENING) 0.92f else when (mood) {
        GuardianAvatarMood.SPEAKING -> 0.86f
        GuardianAvatarMood.LISTENING -> 0.72f
        GuardianAvatarMood.THINKING -> 0.34f
        GuardianAvatarMood.ERROR -> 0.12f
        else -> 0.38f
    }
    val sorrow = when (mood) {
        GuardianAvatarMood.ERROR -> 0.78f
        GuardianAvatarMood.THINKING -> 0.24f
        else -> 0.05f
    }
    val angry = when (mood) {
        GuardianAvatarMood.ERROR -> 0.22f
        else -> 0.0f
    }
    val surprised = when (mood) {
        GuardianAvatarMood.ERROR -> 0.0f
        GuardianAvatarMood.THINKING -> 0.14f
        else -> 0.0f
    }

    val (a, i, u, e, o) = mouthShapeWeights(mouthPulse, mood)

    return buildMap {
        put("neutral", if (mood == GuardianAvatarMood.ERROR) 0.0f else 0.18f)
        put("joy", bigSmile)
        put("sorrow", sorrow)
        put("angry", angry)
        put("surprised", surprised)
        put("fun", if (mood == GuardianAvatarMood.SPEAKING || greetingLike) 0.48f else 0.12f)
        put("blink", blinkPulse)
        put("blink_l", blinkPulse * 0.85f)
        put("blink_r", blinkPulse * 0.85f)
        put("a", a)
        put("i", i)
        put("u", u)
        put("e", e)
        put("o", o)
    }
}

private fun mouthShapeWeights(
    pulse: Float,
    mood: GuardianAvatarMood,
): Quintuple {
    return when (mood) {
        GuardianAvatarMood.SPEAKING -> {
            val speak = pulse.coerceIn(0f, 1f)
            Quintuple(
                a = 0.22f + speak * 0.68f,
                i = 0.08f + (1f - speak) * 0.22f,
                u = 0.05f,
                e = 0.12f + speak * 0.24f,
                o = 0.10f + speak * 0.38f,
            )
        }
        GuardianAvatarMood.LISTENING -> {
            val speak = pulse.coerceIn(0f, 1f)
            Quintuple(
                a = 0.08f + speak * 0.24f,
                i = 0.06f,
                u = 0.04f,
                e = 0.10f + speak * 0.10f,
                o = 0.08f + speak * 0.12f,
            )
        }
        GuardianAvatarMood.THINKING -> {
            val think = pulse.coerceIn(0f, 1f)
            Quintuple(
                a = 0.04f,
                i = 0.16f,
                u = 0.10f,
                e = 0.18f + think * 0.16f,
                o = 0.05f,
            )
        }
        GuardianAvatarMood.ERROR -> Quintuple(
            a = 0.06f,
            i = 0.04f,
            u = 0.02f,
            e = 0.08f,
            o = 0.16f,
        )
        else -> {
            val speak = pulse.coerceIn(0f, 1f)
            Quintuple(
                a = 0.10f + speak * 0.28f,
                i = 0.05f,
                u = 0.03f,
                e = 0.08f,
                o = 0.08f + speak * 0.10f,
            )
        }
    }
}

private fun summarizeMorphWeights(weights: Map<String, Float>): String {
    return weights
        .filterValues { it > 0.01f }
        .entries
        .sortedByDescending { it.value }
        .joinToString(separator = ", ") { (name, value) ->
            "$name=${formatFloat(value)}"
        }
        .ifBlank { "none" }
}

private fun buildExpressionStateSignature(
    mood: GuardianAvatarMood,
    greetingLike: Boolean,
): String {
    return "${mood.name}|${if (greetingLike) "G" else "N"}"
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

private fun mouthPulse(nowSeconds: Float, mood: GuardianAvatarMood): Float {
    val base = nowSeconds * when (mood) {
        GuardianAvatarMood.SPEAKING -> 9.0f
        GuardianAvatarMood.LISTENING -> 4.0f
        GuardianAvatarMood.THINKING -> 2.5f
        GuardianAvatarMood.ERROR -> 1.6f
        else -> 3.2f
    }
    return ((sin(base.toDouble()) + 1.0) / 2.0).toFloat()
}

private fun normalizeMorphName(name: String): String? {
    val normalized = name.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "neutral" -> "neutral"
        "a" -> "a"
        "i" -> "i"
        "u" -> "u"
        "e" -> "e"
        "o" -> "o"
        "blink" -> "blink"
        "blink_l" -> "blink_l"
        "blink_r" -> "blink_r"
        "angry" -> "angry"
        "fun" -> "fun"
        "joy" -> "joy"
        "sorrow" -> "sorrow"
        "surprised" -> "surprised"
        else -> null
    } ?: when (normalized) {
        "fcl_all_neutral", "fcl_mth_neutral" -> "neutral"
        "fcl_all_angry" -> "angry"
        "fcl_all_fun" -> "fun"
        "fcl_all_joy" -> "joy"
        "fcl_all_sorrow" -> "sorrow"
        "fcl_all_surprised" -> "surprised"
        "fcl_eye_close" -> "blink"
        "fcl_eye_close_l" -> "blink_l"
        "fcl_eye_close_r" -> "blink_r"
        "fcl_mth_a" -> "a"
        "fcl_mth_i" -> "i"
        "fcl_mth_u" -> "u"
        "fcl_mth_e" -> "e"
        "fcl_mth_o" -> "o"
        else -> null
    }
}

private data class Quintuple(
    val a: Float,
    val i: Float,
    val u: Float,
    val e: Float,
    val o: Float,
)

private fun String?.isGreetingLike(): Boolean {
    val normalized = this.orEmpty().trim()
    if (normalized.isBlank()) return false
    return listOf(
        "久しぶり",
        "こんにちは",
        "こんばんは",
        "おはよう",
        "やあ",
        "はじめまして",
        "初めまして",
        "元気",
    ).any { normalized.contains(it) }
}

private fun Context.assetExists(assetPath: String): Boolean {
    return runCatching {
        assets.open(assetPath).use { }
        true
    }.getOrDefault(false)
}

private fun distanceBetween(home: Position, target: Position): Float {
    val dx = home.x - target.x
    val dy = home.y - target.y
    val dz = home.z - target.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun Position.toDebugString(): String {
    return "(${formatFloat(x)}, ${formatFloat(y)}, ${formatFloat(z)})"
}

private fun Float.toFormattedString(): String {
    return String.format(Locale.US, "%.2f", this)
}

private fun formatFloat(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}
