package com.example.smartphonapptest001.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlantProfile(
    val key: String,
    val displayName: String,
    val summary: String,
    val traits: List<String> = emptyList(),
    val light: String = "",
    val watering: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val soil: String = "",
    val fertilizer: String = "",
    val repotting: String = "",
    val pests: String = "",
    val dormantSeason: String = "",
    val warnings: List<String> = emptyList(),
    val extraNotes: List<String> = emptyList(),
    val noPlantPresentMessage: String = "\u3053\u3053\u306b\u306f\u79c1\u306e\u5b88\u8b77\u5bfe\u8c61\u306f\u3044\u306a\u3044\u3002",
)
