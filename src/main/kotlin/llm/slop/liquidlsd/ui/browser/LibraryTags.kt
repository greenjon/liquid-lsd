package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.LibraryPanel
import mu.KotlinLogging
import java.io.File

/**
 * Tags as crates: adding, removing and renaming tags on saved files of any kind. A tag lives in the file's own `tags` list, so it
 * travels with the file. Only that list is rewritten; every other field of the file is kept exactly as it was. Decks and chains
 * loaded from a retagged file take the new tags, so their next save doesn't put the old ones back.
 */
object LibraryTags {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true }

    /** Tags are stored trimmed and lowercase, as the save dialog writes them. */
    fun normalize(tag: String): String = tag.trim().lowercase()

    /** The tags typed into a field ("Dark, slow ,"), normalized, empty ones dropped. */
    fun parse(text: String): List<String> = text.split(',').map(::normalize).filter { it.isNotEmpty() }.distinct()

    fun read(file: File): List<String> = try {
        json.parseToJsonElement(file.readText()).jsonObject["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Replaces [file]'s tags with [tags]; false when the file couldn't be read or written. */
    internal fun write(file: File, tags: List<String>): Boolean = try {
        val obj = json.parseToJsonElement(file.readText()).jsonObject
        val updated = JsonObject(LinkedHashMap(obj).apply { put("tags", JsonArray(tags.map(::JsonPrimitive))) })
        file.writeText(json.encodeToString(JsonObject.serializer(), updated))
        true
    } catch (e: Exception) {
        logger.error(e) { "Could not update the tags of ${file.name}" }
        false
    }

    /**
     * Applies [change] to the tags of each of [files] and writes the ones that changed. Returns how many files changed.
     * [fxChains] are the mixer's, so a chain loaded from a retagged file follows.
     */
    fun edit(files: List<File>, fxChains: List<FxChain>, change: (List<String>) -> List<String>): Int {
        var changed = 0
        for (file in files.distinct()) {
            if (LibraryRefs.kindOf(file) == null || !file.isFile) continue
            val before = read(file)
            val after = change(before).map(::normalize).filter { it.isNotEmpty() }.distinct()
            if (after == before || !write(file, after)) continue
            changed++
            PresetManager.retagActive(file, after)
            for (chain in fxChains) if (chain.sourceFile?.absolutePath == file.absolutePath) {
                chain.tags = after
                chain.baselineDto = chain.baselineDto?.copy(tags = after)
            }
        }
        if (changed > 0) {
            FileSystemManager.clearScanCache()
            LibraryPanel.refreshAssets()
        }
        return changed
    }

    fun add(files: List<File>, tags: List<String>, fxChains: List<FxChain>): Int = edit(files, fxChains) { it + tags }

    fun remove(files: List<File>, tag: String, fxChains: List<FxChain>): Int =
        edit(files, fxChains) { current -> current.filter { normalize(it) != normalize(tag) } }

    /** Renames tag [old] to [new] on [files]; a file that already has [new] just loses [old]. */
    fun rename(files: List<File>, old: String, new: String, fxChains: List<FxChain>): Int =
        edit(files, fxChains) { current -> current.map { if (normalize(it) == normalize(old)) new else it } }
}
