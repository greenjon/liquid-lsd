package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** The dice reach effect slots (through the deck's randomizable list) and transitions roll only their shipped ranges. */
class FxDiceTest {

    private fun float(name: String, default: Float) =
        ISFInput(NAME = name, TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(1f), DEFAULT = JsonPrimitive(default))

    private fun filter(id: String, vararg names: String) =
        ISFFilter(id, id, ISFHeader(INPUTS = names.map { float(it, 0.5f) }), mockk<Shader>(relaxed = true))

    @Test
    fun `slot list holds the enabled slots' inputs and Dry-Wets and skips disabled slots`() {
        val chain = FxChain("Chain")
        val on = filter("fx_on", "angle", "size")
        val off = filter("fx_off", "hue").apply { enabled = false }
        chain.slots[0] = on
        chain.slots[2] = off

        val params = chain.slotRandomizableParameters()
        assertTrue(on.dryWet in params)
        assertTrue(params.containsAll(on.parameters.values))
        assertFalse(off.parameters.getValue("hue") in params)
        assertFalse(off.dryWet in params)
    }

    @Test
    fun `a deck-style roll moves a ranged slot input inside its range and leaves the others alone`() {
        val chain = FxChain("Chain")
        val fx = filter("fx_roll", "angle", "size")
        chain.slots[0] = fx
        val angle = fx.parameters.getValue("angle").apply { randomizeBase = true; baseMin = 0.2f; baseMax = 0.4f }
        val size = fx.parameters.getValue("size")

        val controller = DeckMorphController(paramsProvider = { listOf(chain.dryWet) + chain.slotRandomizableParameters() })
        val seen = mutableSetOf<Float>()
        repeat(20) {
            controller.forceRandomize(Random(it))
            assertTrue(angle.baseValue in 0.2f..0.4f, "angle ${angle.baseValue} left its range")
            assertEquals(0.5f, size.baseValue)
            assertEquals(1.0f, chain.dryWet.baseValue)
            seen += angle.baseValue
        }
        assertTrue(seen.size > 1, "the roll never changed the ranged input")
    }

    @Test
    fun `transition roll touches only ranged inputs, inside their ranges, and never progress`() {
        val trans = filter("trans_roll", "progress", "spread", "softness")
        val progress = trans.parameters.getValue("progress").apply { randomizeBase = true; baseMin = 0f; baseMax = 1f; baseValue = 0.5f }
        val spread = trans.parameters.getValue("spread").apply { randomizeBase = true; baseMin = 0.1f; baseMax = 0.3f }
        val softness = trans.parameters.getValue("softness")

        repeat(20) {
            trans.randomizeTransitionInputs(Random(it))
            assertTrue(spread.baseValue in 0.1f..0.3f, "spread ${spread.baseValue} left its range")
            assertEquals(0.5f, softness.baseValue)
            assertEquals(0.5f, progress.baseValue)
        }
    }
}
