package com.example.smartphonapptest001.data.network

import android.content.Context
import android.os.Build
import com.example.smartphonapptest001.BuildConfig
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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

        logger.log(
            AppLogSeverity.INFO,
            "AppStartReporter",
            "Guardian API base URL configured",
            details = "baseUrl=$normalizedBaseUrl",
        )

        checkHealth(normalizedBaseUrl)
        postAppStart(context, normalizedBaseUrl)
    }

    private suspend fun checkHealth(normalizedBaseUrl: String) {
        val healthUrl = "$normalizedBaseUrl/health"
        runCatching {
            client.get(healthUrl) {
                shortTimeout()
                accept(ContentType.Application.Json)
            }
        }.onSuccess { response ->
            val body = response.bodyAsText()
            val severity = if (response.status.isSuccess()) AppLogSeverity.INFO else AppLogSeverity.WARN
            logger.log(
                severity,
                "AppStartReporter",
                "Guardian API health check completed",
                details = listOf(
                    "url=$healthUrl",
                    "status=${response.status.value}",
                    "body=${body.take(200)}",
                ).joinToString(separator = "\n"),
            )
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.WARN,
                "AppStartReporter",
                "Guardian API health check failed",
                details = listOf(
                    "url=$healthUrl",
                    "errorType=${error::class.qualifiedName}",
                    "errorMessage=${error.message.orEmpty()}",
                ).joinToString(separator = "\n"),
            )
        }
    }

    private suspend fun postAppStart(context: Context, normalizedBaseUrl: String) {
        val appStartUrl = "$normalizedBaseUrl/app-start"
        runCatching {
            client.post(appStartUrl) {
                shortTimeout()
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
        }.onSuccess { response ->
            val body = response.bodyAsText()
            val severity = if (response.status.isSuccess()) AppLogSeverity.INFO else AppLogSeverity.WARN
            logger.log(
                severity,
                "AppStartReporter",
                "Server app-start report completed",
                details = listOf(
                    "url=$appStartUrl",
                    "status=${response.status.value}",
                    "body=${body.take(200)}",
                ).joinToString(separator = "\n"),
            )
        }.onFailure { error ->
            logger.log(
                AppLogSeverity.WARN,
                "AppStartReporter",
                "Server app-start report failed",
                details = listOf(
                    "url=$appStartUrl",
                    "errorType=${error::class.qualifiedName}",
                    "errorMessage=${error.message.orEmpty()}",
                ).joinToString(separator = "\n"),
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.shortTimeout() {
        timeout {
            connectTimeoutMillis = 3_000
            requestTimeoutMillis = 5_000
            socketTimeoutMillis = 5_000
        }
    }
}

@Serializable
private data class AppStartRequest(
    val deviceId: String,
    val appVersion: String,
    val details: Map<String, String> = emptyMap(),
)
