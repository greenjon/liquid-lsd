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
        val rejected: List<Rejected>
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
        val target = snapshot().userFiles[page.id] ?: File(userDir, "${page.id}.json")
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
        userDir.mkdirs()
        target.writeText(json.encodeToString(PerfPageDef.serializer(), page))
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
        val userFileById = LinkedHashMap<String, File>()
        val rejected = ArrayList<Rejected>()
        val userFiles = userDir.listFiles { _, n -> n.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        for (file in userFiles) {
            val (page, problems) = parse(file.readText(), file.path)
            if (page != null) {
                pages[page.id] = page
                userFileById[page.id] = file
            } else {
                rejected += Rejected(file, problems)
            }
        }
        if (pages.isEmpty()) {
            logger.error { "No perform pages loaded; using a synthesised DECKS page" }
            val fallback = PerfPageDef("decks", "DECKS", rows = PerfRows.DECK_TAGS.map { RowPlacement("deck.$it.srcfx") })
            pages[fallback.id] = fallback
        }
        return Snapshot(pages.values.toList(), builtIn.keys.toSet(), builtIn, userFileById, rejected)
    }

    private fun parse(text: String, source: String): Pair<PerfPageDef?, List<String>> {
        val page = try {
            json.decodeFromString<PerfPageDef>(text)
        } catch (e: Exception) {
            logger.error(e) { "Could not parse perform page ($source)" }
            return null to listOf("Not a valid page: ${e.message?.lineSequence()?.firstOrNull()}")
        }
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
