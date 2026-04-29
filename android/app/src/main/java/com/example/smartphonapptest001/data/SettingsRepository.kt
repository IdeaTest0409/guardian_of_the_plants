package com.example.smartphonapptest001.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.smartphonapptest001.data.model.AiImageQuality
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.AutoSmallTalkInterval
import com.example.smartphonapptest001.data.model.CompanionRole
import com.example.smartphonapptest001.data.model.AvatarPresentationMode
import com.example.smartphonapptest001.data.model.GuardianAngelPersonality
import com.example.smartphonapptest001.data.model.LocalExecutionBackend
import com.example.smartphonapptest001.data.model.ProviderType
import com.example.smartphonapptest001.data.model.PlantSpecies
import com.example.smartphonapptest001.data.model.TtsVoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun save(settings: AppSettings)
    suspend fun setAvatarExpressionEnabled(enabled: Boolean)
    suspend fun setMrAvatarMotionEnabled(enabled: Boolean)
}

class DataStoreSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("smartphone_app_settings") },
    )

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                providerType = parseProviderType(prefs[Keys.providerType]),
                plantSpecies = parsePlantSpecies(prefs[Keys.plantSpecies]),
                companionRole = parseCompanionRole(prefs[Keys.companionRole]),
                localExecutionBackend = parseLocalExecutionBackend(prefs[Keys.localExecutionBackend]),
                guardianAngelPersonality = parseGuardianAngelPersonality(prefs[Keys.guardianAngelPersonality]),
                avatarPresentationMode = parseAvatarPresentationMode(prefs[Keys.avatarPresentationMode]),
                avatarExpressionEnabled = prefs[Keys.avatarExpressionEnabled]
                    ?: AppSettings.DEFAULT_AVATAR_EXPRESSION_ENABLED,
                chatStatusBarVisible = prefs[Keys.chatStatusBarVisible]
                    ?: AppSettings.DEFAULT_CHAT_STATUS_BAR_VISIBLE,
                mrAvatarMotionEnabled = prefs[Keys.mrAvatarMotionEnabled]
                    ?: AppSettings.DEFAULT_MR_AVATAR_MOTION_ENABLED,
                mrModelControlsVisible = prefs[Keys.mrModelControlsVisible]
                    ?: AppSettings.DEFAULT_MR_MODEL_CONTROLS_VISIBLE,
                mrModelOffsetX = prefs[Keys.mrModelOffsetX] ?: AppSettings.DEFAULT_MR_MODEL_OFFSET_X,
                mrModelOffsetY = prefs[Keys.mrModelOffsetY] ?: AppSettings.DEFAULT_MR_MODEL_OFFSET_Y,
                mrModelScale = prefs[Keys.mrModelScale] ?: AppSettings.DEFAULT_MR_MODEL_SCALE,
                mrModelCameraDistance = prefs[Keys.mrModelCameraDistance]
                    ?: AppSettings.DEFAULT_MR_MODEL_CAMERA_DISTANCE,
                mrModelYawDegrees = prefs[Keys.mrModelYawDegrees] ?: AppSettings.DEFAULT_MR_MODEL_YAW_DEGREES,
                mrModelTiltDegrees = prefs[Keys.mrModelTiltDegrees] ?: AppSettings.DEFAULT_MR_MODEL_TILT_DEGREES,
                aiImageQuality = parseAiImageQuality(prefs[Keys.aiImageQuality]),
                localBaseUrl = prefs[Keys.localBaseUrl] ?: AppSettings.DEFAULT_LOCAL_BASE_URL,
                localModel = prefs[Keys.localModel] ?: AppSettings.DEFAULT_LOCAL_MODEL,
                cloudBaseUrl = prefs[Keys.cloudBaseUrl] ?: AppSettings.DEFAULT_CLOUD_BASE_URL,
                cloudModel = prefs[Keys.cloudModel] ?: AppSettings.DEFAULT_CLOUD_MODEL,
                cloudApiKey = prefs[Keys.cloudApiKey] ?: "",
                ollamaCloudBaseUrl = prefs[Keys.ollamaCloudBaseUrl] ?: AppSettings.DEFAULT_OLLAMA_CLOUD_BASE_URL,
                ollamaCloudModel = prefs[Keys.ollamaCloudModel] ?: AppSettings.DEFAULT_OLLAMA_CLOUD_MODEL,
                ollamaCloudApiKey = prefs[Keys.ollamaCloudApiKey] ?: AppSettings.DEFAULT_OLLAMA_CLOUD_API_KEY,
                streamResponses = prefs[Keys.streamResponses] ?: false,
                speakAssistantReplies = prefs[Keys.speakAssistantReplies] ?: AppSettings.DEFAULT_SPEAK_ASSISTANT_REPLIES,
                ttsVoiceProfile = parseTtsVoiceProfile(prefs[Keys.ttsVoiceProfile]),
                ttsSpeechRateMultiplier = prefs[Keys.ttsSpeechRateMultiplier]?.toDoubleOrNull()
                    ?.coerceIn(1.0, 2.0)
                    ?: AppSettings.DEFAULT_TTS_SPEECH_RATE_MULTIPLIER,
                autoSmallTalkInterval = AutoSmallTalkInterval.fromName(prefs[Keys.autoSmallTalkInterval]),
                maxOutputTokens = prefs[Keys.maxOutputTokens]?.toIntOrNull() ?: AppSettings.DEFAULT_MAX_OUTPUT_TOKENS,
                topK = prefs[Keys.topK]?.toIntOrNull() ?: AppSettings.DEFAULT_TOP_K,
                topP = prefs[Keys.topP]?.toDoubleOrNull() ?: AppSettings.DEFAULT_TOP_P,
                temperature = prefs[Keys.temperature]?.toDoubleOrNull() ?: AppSettings.DEFAULT_TEMPERATURE,
                thinkingEnabled = prefs[Keys.thinkingEnabled] ?: AppSettings.DEFAULT_THINKING_ENABLED,
            )
        }

    override suspend fun save(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.providerType] = settings.providerType.name
            prefs[Keys.plantSpecies] = settings.plantSpecies.name
            prefs[Keys.companionRole] = settings.companionRole.name
            prefs[Keys.localExecutionBackend] = settings.localExecutionBackend.name
            prefs[Keys.guardianAngelPersonality] = settings.guardianAngelPersonality.name
            prefs[Keys.avatarPresentationMode] = settings.avatarPresentationMode.name
            prefs[Keys.avatarExpressionEnabled] = settings.avatarExpressionEnabled
            prefs[Keys.chatStatusBarVisible] = settings.chatStatusBarVisible
            prefs[Keys.mrAvatarMotionEnabled] = settings.mrAvatarMotionEnabled
            prefs[Keys.mrModelControlsVisible] = settings.mrModelControlsVisible
            prefs[Keys.mrModelOffsetX] = settings.mrModelOffsetX
            prefs[Keys.mrModelOffsetY] = settings.mrModelOffsetY
            prefs[Keys.mrModelScale] = settings.mrModelScale
            prefs[Keys.mrModelCameraDistance] = settings.mrModelCameraDistance
            prefs[Keys.mrModelYawDegrees] = settings.mrModelYawDegrees
            prefs[Keys.mrModelTiltDegrees] = settings.mrModelTiltDegrees
            prefs[Keys.aiImageQuality] = settings.aiImageQuality.name
            prefs[Keys.localBaseUrl] = settings.localBaseUrl
            prefs[Keys.localModel] = settings.localModel
            prefs[Keys.cloudBaseUrl] = settings.cloudBaseUrl.ifBlank { AppSettings.DEFAULT_CLOUD_BASE_URL }
            prefs[Keys.cloudModel] = settings.cloudModel.ifBlank { AppSettings.DEFAULT_CLOUD_MODEL }
            prefs[Keys.cloudApiKey] = settings.cloudApiKey
            prefs[Keys.ollamaCloudBaseUrl] =
                settings.ollamaCloudBaseUrl.ifBlank { AppSettings.DEFAULT_OLLAMA_CLOUD_BASE_URL }
            prefs[Keys.ollamaCloudModel] =
                settings.ollamaCloudModel.ifBlank { AppSettings.DEFAULT_OLLAMA_CLOUD_MODEL }
            prefs[Keys.ollamaCloudApiKey] = settings.ollamaCloudApiKey
            prefs[Keys.streamResponses] = settings.streamResponses
            prefs[Keys.speakAssistantReplies] = settings.speakAssistantReplies
            prefs[Keys.ttsVoiceProfile] = settings.ttsVoiceProfile.name
            prefs[Keys.ttsSpeechRateMultiplier] = settings.ttsSpeechRateMultiplier.coerceIn(1.0, 2.0).toString()
            prefs[Keys.autoSmallTalkInterval] = settings.autoSmallTalkInterval.name
            prefs[Keys.maxOutputTokens] = settings.maxOutputTokens.toString()
            prefs[Keys.topK] = settings.topK.toString()
            prefs[Keys.topP] = settings.topP.toString()
            prefs[Keys.temperature] = settings.temperature.toString()
            prefs[Keys.thinkingEnabled] = settings.thinkingEnabled
        }
    }

    override suspend fun setAvatarExpressionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.avatarExpressionEnabled] = enabled
        }
    }

    override suspend fun setMrAvatarMotionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.mrAvatarMotionEnabled] = enabled
        }
    }

    private object Keys {
        val providerType = stringPreferencesKey("provider_type")
        val plantSpecies = stringPreferencesKey("plant_species")
        val companionRole = stringPreferencesKey("companion_role")
        val localExecutionBackend = stringPreferencesKey("local_execution_backend")
        val guardianAngelPersonality = stringPreferencesKey("guardian_angel_personality")
        val avatarPresentationMode = stringPreferencesKey("avatar_presentation_mode")
        val avatarExpressionEnabled = booleanPreferencesKey("avatar_expression_enabled")
        val chatStatusBarVisible = booleanPreferencesKey("chat_status_bar_visible")
        val mrAvatarMotionEnabled = booleanPreferencesKey("mr_avatar_motion_enabled")
        val mrModelControlsVisible = booleanPreferencesKey("mr_model_controls_visible")
        val mrModelOffsetX = floatPreferencesKey("mr_model_offset_x")
        val mrModelOffsetY = floatPreferencesKey("mr_model_offset_y")
        val mrModelScale = floatPreferencesKey("mr_model_scale")
        val mrModelCameraDistance = floatPreferencesKey("mr_model_camera_distance")
        val mrModelYawDegrees = floatPreferencesKey("mr_model_yaw_degrees")
        val mrModelTiltDegrees = floatPreferencesKey("mr_model_tilt_degrees")
        val aiImageQuality = stringPreferencesKey("ai_image_quality")
        val localBaseUrl = stringPreferencesKey("local_base_url")
        val localModel = stringPreferencesKey("local_model")
        val cloudBaseUrl = stringPreferencesKey("cloud_base_url")
        val cloudModel = stringPreferencesKey("cloud_model")
        val cloudApiKey = stringPreferencesKey("cloud_api_key")
        val ollamaCloudBaseUrl = stringPreferencesKey("ollama_cloud_base_url")
        val ollamaCloudModel = stringPreferencesKey("ollama_cloud_model")
        val ollamaCloudApiKey = stringPreferencesKey("ollama_cloud_api_key")
        val streamResponses = booleanPreferencesKey("stream_responses")
        val speakAssistantReplies = booleanPreferencesKey("speak_assistant_replies")
        val ttsVoiceProfile = stringPreferencesKey("tts_voice_profile")
        val ttsSpeechRateMultiplier = stringPreferencesKey("tts_speech_rate_multiplier")
        val autoSmallTalkInterval = stringPreferencesKey("auto_small_talk_interval")
        val maxOutputTokens = stringPreferencesKey("max_output_tokens")
        val topK = stringPreferencesKey("top_k")
        val topP = stringPreferencesKey("top_p")
        val temperature = stringPreferencesKey("temperature")
        val thinkingEnabled = booleanPreferencesKey("thinking_enabled")
    }

    private fun parsePlantSpecies(rawValue: String?): PlantSpecies {
        return runCatching { PlantSpecies.valueOf(rawValue ?: PlantSpecies.default().name) }
            .getOrDefault(PlantSpecies.default())
    }

    private fun parseCompanionRole(rawValue: String?): CompanionRole {
        return runCatching { CompanionRole.valueOf(rawValue ?: CompanionRole.default().name) }
            .getOrDefault(CompanionRole.default())
    }

    private fun parseLocalExecutionBackend(rawValue: String?): LocalExecutionBackend {
        return runCatching { LocalExecutionBackend.valueOf(rawValue ?: LocalExecutionBackend.default().name) }
            .getOrDefault(LocalExecutionBackend.default())
    }

    private fun parseGuardianAngelPersonality(rawValue: String?): GuardianAngelPersonality {
        return runCatching { GuardianAngelPersonality.valueOf(rawValue ?: GuardianAngelPersonality.default().name) }
            .getOrDefault(GuardianAngelPersonality.default())
    }

    private fun parseAvatarPresentationMode(rawValue: String?): AvatarPresentationMode {
        return runCatching { AvatarPresentationMode.valueOf(rawValue ?: AvatarPresentationMode.default().name) }
            .getOrDefault(AvatarPresentationMode.default())
    }

    private fun parseAiImageQuality(rawValue: String?): AiImageQuality {
        return runCatching { AiImageQuality.valueOf(rawValue ?: AiImageQuality.default().name) }
            .getOrDefault(AiImageQuality.default())
    }

    private fun parseProviderType(rawValue: String?): ProviderType {
        return runCatching { ProviderType.valueOf(rawValue ?: AppSettings.DEFAULT_PROVIDER_TYPE.name) }
            .getOrDefault(AppSettings.DEFAULT_PROVIDER_TYPE)
    }

    private fun parseTtsVoiceProfile(rawValue: String?): TtsVoiceProfile {
        return runCatching { TtsVoiceProfile.valueOf(rawValue ?: TtsVoiceProfile.default().name) }
            .getOrDefault(TtsVoiceProfile.default())
    }

}
