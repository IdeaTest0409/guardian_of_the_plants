package com.example.smartphonapptest001.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartphonapptest001.data.SettingsRepository
import com.example.smartphonapptest001.data.knowledge.PlantKnowledgeRepository
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.toStructuredLogDetails
import com.example.smartphonapptest001.data.logging.structuredDetails
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AttachmentKind
import com.example.smartphonapptest001.data.model.AutoSmallTalkInterval
import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.data.model.PlantImageSelectionMode
import com.example.smartphonapptest001.data.model.PlantProfile
import com.example.smartphonapptest001.data.model.PlantProfileRepository
import com.example.smartphonapptest001.data.model.buildPlantGuardianPrompt
import com.example.smartphonapptest001.data.model.buildPlantImageDiagnosticPrompt
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.data.repository.ChatRepository
import com.example.smartphonapptest001.ui.model.GuardianAvatarMood
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messageDraft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val pendingAttachment: Attachment? = null,
    val activeProviderSummary: String = "Cloud LM Studio",
    val activePlantName: String = PlantProfileRepositoryFallbacks.defaultPlantName,
    val activeCompanionRole: CompanionRole = CompanionRole.default(),
    val activeCompanionRoleName: String = "\u5929\u4f7f",
    val activePersonalityName: String = "\u3084\u3055\u3057\u3044",
    val plantCareNotes: String = "",
    val plantPresentHint: String = "",
    val selectedPlantImage: Attachment? = null,
    val plantImageSelectionMode: PlantImageSelectionMode = PlantImageSelectionMode.default(),
    val avatarMoodOverride: GuardianAvatarMood? = null,
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val plantProfileRepository: PlantProfileRepository,
    private val plantKnowledgeRepository: PlantKnowledgeRepository,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeSettings: AppSettings = AppSettings()
    private var activePlantProfile: PlantProfile = plantProfileRepository.profileFor(AppSettings().plantSpecies)
    private var sendJob: Job? = null
    private var autoSmallTalkJob: Job? = null
    private var lastUserActivityElapsedMs: Long = SystemClock.elapsedRealtime()
    private var autoSmallTalkTopicIndex: Int = 0
    private val recentAutoSmallTalkThemes = ArrayDeque<String>()
    private var cachedPlantImageDiagnostic: CachedPlantImageDiagnostic? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                activeSettings = settings
                activePlantProfile = plantProfileRepository.profileFor(settings.plantSpecies)
                logger.log(
                    AppLogSeverity.INFO,
                    TAG,
                    "Settings applied to chat",
                    details = buildString {
                        appendLine("provider=${settings.providerType}")
                        appendLine("baseUrl=${settings.activeEndpointConfig.baseUrl}")
                        appendLine("model=${settings.activeEndpointConfig.model}")
                        appendLine("streamResponses=${settings.streamResponses}")
                        appendLine("plant=${activePlantProfile.displayName}")
                        appendLine("companionRole=${settings.companionRole.label}")
                        appendLine("personality=${settings.guardianAngelDisplayName}")
                        appendLine("autoSmallTalkInterval=${settings.autoSmallTalkInterval.name}")
                    },
                )
                _uiState.update {
                    it.copy(
                        activeProviderSummary = buildProviderSummary(settings),
                        activePlantName = activePlantProfile.displayName,
                        activeCompanionRole = settings.companionRole,
                        activeCompanionRoleName = settings.companionRole.label,
                        activePersonalityName = settings.guardianAngelDisplayName,
                        plantCareNotes = activePlantProfile.summary,
                        plantPresentHint = activePlantProfile.noPlantPresentMessage,
                    )
                }
                restartAutoSmallTalkTimer(settings.autoSmallTalkInterval)
            }
        }
    }

    fun onSettingsChanged(settings: AppSettings) {
        activeSettings = settings
        activePlantProfile = plantProfileRepository.profileFor(settings.plantSpecies)
        _uiState.update {
            it.copy(
                activeProviderSummary = buildProviderSummary(settings),
                activePlantName = activePlantProfile.displayName,
                activeCompanionRole = settings.companionRole,
                activeCompanionRoleName = settings.companionRole.label,
                activePersonalityName = settings.guardianAngelDisplayName,
                plantCareNotes = activePlantProfile.summary,
                plantPresentHint = activePlantProfile.noPlantPresentMessage,
            )
        }
    }

    fun onPlantImageChanged(attachment: Attachment?, mode: PlantImageSelectionMode) {
        _uiState.update {
            it.copy(
                selectedPlantImage = attachment,
                plantImageSelectionMode = mode,
            )
        }
    }

    fun onMessageChange(value: String) {
        markUserActivity()
        _uiState.update { it.copy(messageDraft = value) }
    }

    fun onAttachmentCaptured(attachment: Attachment) {
        markUserActivity()
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Attachment captured",
            details = buildString {
                appendLine("displayName=${attachment.displayName}")
                appendLine("mimeType=${attachment.mimeType}")
                appendLine("kind=${attachment.kind}")
                appendLine("hasDataUrl=${!attachment.dataUrl.isNullOrBlank()}")
            },
        )
        _uiState.update { it.copy(pendingAttachment = attachment, errorMessage = null) }
    }

    fun clearPendingAttachment() {
        markUserActivity()
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun resetConversation() {
        markUserActivity()
        logger.log(AppLogSeverity.INFO, TAG, "Conversation reset")
        _uiState.update {
            it.copy(
                messages = emptyList(),
                errorMessage = null,
                avatarMoodOverride = null,
            )
        }
    }

    fun sendMessage() {
        markUserActivity()
        sendMessageInternal(triggeredByAutoSmallTalk = false)
    }

    private fun sendAutoSmallTalkMessage() {
        val prompt = nextAutoSmallTalkPrompt()
        rememberAutoSmallTalkTheme(prompt.themeId)
        _uiState.update { it.copy(messageDraft = prompt.content) }
        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "Auto small talk queued",
            details = structuredDetails(
                "interval" to activeSettings.autoSmallTalkInterval.name,
                "themeId" to prompt.themeId,
                "themeLabel" to prompt.themeLabel,
                "promptChars" to prompt.content.length,
                "messageCount" to uiState.value.messages.size,
                "recentThemes" to recentAutoSmallTalkThemes.joinToString(),
            ),
        )
        sendMessageInternal(triggeredByAutoSmallTalk = true)
        lastUserActivityElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun sendMessageInternal(triggeredByAutoSmallTalk: Boolean) {
        val draft = uiState.value.messageDraft.trim()
        val attachment = uiState.value.pendingAttachment
        val plantImage = uiState.value.selectedPlantImage
        if (draft.isBlank() && attachment == null && plantImage == null) return
        if (uiState.value.isSending) return

        sendJob?.cancel()
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = draft,
            attachments = buildList {
                if (plantImage != null) add(plantImage)
                if (attachment != null) add(attachment)
            },
        )
        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = "", isPending = true)
        val conversationBeforeSend = uiState.value.messages

        _uiState.update {
            it.copy(
                messageDraft = "",
                pendingAttachment = null,
                messages = if (triggeredByAutoSmallTalk) {
                    it.messages + assistantMessage
                } else {
                    it.messages + userMessage + assistantMessage
                },
                isSending = true,
                errorMessage = null,
                avatarMoodOverride = GuardianAvatarMood.LISTENING,
            )
        }
        logger.log(
            AppLogSeverity.DEBUG,
            TAG,
            "Avatar mood primed for listening",
            details = structuredDetails(
                "draftChars" to draft.length,
                "attachmentCount" to userMessage.attachments.size,
                "plantImagePresent" to (plantImage != null),
                "selectedPlantImageMode" to uiState.value.plantImageSelectionMode.name,
            ),
        )

        logger.log(
            AppLogSeverity.INFO,
            TAG,
            "User message queued",
            details = buildString {
                appendLine("provider=${activeSettings.providerType}")
                appendLine("baseUrl=${activeSettings.activeEndpointConfig.baseUrl}")
                appendLine("model=${activeSettings.activeEndpointConfig.model}")
                appendLine("messageChars=${draft.length}")
                appendLine("message=$draft")
                appendLine("triggeredByAutoSmallTalk=$triggeredByAutoSmallTalk")
                appendLine("hasAttachment=${attachment != null}")
                appendLine("attachmentKind=${attachment?.kind}")
                appendLine("attachmentMime=${attachment?.mimeType}")
                appendLine("plantImageSelected=${plantImage != null}")
                appendLine("plantImageMode=${uiState.value.plantImageSelectionMode.name}")
                appendLine("providerSummary=${buildProviderSummary(activeSettings)}")
                appendLine("companionRole=${activeSettings.companionRole.label}")
                appendLine("plant=${activePlantProfile.displayName}")
                appendLine("selectedPlantImage=${plantImage?.displayName.orEmpty()}")
                appendLine("selectedPlantImageMode=${uiState.value.plantImageSelectionMode.name}")
                appendLine("pendingAttachment=${attachment?.displayName.orEmpty()}")
            },
        )

        sendJob = viewModelScope.launch {
            runCatching {
                val imageDiagnostic = runPlantImageDiagnostic(userMessage)
                val knowledgeResult = plantKnowledgeRepository.relevantKnowledge(
                    query = draft,
                    plantProfile = activePlantProfile,
                )
                logger.log(
                    AppLogSeverity.DEBUG,
                    TAG,
                    "Plant knowledge context prepared",
                    details = structuredDetails(
                        "chars" to knowledgeResult.text.length,
                        "source" to knowledgeResult.source,
                        "chunkCount" to knowledgeResult.chunkCount,
                        "empty" to knowledgeResult.isEmpty,
                    ),
                )
                val conversation = buildList {
                    add(
                        ChatMessage(
                            role = ChatRole.SYSTEM,
                            content = buildPlantGuardianPrompt(
                                plantProfile = activePlantProfile,
                                companionRole = activeSettings.companionRole,
                                personality = activeSettings.guardianAngelPersonality,
                                thinkingEnabled = activeSettings.thinkingEnabled,
                                knowledgeContext = knowledgeResult.text,
                            ),
                        ),
                    )
                    addAll(conversationBeforeSend)
                    add(userMessage)
                }
                logger.log(
                    AppLogSeverity.DEBUG,
                    TAG,
                    "Conversation prepared",
                    details = buildString {
                        appendLine("conversationSize=${conversation.size}")
                        appendLine("provider=${activeSettings.providerType}")
                        appendLine("model=${activeSettings.activeEndpointConfig.model}")
                        appendLine("plant=${activePlantProfile.displayName}")
                        appendLine("streamResponses=${activeSettings.streamResponses}")
                        appendLine("messageAttachments=${userMessage.attachments.size}")
                        appendLine("plantImagePresent=${plantImage != null}")
                        appendLine("triggeredByAutoSmallTalk=$triggeredByAutoSmallTalk")
                    },
                )
                val reply = if (imageDiagnostic?.guardianTargetPresent == false) {
                    logger.log(
                        AppLogSeverity.INFO,
                        TAG,
                        "Skipping guardian reply AI because no target plant was detected",
                        details = structuredDetails(
                            "imageStatus" to imageDiagnostic.imageStatus,
                            "targetName" to imageDiagnostic.targetName,
                            "confidence" to imageDiagnostic.confidence,
                            "reason" to imageDiagnostic.reason,
                        ),
                    )
                    activePlantProfile.noPlantPresentMessage
                } else if (activeSettings.streamResponses) {
                    val builder = StringBuilder()
                    repository.stream(conversation, activeSettings).collectLatest { partial ->
                        builder.clear()
                        builder.append(partial)
                        logger.log(
                            AppLogSeverity.DEBUG,
                            TAG,
                            "Streaming reply update",
                            details = "partialChars=${partial.length}",
                        )
                        updateAssistantMessage(builder.toString(), pending = true)
                    }
                    builder.toString()
                } else {
                    repository.complete(conversation, activeSettings)
                }
                logger.log(
                    AppLogSeverity.INFO,
                    TAG,
                    "Assistant reply received",
                    details = "replyChars=${reply.length}\nreply=$reply",
                )
                val displayReply = normalizeAssistantReply(reply)
                updateAssistantMessage(displayReply, pending = false)
                val assistantMood = classifyAssistantMood(displayReply)
                logger.log(
                    AppLogSeverity.DEBUG,
                    TAG,
                    "Assistant mood classified",
                    details = structuredDetails(
                        "replyChars" to displayReply.length,
                        "replyPreview" to displayReply.take(120),
                        "questionLike" to isQuestionLike(draft),
                        "assistantMood" to assistantMood.name,
                        "rawReplyChars" to reply.length,
                    ),
                )
                _uiState.update {
                    it.copy(avatarMoodOverride = assistantMood)
                }
                delay(1_500)
                _uiState.update { state ->
                    if (state.avatarMoodOverride != null && state.isSending.not()) {
                        logger.log(
                            AppLogSeverity.DEBUG,
                            TAG,
                            "Avatar mood override cleared",
                            details = structuredDetails(
                                "previousMood" to state.avatarMoodOverride?.name,
                                "isSending" to state.isSending,
                            ),
                        )
                        state.copy(avatarMoodOverride = null)
                    } else {
                        state
                    }
                }
            }.onFailure { error ->
                logger.log(
                    AppLogSeverity.ERROR,
                    TAG,
                    "Chat send failed",
                    details = error.toStructuredLogDetails(
                        "provider" to activeSettings.providerType.name,
                        "baseUrl" to activeSettings.activeEndpointConfig.baseUrl,
                        "model" to activeSettings.activeEndpointConfig.model,
                        "messageCount" to uiState.value.messages.size,
                        "draftChars" to draft.length,
                        "attachmentCount" to userMessage.attachments.size,
                        "plant" to activePlantProfile.displayName,
                    ),
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.message ?: "Unknown error",
                        messages = it.messages.filterNotLastPendingAssistant(),
                        avatarMoodOverride = GuardianAvatarMood.ERROR,
                    )
                }
                logger.log(
                    AppLogSeverity.DEBUG,
                    TAG,
                    "Avatar mood forced to error",
                    details = structuredDetails(
                        "messageCount" to uiState.value.messages.size,
                        "draftChars" to draft.length,
                        "attachmentCount" to userMessage.attachments.size,
                    ),
                )
                delay(1_500)
                _uiState.update { state ->
                    if (state.avatarMoodOverride == GuardianAvatarMood.ERROR) {
                        state.copy(avatarMoodOverride = null)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun updateAssistantMessage(content: String, pending: Boolean) {
        _uiState.update { state ->
            val updatedMessages = state.messages.updateLastAssistant(content, pending)
            state.copy(
                messages = updatedMessages,
                isSending = pending,
            )
        }
    }

    private suspend fun runPlantImageDiagnostic(userMessage: ChatMessage): PlantImageDiagnosticResult? {
        val imageAttachments = userMessage.attachments.filter { it.kind == AttachmentKind.IMAGE }
        if (imageAttachments.isEmpty()) {
            return null
        }

        val diagnosticConversation = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = buildString {
                    append(
                        buildPlantImageDiagnosticPrompt(
                            plantProfile = activePlantProfile,
                            companionRole = activeSettings.companionRole,
                            personality = activeSettings.guardianAngelPersonality,
                        ),
                    )
                    if (activeSettings.providerType == ProviderType.SERVER) {
                        appendLine()
                        appendLine()
                        appendLine("Server strict response rules:")
                        appendLine("- You must output one minified JSON object only.")
                        appendLine("- The first character must be { and the last character must be }.")
                        appendLine("- Do not output markdown, prose, analysis, or code fences.")
                        appendLine("- Use image_status as exactly one of present, absent, unclear.")
                        appendLine("- Use guardian_target_present as exactly true, false, or null.")
                        appendLine("- If a houseplant is visible and resembles the target plant, choose true.")
                    }
                },
            ),
            userMessage,
        )
        val cacheKey = buildPlantImageDiagnosticCacheKey(
            imageAttachments = imageAttachments,
            mode = uiState.value.plantImageSelectionMode,
        )
        val nowMs = SystemClock.elapsedRealtime()
        val cached = cachedPlantImageDiagnostic
        val needsFreshDiagnostic = shouldRunFreshPlantImageDiagnostic(
            messageText = userMessage.content,
            cacheKey = cacheKey,
            cached = cached,
            nowMs = nowMs,
        )
        if (!needsFreshDiagnostic && cached != null) {
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Hidden image diagnostic cache used",
                details = structuredDetails(
                    "cacheKey" to cacheKey,
                    "ageMs" to (nowMs - cached.timestampElapsedMs),
                    "guardianTargetPresent" to cached.result.guardianTargetPresent,
                    "imageStatus" to cached.result.imageStatus,
                    "questionNeedsImageDiagnostic" to userMessage.content.needsImageDiagnostic(),
                ),
            )
            return cached.result
        }

        logger.log(
            AppLogSeverity.DEBUG,
            TAG,
            "Hidden image diagnostic request started",
            details = buildString {
                appendLine("provider=${activeSettings.providerType}")
                appendLine("baseUrl=${activeSettings.activeEndpointConfig.baseUrl}")
                appendLine("model=${activeSettings.activeEndpointConfig.model}")
                appendLine("imageAttachmentCount=${imageAttachments.size}")
                appendLine("imageNames=${imageAttachments.joinToString { it.displayName }}")
                appendLine("imageMimeTypes=${imageAttachments.joinToString { it.mimeType }}")
                appendLine("imageDataUrlChars=${imageAttachments.joinToString { (it.dataUrl?.length ?: 0).toString() }}")
                appendLine("imageHasDataUrl=${imageAttachments.joinToString { (!it.dataUrl.isNullOrBlank()).toString() }}")
                appendLine("diagnosticConversationSize=${diagnosticConversation.size}")
                appendLine("systemPromptChars=${diagnosticConversation.first().content.length}")
                appendLine("cacheKey=$cacheKey")
                appendLine("questionNeedsImageDiagnostic=${userMessage.content.needsImageDiagnostic()}")
                appendLine("strictJsonMode=${activeSettings.providerType == ProviderType.SERVER}")
            },
        )

        val diagnosticSettings = activeSettings.copy(
            streamResponses = false,
            maxOutputTokens = 96,
            topK = 1,
            topP = 0.1,
            temperature = 0.0,
            thinkingEnabled = false,
        )
        val diagnosticReply = runCatching {
            repository.complete(diagnosticConversation, diagnosticSettings)
        }.getOrElse { error ->
            logger.log(
                AppLogSeverity.WARN,
                TAG,
                "Hidden image diagnostic failed",
                details = error.toStructuredLogDetails(
                    "provider" to activeSettings.providerType.name,
                    "baseUrl" to activeSettings.activeEndpointConfig.baseUrl,
                    "model" to activeSettings.activeEndpointConfig.model,
                    "imageAttachmentCount" to imageAttachments.size,
                ),
            )
            return null
        }
        val result = parsePlantImageDiagnosticReply(
            reply = diagnosticReply,
            expectedPlantName = activePlantProfile.displayName,
        )
        logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Hidden image diagnostic result",
                details = buildString {
                    appendLine("provider=${activeSettings.providerType}")
                    appendLine("model=${activeSettings.activeEndpointConfig.model}")
                    appendLine("imageAttachmentCount=${imageAttachments.size}")
                    appendLine("imageMimeTypes=${imageAttachments.joinToString { it.mimeType }}")
                    appendLine("imageDataUrlChars=${imageAttachments.joinToString { (it.dataUrl?.length ?: 0).toString() }}")
                    appendLine("jsonExtracted=${result.jsonExtracted}")
                    appendLine("guardianTargetPresentSource=${result.guardianTargetPresentSource}")
                    appendLine("imageStatus=${result.imageStatus}")
                    appendLine("guardianTargetPresent=${result.guardianTargetPresent}")
                    appendLine("targetName=${result.targetName}")
                appendLine("confidence=${result.confidence}")
                appendLine("reason=${result.reason}")
                appendLine("diagnosticReplyChars=${diagnosticReply.length}")
                    appendLine("diagnosticJson=${result.normalizedJsonForLog()}")
                    appendLine("diagnosticRawReply=${diagnosticReply.take(1200)}")
                },
        )
        cachedPlantImageDiagnostic = CachedPlantImageDiagnostic(
            cacheKey = cacheKey,
            timestampElapsedMs = nowMs,
            result = result,
        )
        return result
    }

    private fun markUserActivity() {
        lastUserActivityElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun restartAutoSmallTalkTimer(interval: AutoSmallTalkInterval) {
        autoSmallTalkJob?.cancel()
        val intervalMillis = interval.intervalMillis ?: return
        autoSmallTalkJob = viewModelScope.launch {
            logger.log(
                AppLogSeverity.INFO,
                TAG,
                "Auto small talk timer started",
                details = structuredDetails(
                    "interval" to interval.name,
                    "intervalMillis" to intervalMillis,
                ),
            )
            while (true) {
                delay(intervalMillis)
                val state = uiState.value
                val idleMs = SystemClock.elapsedRealtime() - lastUserActivityElapsedMs
                val stillEnabled = activeSettings.autoSmallTalkInterval == interval
                val canSend = stillEnabled &&
                    idleMs >= intervalMillis &&
                    !state.isSending &&
                    state.messageDraft.isBlank()
                logger.log(
                    AppLogSeverity.DEBUG,
                    TAG,
                    "Auto small talk timer tick",
                    details = structuredDetails(
                        "interval" to interval.name,
                        "idleMs" to idleMs,
                        "isSending" to state.isSending,
                        "draftBlank" to state.messageDraft.isBlank(),
                        "canSend" to canSend,
                    ),
                )
                if (canSend) {
                    sendAutoSmallTalkMessage()
                }
            }
        }
    }

    private fun nextAutoSmallTalkPrompt(): AutoSmallTalkPrompt {
        val topics = listOf(
            AutoSmallTalkTopic(
                themeId = "friend_guardian_pothos",
                label = "友達の守護天使とポトス",
                instruction = "友達の守護天使が守るポトスの話を軽く出し、今の植物との違いを一つだけ話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "friend_guardian_cactus",
                label = "友達の守護天使とサボテン",
                instruction = "友達の守護天使が守るサボテンの話から、乾燥に強い植物同士の豆知識へ自然につなげてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "friend_guardian_fern",
                label = "友達の守護天使とシダ",
                instruction = "友達の守護天使が守るシダの話を出し、湿度を好む植物との違いを短く話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "leaf_pattern_fact",
                label = "葉模様の豆知識",
                instruction = "葉の縞模様や斑の見え方について、観察したくなる豆知識を一つ話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "water_storage_fact",
                label = "水分をためる葉",
                instruction = "サンスベリアの厚い葉が水分をためることを、やさしい豆知識として話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "winter_rest_fact",
                label = "冬の休眠",
                instruction = "冬は成長がゆっくりになり水を控えめにする、という季節の豆知識を話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "light_rotation_tip",
                label = "光と向き",
                instruction = "光の方向で葉の見え方が変わることや、ときどき鉢の向きを変える話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "root_health_tip",
                label = "根と土",
                instruction = "見えない根を守るために、乾きやすい土と水はけが大切という話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "new_leaf_watch",
                label = "新芽の観察",
                instruction = "新しい葉や小さな変化を探す、観察の楽しさを話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "gentle_touch",
                label = "触れ方",
                instruction = "葉に触れるならそっと支える程度がよい、という短い話題にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "air_cleaning_lore",
                label = "空気と植物の印象",
                instruction = "植物が部屋の空気を落ち着いた印象にしてくれる、というやわらかい雑談にしてください。",
            ),
            AutoSmallTalkTopic(
                themeId = "pot_friend_story",
                label = "鉢まわりの友達",
                instruction = "友達の守護天使たちが、それぞれの鉢の見回りをしているという世界観で話題を広げてください。",
            ),
        )
        val availableTopics = topics.filterNot { it.themeId in recentAutoSmallTalkThemes }
            .ifEmpty { topics }
        val topic = availableTopics[autoSmallTalkTopicIndex % availableTopics.size]
        autoSmallTalkTopicIndex += 1
        val recentReplies = recentAssistantReplySummary()
        val recentThemeText = recentAutoSmallTalkThemes.joinToString()
            .ifBlank { "none" }
        return AutoSmallTalkPrompt(
            themeId = topic.themeId,
            themeLabel = topic.label,
            content = buildString {
                appendLine("自動雑談です。ユーザーにはこの指示文を見せず、守護者の自然な一言だけ返してください。")
                appendLine("80文字以内の日本語で、前回と似た話題をなるべく避けてください。")
                appendLine("今回の話題: ${topic.label}")
                appendLine(topic.instruction)
                appendLine("同じ内容の言い換え、水やりだけの繰り返し、同じ植物状態の反復は避けてください。")
                appendLine("必要なら、他の植物は友達の守護天使が守っているという形で話題を広げてください。")
                appendLine("その植物の豆知識を一つだけ自然に混ぜてください。")
                appendLine("直近の自動雑談テーマ: $recentThemeText")
                if (recentReplies.isNotBlank()) {
                    appendLine("直近の返信要約:")
                    appendLine(recentReplies)
                }
            },
        )
    }

    private fun rememberAutoSmallTalkTheme(themeId: String) {
        recentAutoSmallTalkThemes.remove(themeId)
        recentAutoSmallTalkThemes.addLast(themeId)
        while (recentAutoSmallTalkThemes.size > RECENT_AUTO_SMALL_TALK_THEME_LIMIT) {
            recentAutoSmallTalkThemes.removeFirst()
        }
    }

    private fun recentAssistantReplySummary(): String {
        return uiState.value.messages
            .asReversed()
            .filter { it.role == ChatRole.ASSISTANT && !it.isPending && it.content.isNotBlank() }
            .take(RECENT_ASSISTANT_REPLY_LIMIT)
            .joinToString(separator = "\n") { message ->
                "- ${message.content.trim().replace(Regex("\\s+"), " ").take(70)}"
            }
    }

    private fun buildProviderSummary(settings: AppSettings): String {
        return when (settings.providerType) {
            ProviderType.LOCAL -> buildString {
                append("Local / on-device")
                append(" | ")
                append(activePlantProfile.displayName)
                append(" | ")
                append(settings.companionRole.label)
                append(" | ")
                append(settings.guardianAngelDisplayName)
                append(" | ")
                append(settings.localModel.ifBlank { "unset model" })
            }

            ProviderType.CLOUD -> buildString {
                append("Cloud / LM Studio")
                append(" | ")
                append(activePlantProfile.displayName)
                append(" | ")
                append(settings.companionRole.label)
                append(" | ")
                append(settings.guardianAngelDisplayName)
                append(" | ")
                append(settings.cloudBaseUrl.ifBlank { "unset" })
                append(" | ")
                append(settings.cloudModel.ifBlank { "unset model" })
            }

            ProviderType.SERVER -> buildString {
                append("Server / VPS")
                append(" | ")
                append(activePlantProfile.displayName)
                append(" | ")
                append(settings.companionRole.label)
                append(" | ")
                append(settings.guardianAngelDisplayName)
            }
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}

private fun classifyAssistantMood(reply: String): GuardianAvatarMood {
    val normalized = reply.lowercase()
    return when {
        normalized.contains("申し訳") ||
            normalized.contains("エラー") ||
            normalized.contains("失敗") -> GuardianAvatarMood.ERROR

        normalized.contains("注意") ||
            normalized.contains("気をつけ") ||
            normalized.contains("控え") ||
            normalized.contains("根腐れ") ||
            normalized.contains("乾") ||
            normalized.contains("水やり") -> GuardianAvatarMood.THINKING

        normalized.contains("大丈夫") ||
            normalized.contains("元気") ||
            normalized.contains("きれい") ||
            normalized.contains("いい") ||
            normalized.contains("かわいい") -> GuardianAvatarMood.SPEAKING

        else -> GuardianAvatarMood.IDLE
    }
}

private data class PlantImageDiagnosticResult(
    val imageStatus: String,
    val guardianTargetPresent: Boolean?,
    val guardianTargetPresentSource: String,
    val targetName: String,
    val reason: String,
    val confidence: String,
    val jsonExtracted: Boolean,
    val rawReply: String,
)

private data class CachedPlantImageDiagnostic(
    val cacheKey: String,
    val timestampElapsedMs: Long,
    val result: PlantImageDiagnosticResult,
)

private data class AutoSmallTalkTopic(
    val themeId: String,
    val label: String,
    val instruction: String,
)

private data class AutoSmallTalkPrompt(
    val themeId: String,
    val themeLabel: String,
    val content: String,
)

private const val PLANT_IMAGE_DIAGNOSTIC_CACHE_MS = 60_000L
private const val RECENT_AUTO_SMALL_TALK_THEME_LIMIT = 5
private const val RECENT_ASSISTANT_REPLY_LIMIT = 4

private fun parsePlantImageDiagnosticReply(
    reply: String,
    expectedPlantName: String,
): PlantImageDiagnosticResult {
    val extractedJson = reply.extractFirstJsonObject()
    val compact = extractedJson ?: reply.trim()
    val jsonGuardianTargetPresent = Regex(""""guardian_target_present"\s*:\s*(true|false|null)""")
        .find(compact)
        ?.groupValues
        ?.getOrNull(1)
    val inferredGuardianTargetPresent = inferGuardianTargetPresence(compact, expectedPlantName)
    val guardianTargetPresent = when (jsonGuardianTargetPresent) {
        "true" -> true
        "false" -> false
        "null" -> null
        else -> inferredGuardianTargetPresent
    }
    val guardianTargetPresentSource = when {
        jsonGuardianTargetPresent == "true" || jsonGuardianTargetPresent == "false" || jsonGuardianTargetPresent == "null" -> "json"
        inferredGuardianTargetPresent != null -> "inference"
        else -> "null"
    }
    val imageStatus = Regex(""""image_status"\s*:\s*"([^"]*)"""")
        .find(compact)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
        .ifBlank {
            when (guardianTargetPresent) {
                true -> "present"
                false -> "absent"
                null -> "unclear"
            }
        }
    val targetName = Regex(""""target_name"\s*:\s*"([^"]*)"""")
        .find(compact)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val reason = Regex(""""reason"\s*:\s*"([^"]*)"""")
        .find(compact)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val confidence = Regex(""""confidence"\s*:\s*"([^"]*)"""")
        .find(compact)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
        .ifBlank { "unknown" }
    return PlantImageDiagnosticResult(
        imageStatus = imageStatus,
        guardianTargetPresent = guardianTargetPresent,
        guardianTargetPresentSource = guardianTargetPresentSource,
        targetName = targetName,
        reason = reason.ifBlank { compact.take(160) },
        confidence = confidence,
        jsonExtracted = extractedJson != null,
        rawReply = reply,
    )
}

private fun shouldRunFreshPlantImageDiagnostic(
    messageText: String,
    cacheKey: String,
    cached: CachedPlantImageDiagnostic?,
    nowMs: Long,
): Boolean {
    if (cached == null) return true
    if (cached.cacheKey != cacheKey) return true
    if (nowMs - cached.timestampElapsedMs >= PLANT_IMAGE_DIAGNOSTIC_CACHE_MS) return true
    return messageText.needsImageDiagnostic()
}

private fun buildPlantImageDiagnosticCacheKey(
    imageAttachments: List<Attachment>,
    mode: PlantImageSelectionMode,
): String {
    if (mode == PlantImageSelectionMode.REALTIME_CAPTURE) {
        return "REALTIME_CAPTURE"
    }
    return imageAttachments.joinToString(separator = "|") { attachment ->
        val dataUrlSize = attachment.dataUrl?.length ?: 0
        val uriValue = attachment.uri?.toString().orEmpty()
        "${attachment.displayName}:${attachment.mimeType}:$dataUrlSize:$uriValue"
    }
}

private fun String.needsImageDiagnostic(): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return true
    return listOf(
        "葉",
        "葉っぱ",
        "色",
        "水",
        "季節",
        "大丈夫",
        "病気",
        "虫",
        "土",
        "乾",
        "枯",
        "根",
        "植え替え",
        "日光",
        "元気",
        "見て",
        "状態",
        "何と言って",
    ).any { normalized.contains(it) }
}

private fun PlantImageDiagnosticResult.normalizedJsonForLog(): String {
    return buildString {
        append('{')
        append("\"image_status\":\"")
        append(imageStatus.jsonEscaped())
        append("\",\"guardian_target_present\":")
        append(guardianTargetPresent ?: "null")
        append(",\"target_name\":\"")
        append(targetName.jsonEscaped())
        append("\",\"reason\":\"")
        append(reason.take(80).jsonEscaped())
        append("\",\"confidence\":\"")
        append(confidence.jsonEscaped())
        append("\"}")
    }
}

private fun String.extractFirstJsonObject(): String? {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end > start) substring(start, end + 1).trim() else null
}

private fun String.jsonEscaped(): String {
    return replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}

private fun inferGuardianTargetPresence(
    reply: String,
    expectedPlantName: String,
): Boolean? {
    val normalized = reply.lowercase()
    val expected = expectedPlantName.lowercase()
    return when {
        normalized.contains("植物は写ってい") ||
            normalized.contains("植物が写っていません") ||
            normalized.contains("植物ではありません") ||
            normalized.contains("対象植物") && normalized.contains("ありません") -> false
        expected.isNotBlank() && normalized.contains(expected) -> true
        normalized.contains("サンスベリア") -> true
        normalized.contains("植物が写って") ||
            normalized.contains("植物が見え") ||
            normalized.contains("植物が確認") ||
            normalized.contains("植物のよう") -> true
        else -> null
    }
}


private fun normalizeAssistantReply(reply: String): String {
    val normalized = reply
        .trim()
        .replace(Regex("\\s+"), " ")
    val receiptPrefixPattern = Regex(
        pattern = """^(画像|写真)(を)?(受け取りました|確認しました|拝見しました|届きました|ありがとうございます|ありがとうござました|ありがとうございますね|届いています|届きましたね)[。．.!！?？、,\s]*""",
    )
    val greetingPrefixPattern = Regex(
        pattern = """^(画像(?:について)?(?:は)?|写真(?:について)?(?:は)?)[。．.!！?？、,\s]*""",
    )
    val stripped = normalized
        .replace(receiptPrefixPattern, "")
        .replace(greetingPrefixPattern, "")
        .trim()
    return if (stripped.isBlank()) normalized else stripped
}

private fun isQuestionLike(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) return false
    return normalized.contains('？') ||
        normalized.contains('?') ||
        normalized.startsWith("何") ||
        normalized.startsWith("ど") ||
        normalized.startsWith("なぜ") ||
        normalized.startsWith("どう") ||
        normalized.contains("ですか") ||
        normalized.contains("ますか") ||
        normalized.contains("いいですか") ||
        normalized.contains("できますか") ||
        normalized.contains("でしょうか")
}

private object PlantProfileRepositoryFallbacks {
    const val defaultPlantName = "\u30b5\u30f3\u30b9\u30d9\u30ea\u30a2"
}

private fun List<ChatMessage>.updateLastAssistant(content: String, pending: Boolean): List<ChatMessage> {
    val index = indexOfLast { it.role == ChatRole.ASSISTANT }
    if (index < 0) return this
    return toMutableList().also { messages ->
        messages[index] = messages[index].copy(content = content, isPending = pending)
    }
}

private fun List<ChatMessage>.filterNotLastPendingAssistant(): List<ChatMessage> {
    val index = indexOfLast { it.role == ChatRole.ASSISTANT && it.isPending }
    if (index < 0) return this
    return filterIndexed { currentIndex, _ -> currentIndex != index }
}


