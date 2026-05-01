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
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String, speakerId: Int, preferredFormat: String = "aac"): AudioResult? {
        return synthesizeOnce(text, speakerId, preferredFormat)
            ?: if (preferredFormat != "wav") synthesizeOnce(text, speakerId, "wav") else null
    }

    private suspend fun synthesizeOnce(text: String, speakerId: Int, format: String): AudioResult? {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        if (normalizedUrl.isBlank()) {
            logger.log(AppLogSeverity.ERROR, TAG, "TTS base URL is not configured")
            return null
        }

        val fullUrl = "$normalizedUrl/tts/synthesize"

        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = """{"text":"${escapeJson(text)}","speaker":$speakerId,"format":"${escapeJson(format)}"}"""
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(fullUrl)
                    .post(requestBody)
                    .build()

                logger.log(
                    AppLogSeverity.INFO, TAG, "TTS request started",
                    details = "url=$fullUrl, text=${text.take(50)}, speaker=$speakerId, format=$format",
                )

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        logger.log(
                            AppLogSeverity.ERROR, TAG, "TTS synthesis failed",
                            details = "url=$fullUrl, status=${response.code}, format=$format, body=$errorBody",
                        )
                        return@withContext null
                    }
                    val audioData = response.body?.bytes()
                    if (audioData != null && audioData.isNotEmpty()) {
                        val responseFormat = response.header("X-Audio-Format")?.ifBlank { format } ?: format
                        val extension = response.header("X-Audio-Extension")?.ifBlank { extensionFor(responseFormat) }
                            ?: extensionFor(responseFormat)
                        logger.log(
                            AppLogSeverity.INFO, TAG, "TTS synthesis successful",
                            details = "text=${text.take(50)}, speaker=$speakerId, format=$responseFormat, size=${audioData.size} bytes",
                        )
                        return@withContext AudioResult(
                            bytes = audioData,
                            format = responseFormat,
                            extension = extension,
                        )
                    }
                    null
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

        private fun extensionFor(format: String): String =
            when (format.lowercase()) {
                "aac" -> "m4a"
                else -> "wav"
            }
    }
}

data class AudioResult(
    val bytes: ByteArray,
    val format: String,
    val extension: String,
)
