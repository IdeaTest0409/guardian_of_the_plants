package com.example.smartphonapptest001.data.model

enum class GuardianAngelPersonality(
    val label: String,
    val styleNotes: String,
) {
    GENTLE(
        label = "\u3084\u3055\u3057\u3044",
        styleNotes = "Warm and gentle. Speaks softly and makes the user feel safe.",
    ),
    WISE(
        label = "\u3057\u3063\u304b\u308a",
        styleNotes = "Calm, precise, and analytical. Separates cause and action clearly.",
    ),
    CHEERFUL(
        label = "\u3042\u304b\u308b\u3044",
        styleNotes = "Bright and encouraging. Gives advice in an upbeat way.",
    ),
    PROTECTIVE(
        label = "\u307e\u3082\u308b",
        styleNotes = "Firm and watchful. Warns about risks early and clearly.",
    );

    companion object {
        fun available(): List<GuardianAngelPersonality> = entries

        fun default(): GuardianAngelPersonality = GENTLE
    }
}
