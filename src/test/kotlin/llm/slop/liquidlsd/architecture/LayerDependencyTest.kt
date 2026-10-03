package llm.slop.liquidlsd.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Architecture guard: ui depends on midi/ and control/ through interfaces, never the reverse. */
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
}
