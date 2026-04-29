package com.example.smartphonapptest001.data.logging

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class AppLogEntry(
    val timestampMillis: Long,
    val severity: AppLogSeverity,
    val tag: String,
    val message: String,
    val details: String? = null,
) {
    val formattedTimestamp: String
        get() = DATE_FORMATTER.format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()))

    fun toDisplayText(): String {
        return buildString {
            append('[')
            append(formattedTimestamp)
            append("] ")
            append(severity.name)
            append(" / ")
            append(tag)
            append(": ")
            append(message)
            if (!details.isNullOrBlank()) {
                appendLine()
                appendLine("details:")
                append(details)
            }
        }
    }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}
