package com.example.smartphonapptest001.data.local

import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.example.smartphonapptest001.data.model.buildPlantGuardianPrompt
import com.example.smartphonapptest001.data.model.PlantProfileRepository

fun buildLocalConversationPrompt(
    messages: List<ChatMessage>,
    settings: AppSettings,
    plantProfileRepository: PlantProfileRepository,
): String {
    val explicitSystemPrompt = messages
        .firstOrNull { it.role == ChatRole.SYSTEM }
        ?.content
        ?.trim()
        .orEmpty()
    val isDiagnosticPrompt = explicitSystemPrompt.contains("hidden diagnostic evaluator", ignoreCase = true)
    val transcript = messages
        .filterNot { it.role == ChatRole.SYSTEM || it.isPending }
        .joinToString(separator = "\n") { message ->
            val roleLabel = when (message.role) {
                ChatRole.USER -> "user"
                ChatRole.ASSISTANT -> "model"
                ChatRole.SYSTEM -> "system"
            }
            val attachmentSummary = if (message.attachments.isNotEmpty()) {
                " [attachments: ${message.attachments.joinToString { it.displayName }}]"
            } else {
                ""
            }
            buildString {
                append(roleLabel)
                append(": ")
                append(message.content.trim())
                append(attachmentSummary)
            }
        }

    val plantProfile = plantProfileRepository.profileFor(settings.plantSpecies)
    val systemPrompt = explicitSystemPrompt.ifBlank {
        buildPlantGuardianPrompt(
            plantProfile = plantProfile,
            companionRole = settings.companionRole,
            personality = settings.guardianAngelPersonality,
            thinkingEnabled = settings.thinkingEnabled,
        )
    }

    return buildString {
        appendLine(systemPrompt)
        if (!isDiagnosticPrompt) {
            appendLine("Selected plant no-plant message: ${plantProfile.noPlantPresentMessage}")
        }
        appendLine()
        appendLine("Conversation transcript:")
        if (transcript.isBlank()) {
            appendLine("(none)")
        } else {
            appendLine(transcript)
        }
        appendLine()
        if (isDiagnosticPrompt) {
            appendLine("Return exactly one compact JSON object. No prose. No markdown.")
        } else {
            appendLine("Answer as the companion only. Do not reveal hidden reasoning.")
            appendLine("If the user attached an image, respond safely even if image interpretation is limited.")
            appendLine("If the image shows a plant that looks similar to the target plant, treat it as the target plant rather than saying there is no target.")
        }
    }
}
