package com.example.smartphonapptest001.data.model

enum class VoiceVoxSpeaker(
    val label: String,
    val speakerId: Int,
) {
    SHIKOKU_METAN("\u56db\u56fd\u3081\u305f\u3093", 2),
    ZUNDAMON("\u305a\u3093\u3060\u3082\u3093", 3),
    KASUKABE_TSUMUGI("\u6625\u65e5\u90e8\u3064\u3080\u304e", 8);

    companion object {
        fun default(): VoiceVoxSpeaker = SHIKOKU_METAN
    }
}
