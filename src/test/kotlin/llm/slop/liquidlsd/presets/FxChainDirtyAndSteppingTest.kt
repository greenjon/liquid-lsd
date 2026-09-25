package llm.slop.liquidlsd.presets

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FxChainDirtyAndSteppingTest {

    private fun testFilter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader)
    }

    @Test
    fun testDirtyTrackingLoadTweakSaveRevert(@TempDir tempDir: Path) {
        val chain = FxChain("Test Chain")
        val file = tempDir.resolve("my_chain.lsdfxchain").toFile()

        // 1. Initial empty chain is not dirty
        assertFalse(chain.computeIsDirty())

        // 2. Put a filter and capture DTO as baseline
        val filter0 = testFilter("blur")
        chain.slots[0] = filter0
        val baselineDto = chain.toFxChainDto("My Chain")
        chain.baselineDto = baselineDto
        chain.sourceFile = file

        assertFalse(chain.computeIsDirty(), "Chain freshly loaded from baseline should be clean")
        assertEquals(file, chain.sourceFile)
        assertEquals(baselineDto, chain.baselineDto)

        // 3. Tweak parameter -> dirty
        filter0.dryWet.baseValue = 0.42f
        filter0.dryWet.evaluate()
        assertTrue(chain.computeIsDirty(), "Tweak should mark chain dirty")

        // 4. Mark clean (as Save would)
        chain.markClean(file)
        assertFalse(chain.computeIsDirty(), "Marking clean should reset dirty flag")

        // 5. Tweak again -> dirty
        chain.slots[1] = testFilter("invert")
        assertTrue(chain.computeIsDirty())

        // 6. Reset tweak back -> matches clean baseline
        chain.slots[1] = null
        filter0.dryWet.baseValue = 0f
        filter0.dryWet.evaluate()
        chain.markClean(file)
        assertFalse(chain.computeIsDirty(), "Clean state matches baseline")
    }

    @Test
    fun testNewChainSemantics() {
        val chain = FxChain("Test Chain")
        chain.slots[0] = testFilter("blur")
        chain.sourceFile = File("some/path.lsdfxchain")
        chain.baselineDto = chain.toFxChainDto()

        chain.clearFxSlot(0)
        chain.name = "Untitled"
        chain.sourceFile = null
        chain.baselineDto = null

        assertEquals("Untitled", chain.name)
        assertNull(chain.sourceFile)
        assertNull(chain.baselineDto)
        assertFalse(chain.computeIsDirty(), "Empty Untitled chain has no edits")
    }

    @Test
    fun testFolderSteppingLogic(@TempDir tempDir: Path) {
        val fileA = tempDir.resolve("chain_a.lsdfxchain").toFile().apply { writeText("{}") }
        val fileB = tempDir.resolve("chain_b.lsdfxchain").toFile().apply { writeText("{}") }
        val fileC = tempDir.resolve("chain_c.lsdfxchain").toFile().apply { writeText("{}") }

        val files = listOf(fileA, fileB, fileC).sortedBy { it.name.lowercase() }

        // Step forward from A -> B -> C -> A
        fun nextIndex(curr: File?, dir: Int): Int {
            val idx = files.indexOfFirst { it.absolutePath == curr?.absolutePath }
            return if (idx < 0) (if (dir >= 0) 0 else files.lastIndex) else Math.floorMod(idx + dir, files.size)
        }

        assertEquals(1, nextIndex(fileA, 1)) // B
        assertEquals(2, nextIndex(fileB, 1)) // C
        assertEquals(0, nextIndex(fileC, 1)) // A (wraparound)

        // Step backward from A -> C -> B
        assertEquals(2, nextIndex(fileA, -1)) // C (wraparound)
        assertEquals(1, nextIndex(fileC, -1)) // B
        assertEquals(0, nextIndex(fileB, -1)) // A
    }
}
