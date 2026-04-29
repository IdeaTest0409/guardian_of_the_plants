package com.example.smartphonapptest001.data.knowledge

import android.content.Context
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.model.PlantProfile
import java.io.IOException
import java.util.Locale

interface PlantKnowledgeRepository {
    suspend fun relevantKnowledge(
        query: String,
        plantProfile: PlantProfile,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): PlantKnowledgeResult

    companion object {
        const val DEFAULT_MAX_CHARS = 900
    }
}

data class PlantKnowledgeResult(
    val text: String,
    val source: String,
    val chunkCount: Int,
) {
    val isEmpty: Boolean = text.isBlank()
}

class AssetPlantKnowledgeRepository(
    private val context: Context,
    private val logger: AppLogger,
) : PlantKnowledgeRepository {
    private val knowledgeFiles = listOf(
        KnowledgeFile(
            assetPath = "knowledge/sansevieria_knowledge.txt",
            plantKeywords = listOf("サンスベリア", "sansevieria", "サンセベリア", "snake plant"),
        ),
    )
    private var cachedChunks: List<KnowledgeChunk>? = null

    override suspend fun relevantKnowledge(
        query: String,
        plantProfile: PlantProfile,
        maxChars: Int,
    ): PlantKnowledgeResult {
        val chunks = loadChunks()
        if (chunks.isEmpty()) {
            return PlantKnowledgeResult(text = "", source = "", chunkCount = 0)
        }

        val queryTokens = buildQueryTokens(query, plantProfile)
        val ranked = chunks
            .map { chunk -> chunk to scoreChunk(chunk, queryTokens, plantProfile) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<KnowledgeChunk, Int>> { it.second }
                    .thenBy { it.first.index },
            )

        val selected = buildList {
            var chars = 0
            for ((chunk, _) in ranked) {
                val nextLength = chunk.text.length + 2
                if (isNotEmpty() && chars + nextLength > maxChars) continue
                add(chunk)
                chars += nextLength
                if (chars >= maxChars) break
            }
        }

        val text = selected.joinToString(separator = "\n\n") { chunk ->
            buildString {
                if (chunk.heading.isNotBlank()) {
                    append("[")
                    append(chunk.heading)
                    append("]\n")
                }
                append(chunk.text)
            }
        }.take(maxChars)

        return PlantKnowledgeResult(
            text = text,
            source = selected.distinctBy { it.source }.joinToString { it.source },
            chunkCount = selected.size,
        )
    }

    private fun loadChunks(): List<KnowledgeChunk> {
        cachedChunks?.let { return it }
        val chunks = knowledgeFiles.flatMap { file ->
            runCatching {
                context.assets.open(file.assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
                    splitIntoChunks(reader.readText(), file)
                }
            }.getOrElse { error ->
                if (error !is IOException) throw error
                logger.log(
                    AppLogSeverity.WARN,
                    TAG,
                    "Plant knowledge asset could not be loaded",
                    details = "assetPath=${file.assetPath}\nerror=${error.message}",
                )
                emptyList()
            }
        }
        cachedChunks = chunks
        return chunks
    }

    private fun splitIntoChunks(text: String, file: KnowledgeFile): List<KnowledgeChunk> {
        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        if (normalized.isBlank()) return emptyList()

        val blocks = normalized
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var currentHeading = ""
        return blocks.mapIndexed { index, block ->
            val lines = block.lines()
            val firstLine = lines.firstOrNull().orEmpty().trim()
            if (firstLine.length <= 40 && firstLine.any { it == '■' || it == '【' || it.isDigit() }) {
                currentHeading = firstLine
            }
            KnowledgeChunk(
                source = file.assetPath,
                index = index,
                heading = currentHeading,
                text = block,
                plantKeywords = file.plantKeywords,
            )
        }
    }

    private fun buildQueryTokens(query: String, plantProfile: PlantProfile): Set<String> {
        val baseText = listOf(
            query,
            plantProfile.displayName,
            plantProfile.summary,
            plantProfile.traits.joinToString(" "),
        ).joinToString(" ")

        val dynamicTokens = TOKEN_REGEX
            .findAll(baseText.lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it.length >= 2 }
            .toSet()

        val japaneseCareTokens = listOf(
            "水", "水やり", "乾燥", "根腐れ", "葉", "色", "黄色", "茶色", "しわ",
            "冬", "夏", "季節", "日光", "直射日光", "明るい", "置き場所", "肥料",
            "植え替え", "土", "鉢", "病害虫", "寒さ", "温度", "触", "元気",
        ).filter { token -> query.contains(token) || plantProfile.summary.contains(token) }

        return dynamicTokens + japaneseCareTokens + plantProfile.displayName
    }

    private fun scoreChunk(
        chunk: KnowledgeChunk,
        queryTokens: Set<String>,
        plantProfile: PlantProfile,
    ): Int {
        val searchable = "${chunk.heading}\n${chunk.text}".lowercase(Locale.ROOT)
        var score = 0
        for (token in queryTokens) {
            if (token.isBlank()) continue
            if (searchable.contains(token.lowercase(Locale.ROOT))) {
                score += when {
                    token == plantProfile.displayName -> 4
                    token.length <= 2 -> 1
                    else -> 2
                }
            }
        }
        if (chunk.plantKeywords.any { searchable.contains(it.lowercase(Locale.ROOT)) }) {
            score += 3
        }
        if (chunk.heading.contains("アプリ用")) {
            score += 1
        }
        return score
    }

    private data class KnowledgeFile(
        val assetPath: String,
        val plantKeywords: List<String>,
    )

    private data class KnowledgeChunk(
        val source: String,
        val index: Int,
        val heading: String,
        val text: String,
        val plantKeywords: List<String>,
    )

    private companion object {
        const val TAG = "PlantKnowledgeRepository"
        val TOKEN_REGEX = Regex("[A-Za-z0-9_\\-]+|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}ー]{2,}")
    }
}
