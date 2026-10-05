package llm.slop.liquidlsd.control

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Finds controller profiles: built-ins shipped in the jar (`controllers/<name>.json`) and user
 * profiles in `library/controllers/`. A user profile with the same id replaces the built-in, so a
 * copy of a built-in can be edited in place. Profiles with structural problems are skipped.
 */
class ControllerProfileStore(
    userDir: File = File("library/controllers"),
    builtInNames: List<String> = BUILT_IN_NAMES
) {
    private val library = UserJsonLibrary(
        userDir, builtInNames,
        UserJsonLibrary.Spec<ControllerProfile, CompiledController>(
            kind = "controller profile", noun = "profile", resourceDir = "/controllers",
            resourceAnchor = ControllerProfileStore::class.java,
            json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true },
            serializer = ControllerProfile.serializer(),
            currentVersion = ControllerProfile.CURRENT_SCHEMA_VERSION,
            idOf = { it.profile.id },
            versionOf = { it.profile.version },
            toSource = { it.profile },
            stamped = { it.copy(version = ControllerProfile.CURRENT_SCHEMA_VERSION) },
            decodedVersion = { it.version },
            migrate = { ControllerProfile.migrate(it) },
            idOfSource = { it.id },
            compile = { p -> p.compile().let { c -> if (c.problems.isEmpty()) c to emptyList() else null to c.problems } },
            saveProblems = { it.compile().problems },
            // User profiles first, so a user's own profile wins when two match the same device.
            arrange = { builtIn, user -> user.values + builtIn.values.filter { it.profile.id !in user } }
        )
    )

    /** Where a profile in [all] comes from. */
    enum class Source { BUILT_IN, USER, USER_OVERRIDE }

    /** A user file that was skipped, with the reason, so the UI can show it. */
    data class Rejected(val file: File, val problems: List<String>)

    fun reload() = library.reload()

    fun all(): List<CompiledController> = library.all()

    /** User files that failed to parse or validate; they are not in [all]. */
    fun rejected(): List<Rejected> = library.rejected().map { Rejected(it.file, it.problems) }

    /** Non-fatal notes about loaded user files (currently: written by a newer schema version). They are in [all]. */
    fun warnings(): List<Rejected> = library.warnings().map { Rejected(it.file, it.problems) }

    fun get(id: String): CompiledController? = all().firstOrNull { it.profile.id == id }

    fun sourceOf(id: String): Source? = library.sourceOf(id)?.let { Source.valueOf(it.name) }

    /** The first profile (user profiles first) whose match strings fit [deviceName]. */
    fun matchFor(deviceName: String): CompiledController? =
        all().firstOrNull { it.profile.matches(deviceName) }

    /**
     * Writes the built-in profile [id] to `<userDir>/<id>.json`; it then overrides the built-in.
     * Returns an error message, or null on success.
     */
    fun copyBuiltInToUser(id: String): String? = library.copyBuiltInToUser(id)

    /**
     * Validates and writes [profile] to the user directory (replacing the file that holds the same id,
     * or `<id>.json`). Returns the problems, empty on success.
     */
    fun saveUser(profile: ControllerProfile): List<String> = library.saveUser(profile)

    /** Deletes the user file for [id]; a built-in with that id becomes active again. */
    fun deleteUser(id: String): Boolean = library.deleteUser(id)

    companion object {
        val BUILT_IN_NAMES = listOf("midi-fighter-twister", "midi-fighter-twister-6btn")

        /** Shared instance backed by the real `library/controllers/` directory. */
        val default: ControllerProfileStore by lazy { ControllerProfileStore() }

        fun matchFor(deviceName: String): ControllerProfile? = default.matchFor(deviceName)?.profile
    }
}
