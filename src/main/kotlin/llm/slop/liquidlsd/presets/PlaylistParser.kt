package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.models.PlaylistDto
import kotlinx.serialization.json.Json
import java.io.File

object PlaylistParser {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val defaultPresetRoots = listOf(File("library/presets"))
    private val presetExtensions = listOf(".lsd")

    fun parseItems(content: String): List<String> {
        return if (content.trimStart().startsWith("{")) {
            json.decodeFromString<PlaylistDto>(content).items
        } else {
            content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        }
    }

    fun parseFile(file: File): List<String> = parseItems(file.readText())

    fun resolveItem(name: String, presetRoots: List<File> = defaultPresetRoots): File? {
        val direct = File(name)
        if (direct.exists() && direct.isFile) return direct

        for (root in presetRoots) {
            val rooted = File(root, name)
            if (rooted.exists() && rooted.isFile) return rooted
        }

        for (extension in presetExtensions) {
            val nameWithExtension = if (name.endsWith(extension, ignoreCase = true)) name else "$name$extension"
            val directWithExtension = File(nameWithExtension)
            if (directWithExtension.exists() && directWithExtension.isFile) return directWithExtension

            for (root in presetRoots) {
                val rootedWithExtension = File(root, nameWithExtension)
                if (rootedWithExtension.exists() && rootedWithExtension.isFile) return rootedWithExtension
            }
        }

        return null
    }

    fun resolveItems(items: List<String>, presetRoots: List<File> = defaultPresetRoots): List<File> {
        return items.mapNotNull { resolveItem(it, presetRoots) }
    }
}

