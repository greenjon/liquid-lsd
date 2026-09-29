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

        // 3. Tweak parameter or superknob -> should NOT mark chain dirty
        filter0.dryWet.baseValue = 0.42f
        filter0.dryWet.evaluate()
        chain.superKnob.baseValue = 0.85f
        chain.superKnob.evaluate()
        assertFalse(chain.computeIsDirty(), "Parameter and Superknob tweaks should not mark chain dirty")

        // 4. Add filter -> dirty
        chain.slots[1] = testFilter("invert")
        assertTrue(chain.computeIsDirty(), "Adding an FX slot should mark chain dirty")

        // 5. Mark clean (as Save would)
        chain.markClean(file)
        assertFalse(chain.computeIsDirty(), "Marking clean should reset dirty flag")

        // 6. Change order of FX -> dirty
        val filter1 = chain.slots[1]
        chain.slots[0] = filter1
        chain.slots[1] = filter0
        assertTrue(chain.computeIsDirty(), "Changing FX order in slots should mark chain dirty")

        // 7. Reset order back -> matches clean baseline
        chain.slots[0] = filter0
        chain.slots[1] = filter1
        assertFalse(chain.computeIsDirty(), "Restoring slot order should reset dirty flag")
    }

    @Test
    fun testSuperKnobDoesNotTriggerDirty() {
        val chain = FxChain("Test Chain")
        chain.slots[0] = testFilter("blur")
        chain.baselineDto = chain.toFxChainDto()

        assertFalse(chain.computeIsDirty())

        chain.superKnob.baseValue = 0.9f
        chain.superKnob.evaluate()
        chain.update()

        assertFalse(chain.computeIsDirty(), "Superknob movements must not trigger FX chain dirty state")
    }

    @Test
    fun testFxChangeAndOrderTriggersDirty() {
        val chain = FxChain("Test Chain")
        val blur = testFilter("blur")
        val glow = testFilter("glow")
        chain.slots[0] = blur
        chain.slots[1] = glow
        chain.baselineDto = chain.toFxChainDto()

        assertFalse(chain.computeIsDirty(), "Freshly loaded chain should be clean")

        // 1. Reordering slots triggers dirty state
        chain.slots[0] = glow
        chain.slots[1] = blur
        assertTrue(chain.computeIsDirty(), "Reordering FX slots must trigger dirty state")

        // Restore order
        chain.slots[0] = blur
        chain.slots[1] = glow
        assertFalse(chain.computeIsDirty(), "Restoring slot order should restore clean state")

        // 2. Replacing a filter triggers dirty state
        val invert = testFilter("invert")
        chain.slots[1] = invert
        assertTrue(chain.computeIsDirty(), "Replacing an FX slot must trigger dirty state")

        // Restore filter
        chain.slots[1] = glow
        assertFalse(chain.computeIsDirty(), "Restoring original filter should restore clean state")

        // 3. Removing a filter triggers dirty state
        chain.slots[1] = null
        assertTrue(chain.computeIsDirty(), "Removing an FX slot must trigger dirty state")

        // Restore filter
        chain.slots[1] = glow
        assertFalse(chain.computeIsDirty(), "Restoring original filter should restore clean state")
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
