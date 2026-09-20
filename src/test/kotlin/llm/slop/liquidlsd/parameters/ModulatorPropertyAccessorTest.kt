package llm.slop.liquidlsd.parameters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModulatorPropertyAccessorTest {

    @Test
    fun testGetAndSetSubdivision() {
        val mod = CvModulator("lfo", subdivision = 1.0f)
        assertEquals(1.0f, ModulatorPropertyAccessor.get(mod, "subdivision"))

        ModulatorPropertyAccessor.set(mod, "subdivision", 4.5f)
        assertEquals(4.5f, mod.subdivision)
        assertEquals(4.5f, ModulatorPropertyAccessor.get(mod, "subdivision"))
    }

    @Test
    fun testGetAndSetDepthAndDcOffset() {
        val mod = CvModulator("lfo", depth = 0.5f, dcOffset = 0.2f)
        assertEquals(0.5f, ModulatorPropertyAccessor.get(mod, "depth"))
        assertEquals(0.2f, ModulatorPropertyAccessor.get(mod, "dcOffset"))

        ModulatorPropertyAccessor.set(mod, "depth", 0.8f)
        ModulatorPropertyAccessor.set(mod, "dcOffset", -0.1f)
        assertEquals(0.8f, mod.depth)
        assertEquals(-0.1f, mod.dcOffset)
    }

    @Test
    fun testGetAndSetLfoMinMax() {
        val mod = CvModulator("lfo", depth = 0.5f, dcOffset = 0.5f)
        // Min = 0.0, Max = 1.0
        assertEquals(0.0f, ModulatorPropertyAccessor.get(mod, "lfoMin")!!, 1e-4f)
        assertEquals(1.0f, ModulatorPropertyAccessor.get(mod, "lfoMax")!!, 1e-4f)

        // Setting min to 0.2 while max is 1.0 -> dcOffset = 0.6, depth = 0.4
        ModulatorPropertyAccessor.set(mod, "lfoMin", 0.2f)
        assertEquals(0.6f, mod.dcOffset, 1e-4f)
        assertEquals(0.4f, mod.depth, 1e-4f)
    }

    @Test
    fun testUnknownPropertyReturnsNullAndDoesNotThrow() {
        val mod = CvModulator("lfo")
        assertNull(ModulatorPropertyAccessor.get(mod, "unknown_prop"))
        ModulatorPropertyAccessor.set(mod, "unknown_prop", 100f) // safe no-op
    }

    @Test
    fun testFormatPropertyLabel() {
        assertEquals("LFO 1 Speed", ModulatorPropertyAccessor.formatPropertyLabel(0, "subdivision"))
        assertEquals("LFO 1 Depth", ModulatorPropertyAccessor.formatPropertyLabel(0, "depth"))
        assertEquals("LFO 2 Morph", ModulatorPropertyAccessor.formatPropertyLabel(1, "morph"))
        assertEquals("LFO 2 Speed", ModulatorPropertyAccessor.formatPropertyLabel(1, "modSubdivision"))
        assertEquals("Mod 3 Phase", ModulatorPropertyAccessor.formatPropertyLabel(2, "phaseOffset"))
    }
}
