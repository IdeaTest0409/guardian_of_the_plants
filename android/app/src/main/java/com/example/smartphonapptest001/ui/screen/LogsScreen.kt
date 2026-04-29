package com.example.smartphonapptest001.ui.screen

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartphonapptest001.data.logging.AppLogEntry
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import java.io.File
import java.nio.charset.StandardCharsets

@Composable
fun LogsScreen(
    entries: List<AppLogEntry>,
    onClearLogs: () -> Unit,
) {
    val context = LocalContext.current
    val displayedEntries = entries.asReversed()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Execution Logs",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Error, request, response, and state change logs are stored locally and can be shared for debugging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { },
                label = { Text("Entries: ${entries.size}") },
            )
            AssistChip(
                onClick = { },
                label = { Text("Errors: ${entries.count { it.severity == AppLogSeverity.ERROR }}") },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    shareLogs(context, displayedEntries)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                Text("Share logs")
            }
            Button(
                onClick = onClearLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.DeleteForever, contentDescription = null)
                Text("Clear logs")
            }
        }

        if (displayedEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No logs yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayedEntries, key = { "${it.timestampMillis}-${it.tag}-${it.message}" }) { entry ->
                    LogCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LogCard(entry: AppLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (entry.severity) {
                AppLogSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
                AppLogSeverity.WARN -> MaterialTheme.colorScheme.tertiaryContainer
                AppLogSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
                AppLogSeverity.DEBUG -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "[${entry.formattedTimestamp}] ${entry.severity.name} / ${entry.tag}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (!entry.details.isNullOrBlank()) {
                Text(
                    text = entry.details.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shareLogs(context: android.content.Context, entries: List<AppLogEntry>) {
    val text = if (entries.isEmpty()) {
        "No logs recorded."
    } else {
        entries.joinToString(separator = "\n\n") { it.toDisplayText() }
    }
    val shareFile = File(context.cacheDir, "smartphonapptest001_logs.txt").apply {
        parentFile?.mkdirs()
        outputStream().use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        shareFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "smartphonapptest001 logs")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share logs"))
}
