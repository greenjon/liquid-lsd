package llm.slop.liquidlsd.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.control.UserJsonLibrary
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
        const val DEFAULT_ID = "ab"
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
    userDir: File = File("library/perform_pages"),
    builtInNames: List<String> = BUILT_IN_NAMES
) {
    private val library = UserJsonLibrary(
        userDir, builtInNames,
        UserJsonLibrary.Spec<PerfPageDef, PerfPageDef>(
            kind = "perform page", noun = "page", resourceDir = "/perform_pages",
            resourceAnchor = PerfPageStore::class.java,
            json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true },
            serializer = PerfPageDef.serializer(),
            currentVersion = PerfPageDef.CURRENT_SCHEMA_VERSION,
            idOf = { it.id },
            versionOf = { it.version },
            toSource = { it },
            stamped = { it.copy(version = PerfPageDef.CURRENT_SCHEMA_VERSION) },
            decodedVersion = { it.version },
            migrate = { PerfPageDef.migrate(it) },
            idOfSource = { it.id },
            compile = { p -> p.problems().let { pr -> if (pr.isEmpty()) p to emptyList() else null to pr } },
            saveProblems = { it.problems() + idProblems(it.id) },
            // A user page with a built-in's id replaces it in place; new user pages follow, in file-name order.
            arrange = { builtIn, user -> LinkedHashMap(builtIn).also { it.putAll(user) }.values.toList() },
            fallback = { PerfPageDef("ab", "A/B", rows = listOf("deck.A.src", "deck.A.fx", "deck.B.src", "deck.B.fx").map { RowPlacement(it) }) }
        )
    )

    /** Where a page in [all] comes from. */
    enum class Source { BUILT_IN, USER, USER_OVERRIDE }

    /** A user file that was skipped, with the reasons, so the UI can show it. */
    data class Rejected(val file: File, val problems: List<String>)

    fun reload() = library.reload()

    /** Every usable page, in strip order. Never empty: if nothing loads, a minimal A/B page is synthesised. */
    fun all(): List<PerfPageDef> = library.all()

    fun get(id: String): PerfPageDef? = all().firstOrNull { it.id == id }

    fun indexOf(id: String): Int = all().indexOfFirst { it.id == id }

    /** User files that failed to parse or validate; they are not in [all]. */
    fun rejected(): List<Rejected> = library.rejected().map { Rejected(it.file, it.problems) }

    /** Non-fatal notes about loaded user files (currently: written by a newer schema version). They are in [all]. */
    fun warnings(): List<Rejected> = library.warnings().map { Rejected(it.file, it.problems) }

    fun sourceOf(id: String): Source? = library.sourceOf(id)?.let { Source.valueOf(it.name) }

    /** Writes the built-in page [id] to `<userDir>/<id>.json`, where it overrides the built-in. Returns an error message or null. */
    fun copyBuiltInToUser(id: String): String? = library.copyBuiltInToUser(id)

    /** Validates and writes [page] (replacing the file that holds the same id, or `<id>.json`). Returns the problems, empty on success. */
    fun saveUser(page: PerfPageDef): List<String> = library.saveUser(page)

    /** Deletes the user file for [id]; a built-in with that id becomes active again. */
    fun deleteUser(id: String): Boolean = library.deleteUser(id)

    companion object {
        private fun idProblems(id: String): List<String> =
            if (Regex("[a-z0-9][a-z0-9_-]*").matches(id)) emptyList()
            else listOf("page id '$id' must be lowercase letters, digits, '_' or '-'")


        val BUILT_IN_NAMES = listOf("deck-ab", "deck-bgpv", "mixer", "master")

        /** Shared instance backed by the real `library/perform_pages/` directory. */
        val default: PerfPageStore by lazy { PerfPageStore() }
    }
}
