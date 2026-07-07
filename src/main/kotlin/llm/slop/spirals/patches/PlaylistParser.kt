package llm.slop.spirals.patches

import llm.slop.spirals.models.PlaylistDto
import kotlinx.serialization.json.Json
import llm.slop.spirals.config.ProjectConfig
import java.io.File

object PlaylistParser {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val defaultPatchRoots = listOf(File(ProjectConfig.Paths.PATCHES_DIR))
    private val patchExtensions = ProjectConfig.Files.patchFileSuffixes

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

    fun resolveItem(name: String, patchRoots: List<File> = defaultPatchRoots): File? {
        val direct = File(name)
        if (direct.exists() && direct.isFile) return direct

        for (root in patchRoots) {
            val rooted = File(root, name)
            if (rooted.exists() && rooted.isFile) return rooted
        }

        for (extension in patchExtensions) {
            val nameWithExtension = if (name.endsWith(extension, ignoreCase = true)) name else "$name$extension"
            val directWithExtension = File(nameWithExtension)
            if (directWithExtension.exists() && directWithExtension.isFile) return directWithExtension

            for (root in patchRoots) {
                val rootedWithExtension = File(root, nameWithExtension)
                if (rootedWithExtension.exists() && rootedWithExtension.isFile) return rootedWithExtension
            }
        }

        return null
    }

    fun resolveItems(items: List<String>, patchRoots: List<File> = defaultPatchRoots): List<File> {
        return items.mapNotNull { resolveItem(it, patchRoots) }
    }
}
