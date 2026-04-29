package com.example.smartphonapptest001.data.logging

import kotlinx.serialization.Serializable

@Serializable
enum class AppLogSeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
