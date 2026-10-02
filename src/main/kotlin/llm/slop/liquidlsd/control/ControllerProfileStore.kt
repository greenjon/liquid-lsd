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

    @Volatile
    private var cache: List<CompiledController>? = null

    fun reload() { cache = null }

    fun all(): List<CompiledController> = cache ?: load().also { cache = it }

    fun get(id: String): CompiledController? = all().firstOrNull { it.profile.id == id }

    /** The first profile (user profiles first) whose match strings fit [deviceName]. */
    fun matchFor(deviceName: String): CompiledController? =
        all().firstOrNull { it.profile.matches(deviceName) }

    private fun load(): List<CompiledController> {
        val builtIn = LinkedHashMap<String, CompiledController>()
        for (name in builtInNames) {
            val text = ControllerProfileStore::class.java.getResourceAsStream("/controllers/$name.json")
                ?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                logger.error { "Built-in controller profile missing from resources: $name" }
                continue
            }
            parse(text, "built-in $name")?.let { builtIn[it.profile.id] = it }
        }
        val user = LinkedHashMap<String, CompiledController>()
        val userFiles = userDir.listFiles { _, n -> n.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        for (file in userFiles) {
            parse(file.readText(), file.path)?.let { user[it.profile.id] = it }
        }
        // User profiles first, so a user's own profile wins when two match the same device.
        return user.values + builtIn.values.filter { it.profile.id !in user }
    }

    private fun parse(text: String, source: String): CompiledController? {
        val profile = try {
            json.decodeFromString<ControllerProfile>(text)
        } catch (e: Exception) {
            logger.error(e) { "Could not parse controller profile ($source)" }
            return null
        }
        val compiled = profile.compile()
        if (compiled.problems.isNotEmpty()) {
            logger.error { "Skipping controller profile ${profile.id} ($source): ${compiled.problems.joinToString("; ")}" }
            return null
        }
        return compiled
    }

    companion object {
        val BUILT_IN_NAMES = listOf("midi-fighter-twister")

        /** Shared instance backed by the real `library/controllers/` directory. */
        val default: ControllerProfileStore by lazy { ControllerProfileStore() }

        fun matchFor(deviceName: String): ControllerProfile? = default.matchFor(deviceName)?.profile
    }
}
