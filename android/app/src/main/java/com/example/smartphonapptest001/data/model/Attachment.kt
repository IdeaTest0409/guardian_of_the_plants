package com.example.smartphonapptest001.data.model

import android.net.Uri

enum class AttachmentKind {
    IMAGE,
    AUDIO,
    DOCUMENT,
}

data class Attachment(
    val uri: Uri? = null,
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val dataUrl: String? = null,
)
