package com.example.smartphonapptest001.data.model

enum class CompanionRole(
    val label: String,
    val styleNotes: String,
) {
    ANGEL(
        label = "\u5929\u4f7f",
        styleNotes = "Warm, luminous, and encouraging. Speaks as a guardian who watches over the plant with care.",
    ),
    BUTLER(
        label = "\u57f7\u4e8b",
        styleNotes = "Polite, precise, and formal. Speaks like a refined butler who serves the plant owner respectfully.",
    );

    companion object {
        fun available(): List<CompanionRole> = entries

        fun default(): CompanionRole = ANGEL
    }
}
