package com.example.smartphonapptest001.data.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val isPending: Boolean = false,
)
