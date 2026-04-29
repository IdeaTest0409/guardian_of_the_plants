package com.example.smartphonapptest001.data.model

enum class PlantSpecies(
    val label: String,
    val enabledForSelection: Boolean,
) {
    NONE(
        label = "\u306a\u3057",
        enabledForSelection = true,
    ),
    SANSEVIERIA(
        label = "\u30b5\u30f3\u30b9\u30d9\u30ea\u30a2",
        enabledForSelection = true,
    ),
    MONSTERA(
        label = "\u30e2\u30f3\u30b9\u30c6\u30e9",
        enabledForSelection = false,
    ),
    POTOS(
        label = "\u30dd\u30c8\u30b9",
        enabledForSelection = false,
    );

    companion object {
        fun available(): List<PlantSpecies> = entries.filter { it.enabledForSelection }

        fun default(): PlantSpecies = SANSEVIERIA
    }
}
