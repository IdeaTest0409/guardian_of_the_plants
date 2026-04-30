package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ServerTtsApi(
    private val baseUrl: String,
    private val logger: AppLogger,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String, speakerId: Int): ByteArray? {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        if (normalizedUrl.isBlank()) {
            logger.log(AppLogSeverity.ERROR, TAG, "TTS base URL is not configured")
            return null
        }

        val fullUrl = "$normalizedUrl/tts/synthesize"

        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = """{"text":"${escapeJson(text)}","speaker":$speakerId}"""
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(fullUrl)
                    .post(requestBody)
                    .build()

                logger.log(
                    AppLogSeverity.INFO, TAG, "TTS request started",
                    details = "url=$fullUrl, text=${text.take(50)}, speaker=$speakerId",
                )

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        logger.log(
                            AppLogSeverity.ERROR, TAG, "TTS synthesis failed",
                            details = "url=$fullUrl, status=${response.code}, body=$errorBody",
                        )
                        return@withContext null
                    }
                    val wavData = response.body?.bytes()
                    if (wavData != null && wavData.isNotEmpty()) {
                        logger.log(
                            AppLogSeverity.INFO, TAG, "TTS synthesis successful",
                            details = "text=${text.take(50)}, speaker=$speakerId, size=${wavData.size} bytes",
                        )
                    }
                    wavData
                }
            } catch (e: Exception) {
                logger.log(
                    AppLogSeverity.ERROR, TAG, "TTS synthesis exception",
                    details = buildString {
                        appendLine("url=$fullUrl")
                        appendLine("type=${e::class.java.simpleName}")
                        appendLine("message=${e.message.orEmpty()}")
                        appendLine("stackTrace=${android.util.Log.getStackTraceString(e).take(500)}")
                    },
                )
                null
            }
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private const val TAG = "ServerTtsApi"
    }
}
