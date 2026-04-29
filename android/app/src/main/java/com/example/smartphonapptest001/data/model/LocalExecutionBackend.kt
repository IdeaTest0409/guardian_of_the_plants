package com.example.smartphonapptest001.data.model

import android.content.Context
import com.google.ai.edge.litertlm.Backend

enum class LocalExecutionBackend(
    val label: String,
) {
    DEFAULT(
        label = "\u3053\u308c\u307e\u3067\u901a\u308a",
    ),
    CPU(
        label = "CPU",
    ),
    GPU_NPU(
        label = "GPU / NPU",
    );

    fun toLiteRtBackends(context: Context): List<Backend> = when (this) {
        DEFAULT -> listOf(Backend.CPU())
        CPU -> listOf(Backend.CPU())
        GPU_NPU -> buildList {
            add(Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir))
            add(Backend.GPU())
            add(Backend.CPU())
        }
    }

    companion object {
        fun default(): LocalExecutionBackend = CPU
    }
}
