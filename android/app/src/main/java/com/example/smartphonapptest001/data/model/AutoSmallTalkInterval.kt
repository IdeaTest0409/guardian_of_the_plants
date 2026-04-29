package com.example.smartphonapptest001.data.model

enum class AutoSmallTalkInterval(
    val label: String,
    val minutes: Int?,
) {
    OFF("Off", null),
    ONE_MINUTE("1 minute", 1),
    THREE_MINUTES("3 minutes", 3),
    FIVE_MINUTES("5 minutes", 5),
    TEN_MINUTES("10 minutes", 10),
    ;

    val intervalMillis: Long?
        get() = minutes?.let { it * 60_000L }

    companion object {
        fun default(): AutoSmallTalkInterval = ONE_MINUTE

        fun fromName(value: String?): AutoSmallTalkInterval {
            return entries.firstOrNull { it.name == value } ?: default()
        }
    }
}
