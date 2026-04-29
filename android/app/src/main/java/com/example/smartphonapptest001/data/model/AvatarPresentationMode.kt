package com.example.smartphonapptest001.data.model

enum class AvatarPresentationMode(
    val label: String,
) {
    TWO_D(
        label = "2D images",
    ),
    THREE_D(
        label = "3D model",
    ),
    AR(
        label = "MR camera",
    );

    companion object {
        fun default(): AvatarPresentationMode = AR
    }
}
