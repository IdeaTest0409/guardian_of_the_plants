package com.example.smartphonapptest001.data.model

enum class PlantImageSelectionMode(
    val label: String,
    val enabled: Boolean,
) {
    CAMERA_PHOTO(
        label = "植物の写真をその場で撮影する",
        enabled = true,
    ),
    FILE_UPLOAD(
        label = "植物の画像ファイルをアップロードする",
        enabled = true,
    ),
    REALTIME_CAPTURE(
        label = "カメラでリアルタイム取得する",
        enabled = true,
    );

    companion object {
        fun default(): PlantImageSelectionMode = REALTIME_CAPTURE
    }
}
