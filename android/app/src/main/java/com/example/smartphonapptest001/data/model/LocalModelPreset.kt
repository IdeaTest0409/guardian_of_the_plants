package com.example.smartphonapptest001.data.model

enum class LocalModelPreset(
    val modelId: String,
    val label: String,
    val downloadUrl: String,
    val fileName: String,
) {
    GEMMA_4_E2B(
        modelId = "gemma-4-e2b",
        label = "gemma-4-e2b",
        downloadUrl = "https://huggingface.co/aufklarer/Gemma-4-E2B-LiteRT-LM/resolve/main/model.litertlm?download=1",
        fileName = "gemma-4-e2b.litertlm",
    ),
    GEMMA_4_E2B_IT(
        modelId = "gemma-4-E2B-it",
        label = "gemma-4-E2B-it",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=1",
        fileName = "gemma-4-E2B-it.litertlm",
    ),
    GEMMA_4_E4B_IT(
        modelId = "gemma-4-E4B-it",
        label = "gemma-4-E4B-it",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=1",
        fileName = "gemma-4-E4B-it.litertlm",
    );

    companion object {
        fun default(): LocalModelPreset = GEMMA_4_E2B_IT

        fun fromModelId(rawModelId: String?): LocalModelPreset {
            return entries.firstOrNull { it.modelId.equals(rawModelId?.trim(), ignoreCase = true) }
                ?: default()
        }
    }
}
