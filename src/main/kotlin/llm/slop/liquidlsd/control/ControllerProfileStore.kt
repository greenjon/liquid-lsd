package llm.slop.liquidlsd.control

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

/**
 * Finds controller profiles: built-ins shipped in the jar (`controllers/<name>.json`) and user
 * profiles in `library/controllers/`. A user profile with the same id replaces the built-in, so a
 * copy of a built-in can be edited in place. Profiles with structural problems are skipped.
 */
class ControllerProfileStore(
    private val userDir: File = File("library/controllers"),
    private val builtInNames: List<String> = BUILT_IN_NAMES
) {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    /** Where a profile in [all] comes from. */
    enum class Source { BUILT_IN, USER, USER_OVERRIDE }

    /** A user file that was skipped, with the reason, so the UI can show it. */
    data class Rejected(val file: File, val problems: List<String>)

    private class Snapshot(
        val profiles: List<CompiledController>,
        val builtIn: Map<String, CompiledController>,
        val userFiles: Map<String, File>,
        val rejected: List<Rejected>
    )

    @Volatile
    private var cache: Snapshot? = null

    fun reload() { cache = null }

    private fun snapshot(): Snapshot = cache ?: load().also { cache = it }

    fun all(): List<CompiledController> = snapshot().profiles

    /** User files that failed to parse or validate; they are not in [all]. */
    fun rejected(): List<Rejected> = snapshot().rejected

    fun get(id: String): CompiledController? = all().firstOrNull { it.profile.id == id }

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

    /** The first profile (user profiles first) whose match strings fit [deviceName]. */
    fun matchFor(deviceName: String): CompiledController? =
        all().firstOrNull { it.profile.matches(deviceName) }

    /**
     * Writes the built-in profile [id] to `<userDir>/<id>.json`; it then overrides the built-in.
     * Returns an error message, or null on success.
     */
    fun copyBuiltInToUser(id: String): String? {
        val snap = snapshot()
        val builtIn = snap.builtIn[id] ?: return "No built-in profile '$id'"
        if (id in snap.userFiles) return "A user profile '$id' already exists"
        val target = File(userDir, "$id.json")
        if (target.exists()) return "${target.path} already exists"
        return write(target, builtIn.profile)
    }

    /**
     * Validates and writes [profile] to the user directory (replacing the file that holds the same id,
     * or `<id>.json`). Returns the problems, empty on success.
     */
    fun saveUser(profile: ControllerProfile): List<String> {
        val problems = profile.compile().problems
        if (problems.isNotEmpty()) return problems
        val target = snapshot().userFiles[profile.id] ?: File(userDir, "${profile.id}.json")
        return listOfNotNull(write(target, profile))
    }

    /** Deletes the user file for [id]; a built-in with that id becomes active again. */
    fun deleteUser(id: String): Boolean {
        val file = snapshot().userFiles[id] ?: return false
        val ok = file.delete()
        reload()
        return ok
    }

    private fun write(target: File, profile: ControllerProfile): String? = try {
        UserJsonFiles.writeAtomic(target, json.encodeToString(ControllerProfile.serializer(), profile))
        null
    } catch (e: Exception) {
        logger.error(e) { "Could not write controller profile ${target.path}" }
        "Could not write ${target.path}: ${e.message}"
    } finally {
        reload()
    }

    private fun load(): Snapshot {
        val builtIn = LinkedHashMap<String, CompiledController>()
        for (name in builtInNames) {
            val text = ControllerProfileStore::class.java.getResourceAsStream("/controllers/$name.json")
                ?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                logger.error { "Built-in controller profile missing from resources: $name" }
                continue
            }
            parse(text, "built-in $name").first?.let { builtIn[it.profile.id] = it }
        }
        val scan = UserJsonFiles.scan<CompiledController>(userDir, { it.profile.id }) { text, src -> parse(text, src) }
        val user = scan.loaded.associate { it.id to it.value }
        val userFileById = scan.loaded.associate { it.id to it.file }
        val rejected = scan.rejected.map { Rejected(it.first, it.second) }
        // User profiles first, so a user's own profile wins when two match the same device.
        val profiles = user.values + builtIn.values.filter { it.profile.id !in user }
        return Snapshot(profiles, builtIn, userFileById, rejected)
    }

    private fun parse(text: String, source: String): Pair<CompiledController?, List<String>> {
        val profile = try {
            json.decodeFromString<ControllerProfile>(text)
        } catch (e: Exception) {
            logger.error(e) { "Could not parse controller profile ($source)" }
            return null to listOf("Not a valid profile: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        val compiled = profile.compile()
        if (compiled.problems.isNotEmpty()) {
            logger.error { "Skipping controller profile ${profile.id} ($source): ${compiled.problems.joinToString("; ")}" }
            return null to compiled.problems
        }
        return compiled to emptyList()
    }

    companion object {
        val BUILT_IN_NAMES = listOf("midi-fighter-twister")

        /** Shared instance backed by the real `library/controllers/` directory. */
        val default: ControllerProfileStore by lazy { ControllerProfileStore() }

        fun matchFor(deviceName: String): ControllerProfile? = default.matchFor(deviceName)?.profile
    }
}
