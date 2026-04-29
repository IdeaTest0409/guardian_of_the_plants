package com.example.smartphonapptest001.data.model

enum class AiImageQuality(
    val label: String,
    val maxDimension: Int,
    val jpegQuality: Int,
) {
    STABLE(
        label = "Stable / 384px / JPEG 70",
        maxDimension = 384,
        jpegQuality = 70,
    ),
    STANDARD(
        label = "Standard / 512px / JPEG 75",
        maxDimension = 512,
        jpegQuality = 75,
    ),
    DETAILED(
        label = "Detailed / 768px / JPEG 80",
        maxDimension = 768,
        jpegQuality = 80,
    ),
    ORIGINAL(
        label = "Original attachment",
        maxDimension = 0,
        jpegQuality = 100,
    );

    companion object {
        fun default(): AiImageQuality = STANDARD
    }
}
