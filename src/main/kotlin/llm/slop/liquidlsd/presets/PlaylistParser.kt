package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Shared reader for deck, FX and transition playlists. A playlist is either a JSON object with an
 * `items` array of strings, or plain text with one entry per line (blank lines and `#` comments skipped).
 */
object PlaylistParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val presetExtensions = listOf(".lsd")
    private val defaultRoots = listOf(File("library/presets"))

    fun parseItems(content: String): List<String> {
        if (content.trimStart().startsWith("{")) {
            val root: JsonObject = json.parseToJsonElement(content).jsonObject
            return root["items"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        }
        return content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    fun parseFile(file: File): List<String> = parseItems(file.readText())

    fun resolveItem(name: String, presetRoots: List<File> = defaultRoots): File? {
        // The bare name wins over any extension variant; for each spelling, as given before under the roots.
        val spellings = listOf(name) + presetExtensions.map { if (name.endsWith(it, ignoreCase = true)) name else name + it }
        for (spelling in spellings.distinct()) {
            val hit = (listOf(File(spelling)) + presetRoots.map { File(it, spelling) }).firstOrNull { it.isFile }
            if (hit != null) return hit
        }
        return null
    }

    fun resolveItems(items: List<String>, presetRoots: List<File> = defaultRoots): List<File> =
        items.mapNotNull { resolveItem(it, presetRoots) }
}
