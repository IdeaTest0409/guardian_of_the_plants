package com.example.smartphonapptest001.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.provider.OpenableColumns
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.structuredDetails
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.model.AiImageQuality
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AttachmentKind
import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.example.smartphonapptest001.data.model.LocalExecutionBackend
import com.example.smartphonapptest001.data.model.LocalModelPreset
import com.example.smartphonapptest001.data.model.PlantProfileRepository
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.Locale

enum class LocalModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

data class LocalModelDownloadState(
    val status: LocalModelDownloadStatus = LocalModelDownloadStatus.NOT_DOWNLOADED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
)

data class LocalModelRuntimeState(
    val activeModelId: String? = null,
    val isStarting: Boolean = false,
    val isRunning: Boolean = false,
    val message: String? = null,
)

data class LocalModelServiceState(
    val downloads: Map<String, LocalModelDownloadState> = emptyMap(),
    val runtime: LocalModelRuntimeState = LocalModelRuntimeState(),
)

private data class ImportedModelSourceInfo(
    val displayName: String,
    val mimeType: String?,
    val byteCount: Long?,
)

class LocalModelService(
    context: Context,
    private val plantProfileRepository: PlantProfileRepository,
    private val logger: AppLogger,
) {
    private val appContext = context.applicationContext
    private val sharedStorageDir: File = resolveSharedStorageDir()
    private val legacyStorageDir: File = appContext.getExternalFilesDir("local-models")
        ?.apply { mkdirs() }
        ?: File(appContext.filesDir, "local-models").apply { mkdirs() }

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val inferenceDispatcher: CoroutineDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, "local-llm-inference").apply {
                isDaemon = true
            }
        }
        .asCoroutineDispatcher()
    private val engineMutex = Mutex()

    private val _state = MutableStateFlow(
        LocalModelServiceState(
            downloads = LocalModelPreset.entries.associate { preset ->
                preset.modelId to currentDownloadState(preset)
            },
        ),
    )
    val state = _state.asStateFlow()

    private var engine: Engine? = null

    fun modelFile(preset: LocalModelPreset): File = File(sharedStorageDir, preset.fileName)

    fun isDownloaded(preset: LocalModelPreset): Boolean = modelFile(preset).exists() || legacyModelFile(preset).exists()

    suspend fun downloadModel(preset: LocalModelPreset): File = withContext(ioDispatcher) {
        val sharedDestination = modelFile(preset)
        if (sharedDestination.exists() && sharedDestination.length() > 0) {
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Model already downloaded in shared storage",
                details = buildString {
                    appendLine("modelId=${preset.modelId}")
                    appendLine("path=${sharedDestination.absolutePath}")
                    appendLine("sizeBytes=${sharedDestination.length()}")
                },
            )
            updateDownloadState(preset.modelId) {
                copy(
                    status = LocalModelDownloadStatus.DOWNLOADED,
                    bytesDownloaded = sharedDestination.length(),
                    totalBytes = sharedDestination.length(),
                    message = "Downloaded",
                )
            }
            return@withContext sharedDestination
        }

        val legacyFile = legacyModelFile(preset)
        if (legacyFile.exists() && legacyFile.length() > 0) {
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Migrating model from legacy storage to shared storage",
                details = buildString {
                    appendLine("modelId=${preset.modelId}")
                    appendLine("legacyPath=${legacyFile.absolutePath}")
                    appendLine("sharedPath=${sharedDestination.absolutePath}")
                    appendLine("sizeBytes=${legacyFile.length()}")
                },
            )
            sharedDestination.parentFile?.mkdirs()
            legacyFile.copyTo(sharedDestination, overwrite = true)
            updateDownloadState(preset.modelId) {
                copy(
                    status = LocalModelDownloadStatus.DOWNLOADED,
                    bytesDownloaded = sharedDestination.length(),
                    totalBytes = sharedDestination.length(),
                    message = "Downloaded",
                )
            }
            return@withContext sharedDestination
        }

        updateDownloadState(preset.modelId) {
            copy(
                status = LocalModelDownloadStatus.DOWNLOADING,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                message = "Downloading",
            )
        }

        val destination = sharedDestination
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        tempFile.parentFile?.mkdirs()
        tempFile.delete()

        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Model download started",
            details = structuredDetails(
                "modelId" to preset.modelId,
                "sourceUrl" to preset.downloadUrl,
                "destination" to destination.absolutePath,
                "storageRoot" to sharedStorageDir.absolutePath,
            ),
        )

        try {
            val connection = URL(preset.downloadUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 60_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "PlantGuardian/1.0")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.use { input ->
                        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                            reader.readText()
                        }
                    }
                }
                    .getOrNull()
                throw IllegalStateException(
                    buildString {
                        append("Model download failed: HTTP ")
                        append(responseCode)
                        if (!errorBody.isNullOrBlank()) {
                            append(" - ")
                            append(errorBody.take(300))
                        }
                    },
                )
            }

            val totalBytes = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        updateDownloadState(preset.modelId) {
                            copy(
                                status = LocalModelDownloadStatus.DOWNLOADING,
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes,
                                message = if (totalBytes > 0) {
                                    "Downloading ${downloaded * 100 / totalBytes}%"
                                } else {
                                    "Downloading"
                                },
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (destination.exists()) {
                destination.delete()
            }
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }

            validateModelFileForExecution(preset, destination)
            updateDownloadState(preset.modelId) {
                copy(
                    status = LocalModelDownloadStatus.DOWNLOADED,
                    bytesDownloaded = destination.length(),
                    totalBytes = destination.length(),
                    message = "Downloaded",
                )
            }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Model download completed",
                details = buildString {
                    appendLine("modelId=${preset.modelId}")
                    appendLine("path=${destination.absolutePath}")
                    appendLine("sizeBytes=${destination.length()}")
                },
            )
            destination
        } catch (error: Throwable) {
            tempFile.delete()
            updateDownloadState(preset.modelId) {
                copy(
                    status = LocalModelDownloadStatus.FAILED,
                    message = error.message ?: "Download failed",
                )
            }
            logger.log(
                AppLogSeverity.ERROR,
                TAG,
                "Model download failed",
                details = error.toStructuredLogDetails(
                    "phase" to "download",
                    "modelId" to preset.modelId,
                    "destination" to destination.absolutePath,
                ),
            )
            throw error
        }
    }

    suspend fun importModelFile(
        preset: LocalModelPreset,
        sourceUri: Uri,
    ): File = withContext(ioDispatcher) {
        val sourceInfo = readImportedModelSourceInfo(sourceUri)
        validateImportedModelSource(preset, sourceInfo)
        val destination = modelFile(preset)
        val tempFile = File(destination.parentFile, "${destination.name}.importing")
        tempFile.parentFile?.mkdirs()
        tempFile.delete()

        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Model file import started",
            details = structuredDetails(
                "modelId" to preset.modelId,
                "sourceUri" to sourceUri.toString(),
                "sourceDisplayName" to sourceInfo.displayName,
                "sourceMimeType" to sourceInfo.mimeType,
                "sourceByteCount" to sourceInfo.byteCount,
                "destination" to destination.absolutePath,
                "storageRoot" to sharedStorageDir.absolutePath,
            ),
        )

        try {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    copyStream(input, output) { copiedBytes, totalBytes ->
                        updateDownloadState(preset.modelId) {
                            copy(
                                status = LocalModelDownloadStatus.DOWNLOADING,
                                bytesDownloaded = copiedBytes,
                                totalBytes = totalBytes,
                                message = if (totalBytes > 0) {
                                    "Importing ${copiedBytes * 100 / totalBytes}%"
                                } else {
                                    "Importing"
                                },
                            )
                        }
                    }
                }
            } ?: throw IllegalStateException("Could not open selected model file.")

            if (destination.exists()) {
                destination.delete()
            }
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }

            validateModelFileForExecution(preset, destination)
            updateDownloadState(preset.modelId) {
                copy(
                    status = LocalModelDownloadStatus.DOWNLOADED,
                    bytesDownloaded = destination.length(),
                    totalBytes = destination.length(),
                    message = "Imported",
                )
            }
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Model file import completed",
                details = structuredDetails(
                    "modelId" to preset.modelId,
                    "path" to destination.absolutePath,
                    "sizeBytes" to destination.length(),
                    "sha256" to destination.sha256(),
                ),
            )
            destination
        } catch (error: Throwable) {
            tempFile.delete()
            updateDownloadState(preset.modelId) {
                copy(
                    status = LocalModelDownloadStatus.FAILED,
                    message = error.message ?: "Import failed",
                )
            }
            logger.log(
                AppLogSeverity.ERROR,
                TAG,
                "Model file import failed",
                details = error.toStructuredLogDetails(
                    "phase" to "import",
                    "modelId" to preset.modelId,
                    "sourceUri" to sourceUri.toString(),
                    "destination" to destination.absolutePath,
                ),
            )
            throw error
        }
    }

    suspend fun startModel(
        preset: LocalModelPreset,
        backend: LocalExecutionBackend,
        settings: AppSettings,
    ): Unit = withContext(inferenceDispatcher) {
        engineMutex.withLock {
            val readyModelFile = ensureModelReady(preset)
            startModelInternal(preset, backend, settings, readyModelFile)
        }
    }

    private suspend fun startModelInternal(
        preset: LocalModelPreset,
        backend: LocalExecutionBackend,
        settings: AppSettings,
        modelFile: File,
    ) {
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            throw IllegalStateException("Model is not downloaded: ${preset.label}")
        }

        updateRuntimeState {
            copy(
                activeModelId = preset.modelId,
                isStarting = true,
                isRunning = false,
                message = "Starting",
            )
        }

        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Starting local model",
            details = buildString {
                appendLine("modelId=${preset.modelId}")
                appendLine("path=${modelFile.absolutePath}")
                appendLine("sizeBytes=${modelFile.length()}")
                appendLine("executionTarget=on-device")
                appendLine("storageRoot=${sharedStorageDir.absolutePath}")
            },
        )

        try {
            closeEngine()

            val backendCandidates = backend.toLiteRtBackends(appContext)
            var lastError: Throwable? = null
            for ((index, candidateBackend) in backendCandidates.withIndex()) {
                logger.log(
                    AppLogSeverity.INFO,
                    TAG,
                    "Initializing LiteRT-LM engine attempt",
                    details = buildString {
                        appendLine("modelId=${preset.modelId}")
                        appendLine("attempt=${index + 1}/${backendCandidates.size}")
                        appendLine("backend=${backend.name}")
                        appendLine("candidateBackend=${candidateBackend.javaClass.name}")
                        appendLine("modelPath=${modelFile.absolutePath}")
                        appendLine("cacheDir=${appContext.cacheDir.absolutePath}")
                    },
                )

                try {
                    val engineConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = candidateBackend,
                        visionBackend = candidateBackend,
                        audioBackend = candidateBackend,
                        maxNumTokens = settings.maxOutputTokens,
                        cacheDir = appContext.cacheDir.absolutePath,
                    )
                    logger.log(
                        AppLogSeverity.DEBUG,
                        TAG,
                        "LiteRT-LM engine config prepared",
                        details = buildString {
                            appendLine("modelId=${preset.modelId}")
                            appendLine("backend=${backend.name}")
                            appendLine("modelPath=${modelFile.absolutePath}")
                            appendLine("cacheDir=${appContext.cacheDir.absolutePath}")
                        },
                    )
                    val createdEngine = Engine(engineConfig)
                    createdEngine.initialize()
                    engine = createdEngine

                    updateRuntimeState {
                        copy(
                            activeModelId = preset.modelId,
                            isStarting = false,
                            isRunning = true,
                            message = "Running",
                        )
                    }
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Local model started",
                        details = buildString {
                            appendLine("modelId=${preset.modelId}")
                            appendLine("backend=${backend.name}")
                            appendLine("engineBackend=${candidateBackend.javaClass.name}")
                        },
                    )
                    return
                } catch (error: Throwable) {
                    lastError = error
                    logger.log(
                        AppLogSeverity.WARN,
                        TAG,
                        "LiteRT-LM engine initialization attempt failed",
                        details = error.toStructuredLogDetails(
                            "phase" to "engine_initialization",
                            "modelId" to preset.modelId,
                            "backend" to backend.name,
                            "candidateBackend" to candidateBackend.javaClass.name,
                            "modelPath" to modelFile.absolutePath,
                        ),
                    )
                    closeEngine()
                }
            }
            throw lastError ?: IllegalStateException("LiteRT-LM engine initialization failed without a captured error.")
        } catch (error: Throwable) {
            closeEngine()
            updateRuntimeState {
                copy(
                    activeModelId = preset.modelId,
                    isStarting = false,
                    isRunning = false,
                    message = error.message ?: "Start failed",
                )
            }
            logger.log(
                AppLogSeverity.ERROR,
                TAG,
                "Local model start failed",
                details = error.toStructuredLogDetails(
                    "phase" to "model_start",
                    "diagnosis" to diagnoseStartError(error),
                    "hint" to startFailureHint(preset.modelId, error),
                    "modelId" to preset.modelId,
                    "modelPath" to modelFile.absolutePath,
                    "modelSizeBytes" to modelFile.length(),
                    "backend" to backend.name,
                ),
            )
            throw error
        }
    }

    suspend fun stopModel() = withContext(inferenceDispatcher) {
        engineMutex.withLock {
            logger.log(AppLogSeverity.INFO, TAG, "Stopping local model")
            closeEngine()
            _state.update {
                it.copy(
                    runtime = LocalModelRuntimeState(
                        activeModelId = null,
                        isStarting = false,
                        isRunning = false,
                        message = "Stopped",
                    ),
                )
            }
        }
    }

    suspend fun complete(
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): String = withContext(inferenceDispatcher) {
        engineMutex.withLock {
        val activePreset = LocalModelPreset.fromModelId(settings.localModel)
        val runtimeState = _state.value.runtime
        val runtimeMismatch = runtimeState.activeModelId != null && runtimeState.activeModelId != activePreset.modelId
        val needsAutoStart = engine == null || runtimeMismatch
        if (needsAutoStart) {
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Local model not ready during completion; attempting auto-start",
                details = structuredDetails(
                    "modelId" to activePreset.modelId,
                    "backend" to settings.localExecutionBackend.name,
                    "runtimeActive" to runtimeState.activeModelId,
                    "runtimeRunning" to runtimeState.isRunning,
                    "enginePresent" to (engine != null),
                ),
            )
            runCatching {
                val readyModelFile = ensureModelReady(activePreset)
                startModelInternal(activePreset, settings.localExecutionBackend, settings, readyModelFile)
            }.recoverCatching { firstError ->
                if (settings.localExecutionBackend != LocalExecutionBackend.DEFAULT) {
                    logger.log(
                        AppLogSeverity.WARN,
                        TAG,
                        "Auto-start failed with selected backend, retrying with default backend",
                        details = firstError.toStructuredLogDetails(
                            "phase" to "auto_start_retry",
                            "selectedBackend" to settings.localExecutionBackend.name,
                            "fallbackBackend" to LocalExecutionBackend.DEFAULT.name,
                            "modelId" to activePreset.modelId,
                        ),
                    )
                    val readyModelFile = ensureModelReady(activePreset)
                    startModelInternal(activePreset, LocalExecutionBackend.DEFAULT, settings, readyModelFile)
                } else {
                    throw firstError
                }
                }.getOrThrow()
        }

        val currentEngine = engine ?: throw IllegalStateException(
            "Local model is not started. Download the model, then press Start in Settings.",
        )

        val refreshedRuntimeState = _state.value.runtime
        if (refreshedRuntimeState.activeModelId != null && refreshedRuntimeState.activeModelId != activePreset.modelId) {
            throw IllegalStateException(
                "Selected model (${activePreset.label}) is not the one currently running (${refreshedRuntimeState.activeModelId}). Start the selected model first.",
            )
        }

        val prompt = buildLocalConversationPrompt(messages, settings, plantProfileRepository)
        val userMessage = messages.lastOrNull { it.role == ChatRole.USER }
        val imageContents = userMessage
            ?.attachments
            .orEmpty()
            .asSequence()
            .filter { it.kind == AttachmentKind.IMAGE }
            .mapNotNull { it.toLiteRtImageContent(settings.aiImageQuality) }
            .toList()
        val attachmentCount = messages.sumOf { it.attachments.size }
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Local inference started",
            details = buildString {
                appendLine("modelId=${activePreset.modelId}")
                appendLine("backend=${settings.localExecutionBackend.name}")
                appendLine("runtimeRunning=${refreshedRuntimeState.isRunning}")
                appendLine("runtimeStarting=${refreshedRuntimeState.isStarting}")
                appendLine("runtimeActive=${refreshedRuntimeState.activeModelId}")
                appendLine("messageCount=${messages.size}")
                appendLine("attachmentCount=${attachmentCount}")
                appendLine("imageContentCount=${imageContents.size}")
                appendLine("promptChars=${prompt.length}")
                appendLine("promptPreview=${prompt.take(240).replace("\n", "\\n")}")
            },
        )

        logger.log(
            AppLogSeverity.DEBUG,
            TAG,
            "Calling LiteRT-LM inference engine",
            details = buildString {
                appendLine("modelId=${activePreset.modelId}")
                appendLine("promptChars=${prompt.length}")
                appendLine("runtimeActive=${refreshedRuntimeState.activeModelId}")
            },
        )

        val rawResponse = runCatching {
            val conversationConfig = ConversationConfig().copy(
                samplerConfig = SamplerConfig(
                    settings.topK,
                    settings.topP,
                    settings.temperature,
                    0,
                ),
            )
            currentEngine.createConversation(conversationConfig).use { conversation ->
                val contents = buildLiteRtContents(prompt, imageContents)
                val response = conversation.sendMessage(contents)
                response.toString()
            }
        }.recoverCatching { imageError ->
            if (imageContents.isNotEmpty()) {
                logger.log(
                    AppLogSeverity.WARN,
                    TAG,
                    "LiteRT-LM multimodal send failed; retrying as text only",
                    details = imageError.toStructuredLogDetails(
                        "phase" to "multimodal_retry_text_only",
                        "modelId" to activePreset.modelId,
                        "backend" to settings.localExecutionBackend.name,
                    "imageContentCount" to imageContents.size,
                ),
                )
                val conversationConfig = ConversationConfig().copy(
                    samplerConfig = SamplerConfig(
                        settings.topK,
                        settings.topP,
                        settings.temperature,
                        0,
                    ),
                )
                currentEngine.createConversation(conversationConfig).use { conversation ->
                    conversation.sendMessage(Contents.of(Content.Text(prompt))).toString()
                }
            } else {
                throw imageError
            }
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.ERROR,
                TAG,
                "LiteRT-LM inference failed",
                details = error.toStructuredLogDetails(
                    "phase" to "inference",
                    "modelId" to activePreset.modelId,
                    "backend" to settings.localExecutionBackend.name,
                    "runtimeRunning" to refreshedRuntimeState.isRunning,
                    "runtimeStarting" to refreshedRuntimeState.isStarting,
                    "runtimeActive" to refreshedRuntimeState.activeModelId,
                    "promptChars" to prompt.length,
                    "promptPreview" to prompt.take(400).replace("\n", "\\n"),
                ),
            )
        }.getOrThrow()
        val response = normalizeLocalResponse(rawResponse)
        if (response != rawResponse) {
            logger.log(
                AppLogSeverity.DEBUG,
                TAG,
                "Local response normalized",
                details = buildString {
                    appendLine("rawChars=${rawResponse.length}")
                    appendLine("normalizedChars=${response.length}")
                },
            )
        }
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "LiteRT-LM inference completed",
            details = buildString {
                appendLine("responseChars=${response.length}")
                appendLine("modelId=${activePreset.modelId}")
                appendLine("backend=${settings.localExecutionBackend.name}")
                appendLine("runtimeActive=${_state.value.runtime.activeModelId}")
            },
        )
        response.trim()
        }
    }

    fun stream(
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): Flow<String> = flow {
        emit(complete(messages, settings))
    }

    private fun currentDownloadState(preset: LocalModelPreset): LocalModelDownloadState {
        val file = when {
            modelFile(preset).exists() -> modelFile(preset)
            legacyModelFile(preset).exists() -> legacyModelFile(preset)
            else -> modelFile(preset)
        }
        return if (file.exists() && file.length() > 0) {
            LocalModelDownloadState(
                status = LocalModelDownloadStatus.DOWNLOADED,
                bytesDownloaded = file.length(),
                totalBytes = file.length(),
                message = "Downloaded",
            )
        } else {
            LocalModelDownloadState(
                status = LocalModelDownloadStatus.NOT_DOWNLOADED,
                message = "Not downloaded",
            )
        }
    }

    private suspend fun ensureModelReady(preset: LocalModelPreset): File = withContext(ioDispatcher) {
        val sharedFile = modelFile(preset)
        if (sharedFile.exists() && sharedFile.length() > 0L) {
            validateModelFileForExecution(preset, sharedFile)
            return@withContext sharedFile
        }

        val legacyFile = legacyModelFile(preset)
        if (legacyFile.exists() && legacyFile.length() > 0L) {
            sharedFile.parentFile?.mkdirs()
            legacyFile.copyTo(sharedFile, overwrite = true)
            validateModelFileForExecution(preset, sharedFile)
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Model migrated into shared storage for execution",
                details = buildString {
                    appendLine("modelId=${preset.modelId}")
                    appendLine("legacyPath=${legacyFile.absolutePath}")
                    appendLine("sharedPath=${sharedFile.absolutePath}")
                },
            )
            return@withContext sharedFile
        }

        downloadModel(preset)
    }

    private fun validateImportedModelSource(
        preset: LocalModelPreset,
        sourceInfo: ImportedModelSourceInfo,
    ) {
        val normalizedName = sourceInfo.displayName.lowercase(Locale.ROOT)
        val mimeType = sourceInfo.mimeType.orEmpty().lowercase(Locale.ROOT)
        val sizeBytes = sourceInfo.byteCount
        val minimumSizeBytes = minimumAcceptedModelSizeBytes(preset)

        if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
            throw IllegalArgumentException(
                "Selected file looks like media, not a LiteRT-LM model bundle.",
            )
        }
        if (mimeType.startsWith("text/") || mimeType == "application/pdf") {
            throw IllegalArgumentException(
                "Selected file looks like a document, not a LiteRT-LM model bundle.",
            )
        }
        if (sizeBytes != null && sizeBytes < minimumSizeBytes) {
            throw IllegalArgumentException(
                "Selected model file is too small for ${preset.label}. Expected at least ${minimumSizeBytes / (1024 * 1024)} MB.",
            )
        }
        if (!normalizedName.endsWith(".litertlm")) {
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "Model file does not use the expected LiteRT-LM extension",
                details = structuredDetails(
                    "modelId" to preset.modelId,
                    "displayName" to sourceInfo.displayName,
                    "mimeType" to sourceInfo.mimeType,
                    "byteCount" to sizeBytes,
                ),
            )
        }
    }

    private fun validateModelFileForExecution(
        preset: LocalModelPreset,
        file: File,
    ) {
        if (!file.exists() || file.length() <= 0L) {
            throw IllegalStateException("Model file is missing or empty: ${preset.label}")
        }
        val minimumSizeBytes = minimumAcceptedModelSizeBytes(preset)
        if (file.length() < minimumSizeBytes) {
            throw IllegalStateException(
                "Model file is unexpectedly small for ${preset.label}: ${file.length()} bytes",
            )
        }
        logger.log(
            AppLogSeverity.DEBUG,
            TAG,
            "Model file validated for execution",
            details = structuredDetails(
                "modelId" to preset.modelId,
                "path" to file.absolutePath,
                "sizeBytes" to file.length(),
                "sha256" to file.sha256(),
                "minimumAcceptedBytes" to minimumSizeBytes,
            ),
        )
    }

    private fun readImportedModelSourceInfo(sourceUri: Uri): ImportedModelSourceInfo {
        var displayName: String? = null
        var mimeType: String? = null
        var byteCount: Long? = null
        appContext.contentResolver.query(
            sourceUri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    byteCount = cursor.getLong(sizeIndex)
                }
            }
        }
        if (displayName.isNullOrBlank()) {
            displayName = sourceUri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "selected-model-file"
        }
        mimeType = appContext.contentResolver.getType(sourceUri)
        return ImportedModelSourceInfo(
            displayName = displayName.orEmpty(),
            mimeType = mimeType,
            byteCount = byteCount,
        )
    }

    private fun minimumAcceptedModelSizeBytes(preset: LocalModelPreset): Long {
        return when (preset) {
            LocalModelPreset.GEMMA_4_E4B_IT -> 512L * 1024L * 1024L
            else -> 128L * 1024L * 1024L
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        this.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun legacyModelFile(preset: LocalModelPreset): File = File(legacyStorageDir, preset.fileName)

    private fun resolveSharedStorageDir(): File {
        val candidate = when {
            hasAllFilesAccess() -> File(Environment.getExternalStorageDirectory(), "AImodel")
            appContext.externalMediaDirs.firstOrNull() != null -> File(appContext.externalMediaDirs.first(), "AImodel")
            else -> File(appContext.filesDir, "AImodel")
        }
        candidate.mkdirs()
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Resolved model storage directory",
            details = buildString {
                appendLine("path=${candidate.absolutePath}")
                appendLine("allFilesAccess=${hasAllFilesAccess()}")
            },
        )
        return candidate
    }

    private fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun updateDownloadState(
        modelId: String,
        block: LocalModelDownloadState.() -> LocalModelDownloadState,
    ) {
        _state.update { current ->
            val next = current.downloads.toMutableMap()
            val currentEntry = next[modelId] ?: LocalModelDownloadState()
            next[modelId] = currentEntry.block()
            current.copy(downloads = next)
        }
    }

    private fun updateRuntimeState(
        block: LocalModelRuntimeState.() -> LocalModelRuntimeState,
    ) {
        _state.update { current ->
            current.copy(runtime = current.runtime.block())
        }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
    }

    private fun buildLiteRtContents(
        prompt: String,
        imageContents: List<Content>,
    ): Contents {
        val parts = buildList {
            imageContents.forEach { add(it) }
            add(Content.Text(prompt))
        }
        return Contents.of(*parts.toTypedArray())
    }

    private fun Attachment.toLiteRtImageContent(aiImageQuality: AiImageQuality): Content? {
        if (kind != AttachmentKind.IMAGE) return null

        val sourceBytes = when {
            !dataUrl.isNullOrBlank() -> decodeDataUrlBytes(dataUrl)
            uri != null -> readBytesFromUri(uri)
            else -> null
        } ?: run {
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "Image attachment could not be converted for LiteRT-LM input",
                details = structuredDetails(
                    "displayName" to displayName,
                    "mimeType" to mimeType,
                    "hasDataUrl" to (!dataUrl.isNullOrBlank()),
                    "hasUri" to (uri != null),
                ),
            )
            return null
        }
        val imageBytes = sourceBytes.resizeForAi(aiImageQuality)

        logger.log(
            AppLogSeverity.DEBUG,
            TAG,
            "Image attachment converted for LiteRT-LM input",
            details = structuredDetails(
                "displayName" to displayName,
                "mimeType" to mimeType,
                "aiImageQuality" to aiImageQuality.name,
                "sourceByteCount" to sourceBytes.size,
                "byteCount" to imageBytes.size,
            ),
        )
        return Content.ImageBytes(imageBytes)
    }

    private fun ByteArray.resizeForAi(aiImageQuality: AiImageQuality): ByteArray {
        if (aiImageQuality == AiImageQuality.ORIGINAL) return this
        return runCatching {
            val source = BitmapFactory.decodeByteArray(this, 0, size) ?: return this
            val scaled = source.scaleDown(aiImageQuality.maxDimension)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, aiImageQuality.jpegQuality, output)
            if (scaled !== source) {
                scaled.recycle()
            }
            source.recycle()
            output.toByteArray()
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "AI image resize failed; using original image bytes",
                details = error.toStructuredLogDetails(
                    "aiImageQuality" to aiImageQuality.name,
                    "sourceByteCount" to size,
                ),
            )
        }.getOrDefault(this)
    }

    private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
        val longest = maxOf(width, height).toFloat()
        val scale = if (longest <= maxDimension) 1f else maxDimension / longest
        if (scale >= 1f) return this
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
    }

    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "Failed to read image attachment from uri",
                details = error.toStructuredLogDetails(
                    "phase" to "image_read",
                    "uri" to uri.toString(),
                ),
            )
        }.getOrNull()
    }

    private fun decodeDataUrlBytes(dataUrl: String): ByteArray? {
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex < 0) return null
        val encoded = dataUrl.substring(commaIndex + 1)
        return runCatching {
            Base64.decode(encoded, Base64.DEFAULT)
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "Failed to decode image dataUrl",
                details = error.toStructuredLogDetails(
                    "phase" to "data_url_decode",
                ),
            )
        }.getOrNull()
    }

    private fun copyStream(
        input: InputStream,
        output: FileOutputStream,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(copied, copied)
        }
        output.flush()
    }

    private fun normalizeLocalResponse(rawResponse: String): String {
        return rawResponse
            .replace("<turn|>", "")
            .replace("<eos>", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
    }

    private fun diagnoseStartError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("SentencePiece tokenizer is not found", ignoreCase = true) -> {
                "Tokenizer metadata is missing or incompatible for LiteRT-LM."
            }
            message.contains("Unknown model type", ignoreCase = true) -> {
                "The model bundle contains an unsupported model type for LiteRT-LM."
            }
            else -> "LiteRT-LM model initialization failed."
        }
    }

    private fun startFailureHint(modelId: String, error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            modelId.equals("gemma-4-e2b", ignoreCase = true) &&
                message.contains("SentencePiece tokenizer is not found", ignoreCase = true) -> {
                "Use gemma-4-E2B-it for on-device execution; the selected bundle is missing tokenizer metadata."
            }
            else -> "Check the downloaded bundle and LiteRT-LM runtime compatibility."
        }
    }

    private companion object {
        const val TAG = "LocalModelService"
    }
}
