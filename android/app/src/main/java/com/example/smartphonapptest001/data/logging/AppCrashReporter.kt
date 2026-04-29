package com.example.smartphonapptest001.data.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@Serializable
data class CrashReportRecord(
    val recordedAtMillis: Long,
    val source: String,
    val processName: String? = null,
    val threadName: String? = null,
    val summary: String,
    val details: String,
)

class AppCrashReporter(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val crashFile: File = File(context.filesDir, "last_app_crash.json")

    fun recordUncaughtCrash(
        threadName: String,
        throwable: Throwable,
    ) {
        writeCrashReport(
            CrashReportRecord(
                recordedAtMillis = System.currentTimeMillis(),
                source = "uncaught_exception",
                processName = context.packageName,
                threadName = threadName,
                summary = "${throwable::class.java.name}: ${throwable.message.orEmpty()}",
                details = throwable.toStructuredLogDetails(
                    "threadName" to threadName,
                    "processName" to context.packageName,
                ),
            ),
        )
    }

    fun recordHistoricalExitIfAny(log: (String) -> Unit): CrashReportRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val reasons = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        val latest = reasons.firstOrNull() ?: return null
        val summary = buildString {
            append("reason=")
            append(latest.reason)
            append(", status=")
            append(latest.status)
            append(", importance=")
            append(latest.importance)
            append(", description=")
            append(latest.description.orEmpty())
        }
        val trace = runCatching {
            latest.traceInputStream?.use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        }.getOrNull().orEmpty()
        if (latest.reason != ApplicationExitInfo.REASON_USER_REQUESTED) {
            val report = CrashReportRecord(
                recordedAtMillis = latest.timestamp,
                source = "historical_exit",
                processName = latest.processName,
                summary = summary,
                details = sanitizeCrashDetails(trace.ifBlank { latest.description.orEmpty() }),
            )
            writeCrashReport(report)
            log(report.toDisplayText())
            return report
        }
        return null
    }

    fun consumeLastCrashReport(): CrashReportRecord? {
        if (!crashFile.exists()) return null
        val report = runCatching {
            json.decodeFromString(
                CrashReportRecord.serializer(),
                crashFile.readText(StandardCharsets.UTF_8),
            )
        }.getOrNull()
        crashFile.delete()
        return report
    }

    fun clear() {
        if (crashFile.exists()) {
            crashFile.delete()
        }
    }

    private fun writeCrashReport(report: CrashReportRecord) {
        crashFile.parentFile?.mkdirs()
        crashFile.writeText(json.encodeToString(report), StandardCharsets.UTF_8)
    }
}

fun CrashReportRecord.toDisplayText(): String {
    return buildString {
        append("Crash report from ")
        append(source)
        appendLine()
        append("summary=")
        append(summary)
        appendLine()
        append("process=")
        append(processName ?: "")
        if (!threadName.isNullOrBlank()) {
            append(", thread=")
            append(threadName)
        }
        appendLine()
        appendLine("details:")
        append(details.take(MAX_CRASH_DETAILS_CHARS))
    }
}

fun CrashReportRecord.isFilamentMorphTargetCrash(): Boolean {
    val text = "$summary\n$details"
    return text.contains("libgltfio-jni.so", ignoreCase = true) ||
        text.contains("getMorphTargetCountAt", ignoreCase = true) ||
        text.contains("FilamentAsset", ignoreCase = true)
}

private fun sanitizeCrashDetails(raw: String): String {
    if (raw.isBlank()) return ""
    val printable = raw.map { char ->
        when {
            char == '\n' || char == '\r' || char == '\t' -> char
            char.isISOControl() -> ' '
            else -> char
        }
    }.joinToString(separator = "")
        .replace(Regex("[ \\t]{2,}"), " ")

    val patterns = listOf(
        "SIGSEGV",
        "SEGV_MAPERR",
        "libgltfio-jni.so",
        "getMorphTargetCountAt",
        "FilamentAsset",
        "liblitertlm_jni.so",
        "nativeSendMessage",
        "local-llm-infer",
        "CameraDeviceExe",
    )
    val snippets = patterns.mapNotNull { pattern ->
        val index = printable.indexOf(pattern, ignoreCase = true)
        if (index < 0) {
            null
        } else {
            val start = (index - 360).coerceAtLeast(0)
            val end = (index + 720).coerceAtMost(printable.length)
            printable.substring(start, end).trim()
        }
    }.distinct()

    return buildString {
        appendLine("rawTraceChars=${raw.length}")
        appendLine("sanitizedTraceChars=${printable.length}")
        appendLine("detected=${patterns.filter { printable.contains(it, ignoreCase = true) }.joinToString(", ").ifBlank { "none" }}")
        appendLine("snippets:")
        if (snippets.isEmpty()) {
            append(printable.take(MAX_CRASH_DETAILS_CHARS / 2))
        } else {
            append(snippets.joinToString(separator = "\n---\n").take(MAX_CRASH_DETAILS_CHARS - 200))
        }
    }
}

private const val MAX_CRASH_DETAILS_CHARS = 12_000
