package com.example.smartphonapptest001.data.logging

import java.io.PrintWriter
import java.io.StringWriter

fun structuredDetails(vararg pairs: Pair<String, Any?>): String {
    return buildString {
        pairs.forEach { (key, value) ->
            appendLine("$key=${value.toLogValue()}")
        }
    }.trimEnd()
}

fun Throwable.toStructuredLogDetails(vararg pairs: Pair<String, Any?>): String {
    return buildString {
        if (pairs.isNotEmpty()) {
            appendLine("context:")
            pairs.forEach { (key, value) ->
                appendLine("  $key=${value.toLogValue()}")
            }
        }
        appendLine("errorType=${this@toStructuredLogDetails::class.java.name}")
        appendLine("errorMessage=${message.orEmpty()}")
        appendLine("stackTrace:")
        append(stackTraceAsText())
    }.trimEnd()
}

private fun Any?.toLogValue(): String {
    return when (this) {
        null -> ""
        is Throwable -> "${this::class.java.name}: ${message.orEmpty()}"
        else -> toString()
    }
}

private fun Throwable.stackTraceAsText(): String {
    val writer = StringWriter()
    PrintWriter(writer).use { printWriter ->
        printStackTrace(printWriter)
    }
    return writer.toString()
}
