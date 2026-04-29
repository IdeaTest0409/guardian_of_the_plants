package com.example.smartphonapptest001.data.logging

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

class FileAppLogger(
    context: Context,
    private val maxEntries: Int = 400,
) : AppLogger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val logFile: File = File(context.filesDir, "smartphone_app_logs.jsonl")
    private val _entries = MutableStateFlow(loadExistingEntries())
    override val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    override fun log(
        severity: AppLogSeverity,
        tag: String,
        message: String,
        details: String?,
    ) {
        val entry = AppLogEntry(
            timestampMillis = System.currentTimeMillis(),
            severity = severity,
            tag = tag,
            message = message,
            details = details,
        )
        _entries.update { current ->
            (current + entry).takeLast(maxEntries)
        }
        scope.launch {
            mutex.withLock {
                appendEntry(entry)
            }
        }
    }

    override fun clear() {
        _entries.value = emptyList()
        scope.launch {
            mutex.withLock {
                if (logFile.exists()) {
                    logFile.writeText("", charset = StandardCharsets.UTF_8)
                }
            }
        }
    }

    override fun exportText(): String {
        return entries.value.joinToString(separator = "\n\n") { it.toDisplayText() }
    }

    private fun loadExistingEntries(): List<AppLogEntry> {
        if (!logFile.exists()) return emptyList()
        return runCatching {
            logFile.readLines(charset = StandardCharsets.UTF_8)
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching {
                        json.decodeFromString(AppLogEntry.serializer(), line)
                    }.getOrNull()
                }
                .takeLast(maxEntries)
        }.getOrDefault(emptyList())
    }

    private fun appendEntry(entry: AppLogEntry) {
        val serialized = json.encodeToString(AppLogEntry.serializer(), entry)
        if (!logFile.exists()) {
            logFile.parentFile?.mkdirs()
            logFile.createNewFile()
        }
        logFile.appendText(serialized + "\n", charset = StandardCharsets.UTF_8)
    }
}
