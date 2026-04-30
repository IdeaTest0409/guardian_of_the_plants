package com.example.smartphonapptest001.data.model

enum class VoiceVoxSpeaker(
    val label: String,
    val speakerId: Int,
) {
    SHIKOKU_METAN("四国めたん", 2),
    ZUNDAMON("ずんだもん", 3),
    KASUKABE_TSUMUGI("春日部つむぎ", 8);

    companion object {
        fun default(): VoiceVoxSpeaker = SHIKOKU_METAN
    }
}
