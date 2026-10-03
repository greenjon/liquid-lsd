package llm.slop.liquidlsd.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Architecture guard: ui depends on midi/ and control/ through interfaces, never the reverse; midi/ may not grow new control/ dependencies. */
class LayerDependencyTest {
    private val root = File("src/main/kotlin/llm/slop/liquidlsd")

    /** File name -> reason, for any intentional import of ui/. Currently none. */
    private val allowed = emptyMap<String, String>()

    private fun violations(dir: String): List<String> =
        File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in allowed }
            .filter { f -> f.readLines().any { it.trim().startsWith("import llm.slop.liquidlsd.ui") || "llm.slop.liquidlsd.ui." in it } }
            .map { it.path }.toList()

    @Test
    fun midiDoesNotDependOnUi() {
        val v = violations("midi")
        assertTrue(v.isEmpty(), "midi/ must not reference ui/: $v")
    }

    @Test
    fun controlDoesNotDependOnUi() {
        val v = violations("control")
        assertTrue(v.isEmpty(), "control/ must not reference ui/: $v")
    }

    /**
     * File name -> reason, for the existing midi -> control references. These are the dispatch plumbing
     * (command registry, controller runtime, knob/nav surfaces) that `MidiMappingManager` hosts and the
     * output-port sink; control -> midi also exists, so a full split means moving them. New files may not add to it.
     */
    private val midiControlAllowed = mapOf(
        "MidiMappingManager.kt" to "hosts CommandRegistry/ControllerManager and builds CommandContext",
        "MidiOutputPorts.kt" to "implements control.MidiSink and uses CcQueue"
    )

    @Test
    fun midiControlDependenciesAreOnlyTheKnownOnes() {
        val v = File(root, "midi").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in midiControlAllowed }
            .filter { f -> f.readLines().any { it.trim().startsWith("import llm.slop.liquidlsd.control") || "llm.slop.liquidlsd.control." in it } }
            .map { it.path }.toList()
        assertTrue(v.isEmpty(), "midi/ must not reference control/ outside $midiControlAllowed: $v")
    }

    @Test
    fun midiDoesNotUseControllerProfileStorage() {
        val storage = listOf("ControllerProfileStore", "ProfileBindingEdit", "ControllerProfile\\b", "UserJson").map { Regex(it) }
        val v = File(root, "midi").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readLines().any { line -> storage.any { it.containsMatchIn(line) } } }
            .map { it.path }.toList()
        assertTrue(v.isEmpty(), "midi/ must reach controller profiles through ProfileLearner, not the store: $v")
    }
}
