package com.example.smartphonapptest001.data.model

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.UUID

fun Bitmap.toRealtimePlantAttachment(): Attachment {
    val scaled = scaleDown(maxDimension = 1024)
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    return Attachment(
        uri = null,
        displayName = "realtime-${UUID.randomUUID().toString().take(8)}.jpg",
        mimeType = "image/jpeg",
        kind = AttachmentKind.IMAGE,
        dataUrl = "data:image/jpeg;base64,$base64",
    )
}

private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
    val longest = maxOf(width, height).toFloat()
    val scale = if (longest <= maxDimension) 1f else maxDimension / longest
    if (scale >= 1f) return this
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}
