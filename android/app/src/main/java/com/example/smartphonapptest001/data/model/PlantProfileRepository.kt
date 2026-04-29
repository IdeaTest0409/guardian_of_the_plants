package com.example.smartphonapptest001.data.model

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

class PlantProfileRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val profiles: Map<String, PlantProfile> = loadProfiles()

    fun profileFor(species: PlantSpecies): PlantProfile {
        if (species == PlantSpecies.NONE) {
            return noPlantProfile()
        }
        return profiles[species.name.lowercase()] ?: fallbackProfile(species)
    }

    fun profileByKey(key: String): PlantProfile? = profiles[key.lowercase()]

    private fun loadProfiles(): Map<String, PlantProfile> {
        return runCatching {
            val rawJson = appContext.assets.open("plant_profiles.json")
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            val list = json.decodeFromString(
                ListSerializer(PlantProfile.serializer()),
                rawJson,
            )
            list.associateBy { it.key.lowercase() }
        }.getOrElse {
            emptyMap()
        }
    }

    private fun fallbackProfile(species: PlantSpecies): PlantProfile {
        return PlantProfile(
            key = species.name.lowercase(),
            displayName = species.label,
            summary = species.label,
        )
    }

    private fun noPlantProfile(): PlantProfile {
        return PlantProfile(
            key = "none",
            displayName = "\u306a\u3057",
            summary = "\u3053\u3053\u306b\u306f\u5b88\u8b77\u5bfe\u8c61\u306e\u690d\u7269\u304c\u3044\u307e\u305b\u3093\u3002",
            noPlantPresentMessage = "\u3053\u3053\u306b\u306f\u79c1\u306e\u5b88\u8b77\u5bfe\u8c61\u306f\u3044\u306a\u3044\u3002",
        )
    }
}
