package com.example.smartphonapptest001.data.logging

import kotlinx.coroutines.flow.StateFlow

interface AppLogger {
    val entries: StateFlow<List<AppLogEntry>>
    fun log(
        severity: AppLogSeverity,
        tag: String,
        message: String,
        details: String? = null,
    )

    fun clear()

    fun exportText(): String
}
