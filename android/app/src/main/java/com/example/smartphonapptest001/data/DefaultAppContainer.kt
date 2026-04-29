package com.example.smartphonapptest001.data

import android.content.Context
import com.example.smartphonapptest001.data.knowledge.AssetPlantKnowledgeRepository
import com.example.smartphonapptest001.data.knowledge.PlantKnowledgeRepository
import com.example.smartphonapptest001.data.logging.AppCrashReporter
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.logging.FileAppLogger
import com.example.smartphonapptest001.data.local.LocalModelService
import com.example.smartphonapptest001.data.model.PlantProfileRepository
import com.example.smartphonapptest001.data.network.KtorOllamaNativeChatApi
import com.example.smartphonapptest001.data.network.KtorOpenAiCompatibleChatApi
import com.example.smartphonapptest001.data.repository.DefaultChatRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class DefaultAppContainer(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val appLogger: AppLogger = FileAppLogger(context)
    val crashReporter: AppCrashReporter = AppCrashReporter(context)
    val plantProfileRepository: PlantProfileRepository = PlantProfileRepository(context)
    val plantKnowledgeRepository: PlantKnowledgeRepository = AssetPlantKnowledgeRepository(context, appLogger)
    val localModelService: LocalModelService = LocalModelService(context, plantProfileRepository, appLogger)

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val chatApi = KtorOpenAiCompatibleChatApi(
        client = httpClient,
        json = json,
    )

    private val ollamaNativeChatApi = KtorOllamaNativeChatApi(
        client = httpClient,
        json = json,
    )

    val settingsRepository = DataStoreSettingsRepository(context)
    val chatRepository = DefaultChatRepository(chatApi, ollamaNativeChatApi, localModelService, appLogger)
}
