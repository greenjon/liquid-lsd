package llm.slop.liquidlsd.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

/** One slot of a page: a [PerfRows.CATALOG] row id. */
@Serializable
data class RowPlacement(val row: String)

/** A Perform page: exactly [ROWS] rows chosen from the catalog. `perform.<id>` is how controller profiles name it. */
@Serializable
data class PerfPageDef(
    val id: String,
    val name: String,
    val tooltip: String = "",
    val rows: List<RowPlacement>
) {
    /** Structural problems; empty when the page can be shown. */
    fun problems(): List<String> = buildList {
        if (id.isBlank()) add("page id is blank")
        if (name.isBlank()) add("page $id has no name")
        if (rows.size != ROWS) add("page $id has ${rows.size} rows, needs exactly $ROWS")
        rows.filter { it.row !in PerfRows.CATALOG }.forEach { add("page $id: unknown row '${it.row}'") }
    }

    companion object {
        const val ROWS = 4

        /** The built-in page shown first. */
        const val DEFAULT_ID = "decks"
    }
}

/**
 * Finds Perform pages: built-ins shipped in the jar (`perform_pages/<name>.json`) and user pages in
 * `library/perform_pages/`. A user page with the same id replaces the built-in in place; new user pages
 * follow the built-ins, sorted by file name. Pages with structural problems are skipped.
 */
class PerfPageStore(
    private val userDir: File = File("library/perform_pages"),
    private val builtInNames: List<String> = BUILT_IN_NAMES
) {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<PerfPageDef>? = null

    fun reload() { cache = null }

    /** Every usable page, in strip order. Never empty: if nothing loads, a minimal DECKS page is synthesised. */
    fun all(): List<PerfPageDef> = cache ?: load().also { cache = it }

    fun get(id: String): PerfPageDef? = all().firstOrNull { it.id == id }

    fun indexOf(id: String): Int = all().indexOfFirst { it.id == id }

    private fun load(): List<PerfPageDef> {
        val pages = LinkedHashMap<String, PerfPageDef>()
        for (name in builtInNames) {
            val text = PerfPageStore::class.java.getResourceAsStream("/perform_pages/$name.json")
                ?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                logger.error { "Built-in perform page missing from resources: $name" }
                continue
            }
            parse(text, "built-in $name")?.let { pages[it.id] = it }
        }
        val userFiles = userDir.listFiles { _, n -> n.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        for (file in userFiles) {
            parse(file.readText(), file.path)?.let { pages[it.id] = it }
        }
        if (pages.isEmpty()) {
            logger.error { "No perform pages loaded; using a synthesised DECKS page" }
            val fallback = PerfPageDef("decks", "DECKS", rows = PerfRows.DECK_TAGS.map { RowPlacement("deck.$it.srcfx") })
            pages[fallback.id] = fallback
        }
        return pages.values.toList()
    }

    private fun parse(text: String, source: String): PerfPageDef? {
        val page = try {
            json.decodeFromString<PerfPageDef>(text)
        } catch (e: Exception) {
            logger.error(e) { "Could not parse perform page ($source)" }
            return null
        }
        val problems = page.problems()
        if (problems.isNotEmpty()) {
            logger.error { "Skipping perform page ${page.id} ($source): ${problems.joinToString("; ")}" }
            return null
        }
        return page
    }

    companion object {
        val BUILT_IN_NAMES = listOf("decks", "master")

        /** Shared instance backed by the real `library/perform_pages/` directory. */
        val default: PerfPageStore by lazy { PerfPageStore() }
    }
}
