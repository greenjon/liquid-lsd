package llm.slop.liquidlsd.control

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

/**
 * A directory of user JSON files layered over built-ins shipped as jar resources: same-id override, rejection and
 * newer-schema reporting, copy-built-in-to-user, validated atomic save and delete, with a cached immutable snapshot
 * that every write invalidates. [ControllerProfileStore] and `ui.PerfPageStore` delegate to it and keep only
 * their domain parts (what a file means, how it is validated, in which order the results are shown).
 *
 * [S] is the serialised form of a file, [T] what the app uses once it passed validation (they may be the same type).
 */
class UserJsonLibrary<S, T>(
    private val userDir: File,
    private val builtInNames: List<String>,
    private val spec: Spec<S, T>
) {
    /** Where an entry comes from. */
    enum class Source { BUILT_IN, USER, USER_OVERRIDE }

    /** A user file that was skipped (or, for warnings, loaded with a note), with the reasons. */
    data class Rejected(val file: File, val problems: List<String>)

    class Spec<S, T>(
        /** For messages and logs, e.g. "controller profile". */
        val kind: String,
        /** Short noun for user-facing messages, e.g. "profile". */
        val noun: String,
        /** Classpath directory holding the built-ins, e.g. "/controllers". */
        val resourceDir: String,
        val resourceAnchor: Class<*>,
        val json: Json,
        val serializer: KSerializer<S>,
        val currentVersion: Int,
        val idOf: (T) -> String,
        val versionOf: (T) -> Int,
        /** The file form of a loaded value, for copying a built-in to the user directory. */
        val toSource: (T) -> S,
        /** [S] stamped with [currentVersion], as written to disk. */
        val stamped: (S) -> S,
        val decodedVersion: (S) -> Int,
        val migrate: (S) -> S,
        val idOfSource: (S) -> String,
        /** Validates a decoded (and migrated) value: the usable value, or the problems. */
        val compile: (S) -> Pair<T?, List<String>>,
        /** Problems that stop a save, empty to allow it. */
        val saveProblems: (S) -> List<String>,
        /** Display order given the built-ins and the user entries (both keyed by id, in load order). */
        val arrange: (builtIn: Map<String, T>, user: Map<String, T>) -> List<T>,
        /** Used when [arrange] yields nothing; null means an empty result is fine. */
        val fallback: (() -> T)? = null
    )

    private val logger = KotlinLogging.logger {}

    private class Snapshot<T>(
        val items: List<T>,
        val builtIn: Map<String, T>,
        val userFiles: Map<String, File>,
        val rejected: List<Rejected>,
        val warnings: List<Rejected>,
        val newerIds: Set<String>
    )

    @Volatile
    private var cache: Snapshot<T>? = null

    fun reload() { cache = null }

    private fun snapshot(): Snapshot<T> = cache ?: load().also { cache = it }

    fun all(): List<T> = snapshot().items

    fun rejected(): List<Rejected> = snapshot().rejected

    fun warnings(): List<Rejected> = snapshot().warnings

    fun sourceOf(id: String): Source? {
        val snap = snapshot()
        val user = id in snap.userFiles
        val builtIn = id in snap.builtIn
        return when {
            user && builtIn -> Source.USER_OVERRIDE
            user -> Source.USER
            builtIn -> Source.BUILT_IN
            else -> null
        }
    }

    /** Writes the built-in [id] to `<userDir>/<id>.json`; it then overrides the built-in. Returns an error message, or null on success. */
    fun copyBuiltInToUser(id: String): String? {
        val snap = snapshot()
        val builtIn = snap.builtIn[id] ?: return "No built-in ${spec.noun} '$id'"
        if (id in snap.userFiles) return "A user ${spec.noun} '$id' already exists"
        val target = File(userDir, "$id.json")
        if (target.exists()) return "${target.path} already exists"
        return write(target, spec.toSource(builtIn))
    }

    /**
     * Validates and writes [item] to the user directory (replacing the file that holds the same id, or `<id>.json`).
     * Returns the problems, empty on success. A file written by a newer build is never overwritten.
     */
    fun saveUser(item: S): List<String> {
        val problems = spec.saveProblems(item)
        if (problems.isNotEmpty()) return problems
        val snap = snapshot()
        val id = spec.idOfSource(item)
        // Policy: refuse. Saving would write the current schema and silently drop fields this build does not know.
        if (id in snap.newerIds) {
            return listOf("${snap.userFiles[id]?.name ?: id} was written by a newer build; not overwriting it. Edit the file by hand or delete it.")
        }
        val target = snap.userFiles[id] ?: File(userDir, "$id.json")
        return listOfNotNull(write(target, item))
    }

    /** Deletes the user file for [id]; a built-in with that id becomes active again. */
    fun deleteUser(id: String): Boolean {
        val file = snapshot().userFiles[id] ?: return false
        val ok = file.delete()
        reload()
        return ok
    }

    private fun write(target: File, item: S): String? = try {
        UserJsonFiles.writeAtomic(target, spec.json.encodeToString(spec.serializer, spec.stamped(item)))
        null
    } catch (e: Exception) {
        logger.error(e) { "Could not write ${spec.kind} ${target.path}" }
        "Could not write ${target.path}: ${e.message}"
    } finally {
        reload()
    }

    private fun load(): Snapshot<T> {
        val builtIn = LinkedHashMap<String, T>()
        for (name in builtInNames) {
            val text = spec.resourceAnchor.getResourceAsStream("${spec.resourceDir}/$name.json")
                ?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                logger.error { "Built-in ${spec.kind} missing from resources: $name" }
                continue
            }
            parse(text, "built-in $name").first?.let { builtIn[spec.idOf(it)] = it }
        }
        val scan = UserJsonFiles.scan<T>(userDir, spec.idOf) { text, src -> parse(text, src) }
        val user = LinkedHashMap<String, T>()
        for (l in scan.loaded) user[l.id] = l.value
        val userFileById = scan.loaded.associate { it.id to it.file }
        val rejected = scan.rejected.map { Rejected(it.first, it.second) }
        val newer = scan.loaded.filter { spec.versionOf(it.value) > spec.currentVersion }
        val warnings = newer.mapNotNull { l ->
            UserJsonFiles.newerVersionWarning(spec.versionOf(l.value), spec.currentVersion)?.let { Rejected(l.file, listOf(it)) }
        }
        warnings.forEach { logger.warn { "${it.file.path}: ${it.problems.first()}" } }
        var items = spec.arrange(builtIn, user)
        if (items.isEmpty() && spec.fallback != null) {
            logger.error { "No ${spec.kind}s loaded; using a synthesised one" }
            items = listOf(spec.fallback.invoke())
        }
        return Snapshot(items, builtIn, userFileById, rejected, warnings, newer.map { it.id }.toSet())
    }

    private fun parse(text: String, source: String): Pair<T?, List<String>> {
        val decoded = try {
            spec.json.decodeFromString(spec.serializer, text)
        } catch (e: Exception) {
            logger.error(e) { "Could not parse ${spec.kind} ($source)" }
            return null to listOf("Not a valid ${spec.noun}: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        val migrated = if (spec.decodedVersion(decoded) < spec.currentVersion) spec.migrate(decoded) else decoded
        val (value, problems) = spec.compile(migrated)
        if (value == null || problems.isNotEmpty()) {
            logger.error { "Skipping ${spec.kind} ${spec.idOfSource(migrated)} ($source): ${problems.joinToString("; ")}" }
            return null to problems
        }
        return value to emptyList()
    }
}
