package llm.slop.liquidlsd.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.control.UserJsonFiles
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
    val rows: List<RowPlacement>,
    /** Schema version of this file; a missing field means 1. Writers emit [CURRENT_SCHEMA_VERSION]. */
    val version: Int = 1
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

        /** The schema version this build reads and writes. Bump when the format changes and add a step to [migrate]. */
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Upgrades a page read from an older schema ([PerfPageDef.version] < [CURRENT_SCHEMA_VERSION]) to the current
         * one. Identity for now: version 1 is the first versioned format. Add one `if (v < N)` step per bump.
         */
        fun migrate(page: PerfPageDef): PerfPageDef = page

        /** A page id from a display name: lowercase letters and digits joined by '-'. Empty if nothing usable. */
        fun idFromName(name: String): String = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        /** The built-in page shown first. */
        const val DEFAULT_ID = "decks"
    }
}

/** Perform tab-strip sizing. */
object PerfTabStrip {
    const val TAB_WIDTH = 68f
    const val GAP = 4f
    private const val MIN_TAB_WIDTH = 36f
    private const val FULL_WIDTH_TABS = 5

    /** Tab width for [count] tabs: [TAB_WIDTH] up to five, then shrunk so the strip never grows beyond five tabs' width. */
    fun tabWidth(count: Int): Float {
        if (count <= FULL_WIDTH_TABS) return TAB_WIDTH
        val budget = FULL_WIDTH_TABS * TAB_WIDTH + (FULL_WIDTH_TABS - 1) * GAP
        return ((budget - (count - 1) * GAP) / count).coerceAtLeast(MIN_TAB_WIDTH)
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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    /** Where a page in [all] comes from. */
    enum class Source { BUILT_IN, USER, USER_OVERRIDE }

    /** A user file that was skipped, with the reasons, so the UI can show it. */
    data class Rejected(val file: File, val problems: List<String>)

    private class Snapshot(
        val pages: List<PerfPageDef>,
        val builtInIds: Set<String>,
        val builtIn: Map<String, PerfPageDef>,
        val userFiles: Map<String, File>,
        val rejected: List<Rejected>,
        val warnings: List<Rejected>,
        val newerIds: Set<String>
    )

    @Volatile
    private var cache: Snapshot? = null

    fun reload() { cache = null }

    private fun snapshot(): Snapshot = cache ?: load().also { cache = it }

    /** Every usable page, in strip order. Never empty: if nothing loads, a minimal DECKS page is synthesised. */
    fun all(): List<PerfPageDef> = snapshot().pages

    fun get(id: String): PerfPageDef? = all().firstOrNull { it.id == id }

    fun indexOf(id: String): Int = all().indexOfFirst { it.id == id }

    /** User files that failed to parse or validate; they are not in [all]. */
    fun rejected(): List<Rejected> = snapshot().rejected

    /** Non-fatal notes about loaded user files (currently: written by a newer schema version). They are in [all]. */
    fun warnings(): List<Rejected> = snapshot().warnings

    fun sourceOf(id: String): Source? {
        val snap = snapshot()
        val user = id in snap.userFiles
        val builtIn = id in snap.builtInIds
        return when {
            user && builtIn -> Source.USER_OVERRIDE
            user -> Source.USER
            builtIn -> Source.BUILT_IN
            else -> null
        }
    }

    /** Writes the built-in page [id] to `<userDir>/<id>.json`, where it overrides the built-in. Returns an error message or null. */
    fun copyBuiltInToUser(id: String): String? {
        val snap = snapshot()
        val builtIn = snap.builtIn[id] ?: return "No built-in page '$id'"
        if (id in snap.userFiles) return "A user page '$id' already exists"
        val target = File(userDir, "$id.json")
        if (target.exists()) return "${target.path} already exists"
        return write(target, builtIn)
    }

    /** Validates and writes [page] (replacing the file that holds the same id, or `<id>.json`). Returns the problems, empty on success. */
    fun saveUser(page: PerfPageDef): List<String> {
        val problems = page.problems() + idProblems(page.id)
        if (problems.isNotEmpty()) return problems
        val snap = snapshot()
        // Policy: refuse. Saving would write the current schema and silently drop fields this build does not know.
        if (page.id in snap.newerIds) {
            return listOf("${snap.userFiles[page.id]?.name ?: page.id} was written by a newer build; not overwriting it. Edit the file by hand or delete it.")
        }
        val target = snap.userFiles[page.id] ?: File(userDir, "${page.id}.json")
        return listOfNotNull(write(target, page))
    }

    /** Deletes the user file for [id]; a built-in with that id becomes active again. */
    fun deleteUser(id: String): Boolean {
        val file = snapshot().userFiles[id] ?: return false
        val ok = file.delete()
        reload()
        return ok
    }

    private fun idProblems(id: String): List<String> =
        if (Regex("[a-z0-9][a-z0-9_-]*").matches(id)) emptyList()
        else listOf("page id '$id' must be lowercase letters, digits, '_' or '-'")

    private fun write(target: File, page: PerfPageDef): String? = try {
        UserJsonFiles.writeAtomic(target, json.encodeToString(PerfPageDef.serializer(), page.copy(version = PerfPageDef.CURRENT_SCHEMA_VERSION)))
        null
    } catch (e: Exception) {
        logger.error(e) { "Could not write perform page ${target.path}" }
        "Could not write ${target.path}: ${e.message}"
    } finally {
        reload()
    }

    private fun load(): Snapshot {
        val builtIn = LinkedHashMap<String, PerfPageDef>()
        for (name in builtInNames) {
            val text = PerfPageStore::class.java.getResourceAsStream("/perform_pages/$name.json")
                ?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                logger.error { "Built-in perform page missing from resources: $name" }
                continue
            }
            parse(text, "built-in $name").first?.let { builtIn[it.id] = it }
        }
        val pages = LinkedHashMap<String, PerfPageDef>(builtIn)
        val scan = UserJsonFiles.scan<PerfPageDef>(userDir, { it.id }) { text, src -> parse(text, src) }
        val userFileById = scan.loaded.associate { it.id to it.file }
        for (l in scan.loaded) pages[l.id] = l.value
        val rejected = scan.rejected.map { Rejected(it.first, it.second) }
        val newer = scan.loaded.filter { it.value.version > PerfPageDef.CURRENT_SCHEMA_VERSION }
        val warnings = newer.mapNotNull { l ->
            UserJsonFiles.newerVersionWarning(l.value.version, PerfPageDef.CURRENT_SCHEMA_VERSION)?.let { Rejected(l.file, listOf(it)) }
        }
        warnings.forEach { logger.warn { "${it.file.path}: ${it.problems.first()}" } }
        if (pages.isEmpty()) {
            logger.error { "No perform pages loaded; using a synthesised DECKS page" }
            val fallback = PerfPageDef("decks", "DECKS", rows = PerfRows.DECK_TAGS.map { RowPlacement("deck.$it.srcfx") })
            pages[fallback.id] = fallback
        }
        return Snapshot(pages.values.toList(), builtIn.keys.toSet(), builtIn, userFileById, rejected, warnings, newer.map { it.id }.toSet())
    }

    private fun parse(text: String, source: String): Pair<PerfPageDef?, List<String>> {
        val decoded = try {
            json.decodeFromString<PerfPageDef>(text)
        } catch (e: Exception) {
            logger.error(e) { "Could not parse perform page ($source)" }
            return null to listOf("Not a valid page: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        val page = if (decoded.version < PerfPageDef.CURRENT_SCHEMA_VERSION) PerfPageDef.migrate(decoded) else decoded
        val problems = page.problems()
        if (problems.isNotEmpty()) {
            logger.error { "Skipping perform page ${page.id} ($source): ${problems.joinToString("; ")}" }
            return null to problems
        }
        return page to emptyList()
    }

    companion object {
        val BUILT_IN_NAMES = listOf("decks", "master", "deck-ab", "deck-bgpv", "mixer")

        /** Shared instance backed by the real `library/perform_pages/` directory. */
        val default: PerfPageStore by lazy { PerfPageStore() }
    }
}
