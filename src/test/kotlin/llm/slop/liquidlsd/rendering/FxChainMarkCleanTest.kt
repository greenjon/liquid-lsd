package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class FxChainMarkCleanTest {

    private fun filter(id: String) = ISFFilter(id, id, ISFHeader(), mockk<Shader>(relaxed = true))

    @Test
    fun `markClean after a save adopts the file and clears dirty until the next change`() {
        val chain = FxChain("Chain")
        chain.slots[0] = filter("fx1")
        assertTrue(chain.computeIsDirty())

        val file = File("saved.lsdfxchain")
        chain.markClean(file)
        assertFalse(chain.computeIsDirty())
        assertEquals(file, chain.sourceFile)

        chain.slots[1] = filter("fx2")
        assertTrue(chain.computeIsDirty())
    }

    @Test
    fun `baseline is whatever the chain held when markClean ran`() {
        val chain = FxChain("Chain")
        chain.slots[0] = filter("fx1")
        chain.markClean(File("a.lsdfxchain"))
        chain.slots[0] = filter("fx2")
        // A save confirmed after this edit must baseline against fx2, not the earlier fx1.
        chain.markClean(File("a.lsdfxchain"))
        assertFalse(chain.computeIsDirty())
    }
}
