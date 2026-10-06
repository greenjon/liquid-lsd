package llm.slop.liquidlsd.macro

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitionMacroSyncTest {

    @BeforeTest
    fun setUp() = MacroEngine.unregisterBank(MacroEngine.TRANS)

    @AfterTest
    fun tearDown() = MacroEngine.unregisterBank(MacroEngine.TRANS)

    private fun transition(vararg inputs: ISFInput): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "startImage", TYPE = "image"),
            ISFInput(NAME = "endImage", TYPE = "image"),
            ISFInput(NAME = "progress", TYPE = "float")
        ) + inputs)
        return ISFFilter("t", "t", header, mockk<Shader>(relaxed = true))
    }

    private fun float(name: String) =
        ISFInput(NAME = name, TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(2f), DEFAULT = JsonPrimitive(1f))

    private fun select(name: String) =
        ISFInput(NAME = name, TYPE = "long", MIN = JsonPrimitive(0), MAX = JsonPrimitive(2), DEFAULT = JsonPrimitive(0))

    private fun synced(vararg inputs: ISFInput): Pair<Mixer, MacroBank> {
        val mixer = mockk<Mixer>(relaxed = true)
        io.mockk.every { mixer.crossfade } returns llm.slop.liquidlsd.parameters.ModulatableParameter(-1f, minClamp = -1f, maxClamp = 1f)
        io.mockk.every { mixer.transitionFilter } returns transition(*inputs)
        TransitionMacroSync.sync(mixer)
        return mixer to MacroEngine.getBank(MacroEngine.TRANS)!!
    }

    @Test
    fun `knob 1 is the crossfader and 2-4 are the first three float params`() {
        val (_, bank) = synced(float("a"), float("b"), float("c"), float("d"))
        assertEquals(TransitionMacroSync.CROSSFADE_LABEL, bank.knobs[0].label)
        assertTrue(bank.knobs[0].bindings.isEmpty(), "crossfader is mirrored, not bound, so it stays unlocked")
        assertEquals(listOf("Mixer/Transition/a", "Mixer/Transition/b", "Mixer/Transition/c"),
            bank.knobs.drop(1).map { it.bindings.single().parameterId })
    }

    @Test
    fun `selects are skipped and spare knobs are blank`() {
        val (_, bank) = synced(select("mode"), float("amount"))
        assertEquals("Mixer/Transition/amount", bank.knobs[1].bindings.single().parameterId)
        for (k in 2..3) {
            assertEquals("—", bank.knobs[k].label)
            assertTrue(bank.knobs[k].bindings.isEmpty())
        }
    }

    @Test
    fun `crossfader knob follows the crossfader and drives it when turned`() {
        val (mixer, bank) = synced()
        assertEquals(0f, bank.knobs[0].value)
        mixer.crossfade.baseValue = 1f
        TransitionMacroSync.mirrorCrossfade(mixer)
        assertEquals(1f, bank.knobs[0].value)
        bank.knobs[0].value = 0.25f
        TransitionMacroSync.mirrorCrossfade(mixer)
        assertEquals(-0.5f, mixer.crossfade.baseValue, 1e-6f)
    }

    @Test
    fun `transition bank refuses hand-bound targets`() {
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.TRANS, "Mixer/levelA"))
        assertTrue(TransitionMacroSync.isSyncOwned(MacroEngine.TRANS))
    }
}
