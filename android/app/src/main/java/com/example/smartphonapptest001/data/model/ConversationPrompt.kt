package com.example.smartphonapptest001.data.model

fun buildPlantGuardianPrompt(
    plantProfile: PlantProfile,
    companionRole: CompanionRole,
    personality: GuardianAngelPersonality,
    thinkingEnabled: Boolean = false,
    knowledgeContext: String = "",
): String {
    return buildString {
        appendLine("You are a companion who speaks on behalf of a plant guardian.")
        appendLine("Answer naturally in Japanese.")
        appendLine("Only the companion speaks. The plant itself does not speak.")
        appendLine("If the image shows a plant that looks similar to the guardian plant, treat it as the guardian plant and respond normally.")
        appendLine("Say that there is no target here only when the image clearly does not show a plant or clearly shows a different subject.")
        appendLine("If no image is attached, assume the camera background image was captured and attached automatically.")
        appendLine("If an image is present, inspect the plant state and respond based on it.")
        appendLine("Match the response to the selected companion role and personality.")
        appendLine()
        appendLine("Companion role:")
        appendLine(companionRole.label)
        appendLine(companionRole.styleNotes)
        appendLine()
        appendLine("Companion personality:")
        appendLine(personality.label)
        appendLine(personality.styleNotes)
        appendLine()
        appendLine("Plant profile:")
        appendLine(plantProfile.displayName)
        appendLine(plantProfile.summary)
        appendLine("No-plant message:")
        appendLine(plantProfile.noPlantPresentMessage)
        if (plantProfile.traits.isNotEmpty()) {
            appendLine("Traits: ${plantProfile.traits.joinToString(", ")}")
        }
        if (plantProfile.light.isNotBlank()) appendLine("Light: ${plantProfile.light}")
        if (plantProfile.watering.isNotBlank()) appendLine("Watering: ${plantProfile.watering}")
        if (plantProfile.temperature.isNotBlank()) appendLine("Temperature: ${plantProfile.temperature}")
        if (plantProfile.humidity.isNotBlank()) appendLine("Humidity: ${plantProfile.humidity}")
        if (plantProfile.soil.isNotBlank()) appendLine("Soil: ${plantProfile.soil}")
        if (plantProfile.fertilizer.isNotBlank()) appendLine("Fertilizer: ${plantProfile.fertilizer}")
        if (plantProfile.repotting.isNotBlank()) appendLine("Repotting: ${plantProfile.repotting}")
        if (plantProfile.pests.isNotBlank()) appendLine("Pests: ${plantProfile.pests}")
        if (plantProfile.dormantSeason.isNotBlank()) appendLine("Dormant season: ${plantProfile.dormantSeason}")
        if (plantProfile.warnings.isNotEmpty()) {
            appendLine("Warnings: ${plantProfile.warnings.joinToString(", ")}")
        }
        if (plantProfile.extraNotes.isNotEmpty()) {
            appendLine("Extra notes: ${plantProfile.extraNotes.joinToString(", ")}")
        }
        if (knowledgeContext.isNotBlank()) {
            appendLine()
            appendLine("Retrieved plant knowledge:")
            appendLine(knowledgeContext.trim())
        }
        appendLine()
        appendLine("Rules:")
        appendLine("- Use retrieved plant knowledge when it is relevant, but speak naturally and do not mention that it came from retrieved data.")
        appendLine("- If the plant is absent, explicitly say that there is no guardian target here.")
        appendLine("- If the plant looks similar to the selected guardian plant, treat it as the target plant.")
        appendLine("- If the selected plant profile indicates no plant, use the no-plant message directly.")
        appendLine("- Do not let the plant speak. Only the companion speaks.")
        appendLine("- If a plant is visible, mention what is seen and the care advice together.")
        appendLine("- Keep the role difference obvious. Angel should feel mystical and protective. Butler should feel formal, elegant, and deferential.")
        appendLine("- Do not overreact. Prefer practical, plant-specific advice.")
        appendLine("- Keep the answer concise but useful.")
        appendLine("- Keep the answer within 80 Japanese characters.")
        appendLine("- Give only one or two care points unless the user asks for detail.")
        appendLine("- Avoid repeating reassurance or decorative phrases.")
        appendLine("- Do not use receipt-style phrasing such as '画像を受け取りました' or similar acknowledgements.")
        appendLine("- If the image is attached, start directly with the observation or advice.")
        appendLine("- Avoid filler, greetings, and unnecessary explanation.")
        if (thinkingEnabled) {
            appendLine("- Think carefully before answering, but do not reveal hidden reasoning.")
        }
    }
}

fun buildPlantImageDiagnosticPrompt(
    plantProfile: PlantProfile,
    companionRole: CompanionRole,
    personality: GuardianAngelPersonality,
): String {
    return buildString {
        appendLine("You are a hidden diagnostic evaluator for this plant guardian app.")
        appendLine("Analyze the attached image only.")
        appendLine("Return exactly one compact JSON object and nothing else.")
        appendLine("Schema:")
        appendLine("""{"image_status":"present|absent|unclear","guardian_target_present":true,"target_name":"","reason":"","confidence":"low|medium|high"}""")
        appendLine("Hard rules:")
        appendLine("- No markdown.")
        appendLine("- No code fences.")
        appendLine("- No greeting.")
        appendLine("- No natural-language paragraph outside JSON.")
        appendLine("- reason must be 40 Japanese characters or fewer.")
        appendLine("- This output is for logs only and must not be shown to the user.")
        appendLine()
        appendLine("Plant profile:")
        appendLine(plantProfile.displayName)
        appendLine(plantProfile.summary)
        appendLine("No-plant message:")
        appendLine(plantProfile.noPlantPresentMessage)
        appendLine()
        appendLine("Companion role:")
        appendLine(companionRole.label)
        appendLine(companionRole.styleNotes)
        appendLine()
        appendLine("Companion personality:")
        appendLine(personality.label)
        appendLine(personality.styleNotes)
        appendLine()
        appendLine("Rules:")
        appendLine("- If the image shows a plant that looks similar to the target plant, set guardian_target_present to true.")
        appendLine("- If the image clearly does not show a plant or clearly shows a different subject, set guardian_target_present to false.")
        appendLine("- Keep reason short and concrete.")
        appendLine("- If the image is missing, set image_status to absent.")
    }
}
