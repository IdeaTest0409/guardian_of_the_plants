package com.example.smartphonapptest001.data.model

enum class TtsVoiceProfile(
    val label: String,
    val pitch: Float,
    val speechRate: Float,
) {
    STANDARD("標準", 1.0f, 1.0f),
    CUTE_FEMALE("女性・かわいい", 1.22f, 0.94f),
    SOFT_FEMALE("女性・やさしい", 1.12f, 0.88f),
    BRIGHT_FEMALE("女性・明るい", 1.18f, 1.04f),
    COOL_MALE("男性・かっこいい", 0.82f, 0.92f),
    CALM_MALE("男性・落ち着き", 0.76f, 0.84f),
    BUTLER_MALE("男性・執事", 0.72f, 0.78f);

    companion object {
        fun default(): TtsVoiceProfile = CUTE_FEMALE
    }
}
