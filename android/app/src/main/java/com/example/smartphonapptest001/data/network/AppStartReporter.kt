package com.example.smartphonapptest001.data.network

import android.content.Context
import android.os.Build
import com.example.smartphonapptest001.BuildConfig
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class AppStartReporter(
    private val client: HttpClient,
    private val logger: AppLogger,
    private val baseUrl: String = BuildConfig.GUARDIAN_API_BASE_URL,
) {
    suspend fun report(context: Context) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) {
            logger.log(
                AppLogSeverity.INFO,
                "AppStartReporter",
                "Server app-start reporting skipped because guardian API base URL is not configured",
            )
            return
        }

        runCatching {
            client.post("$normalizedBaseUrl/app-start") {
                timeout {
                    connectTimeoutMillis = 3_000
                    requestTimeoutMillis = 5_000
                    socketTimeoutMillis = 5_000
                }
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(
                    AppStartRequest(
                        deviceId = "${Build.MANUFACTURER}/${Build.MODEL}",
                        appVersion = BuildConfig.VERSION_NAME,
                        details = mapOf(
                            "packageName" to context.packageName,
                            "sdkInt" to Build.VERSION.SDK_INT.toString(),
                            "versionCode" to BuildConfig.VERSION_CODE.toString(),
                        ),
                    ),
                )
            }
        }
            .onSuccess {
                logger.log(
                    AppLogSeverity.INFO,
                    "AppStartReporter",
                    "Server app-start report sent",
                )
            }
            .onFailure { error ->
                logger.log(
                    AppLogSeverity.WARN,
                    "AppStartReporter",
                    "Server app-start report failed",
                    details = error.message,
                )
            }
    }
}

@Serializable
private data class AppStartRequest(
    val deviceId: String,
    val appVersion: String,
    val details: Map<String, String> = emptyMap(),
)
