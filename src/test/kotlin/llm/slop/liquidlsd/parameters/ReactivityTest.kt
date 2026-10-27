package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.cv.CVRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactivityTest {
    @AfterTest
    fun reset() {
        Reactivity.amount = 1f
        CVRegistry.updatePushedValue("audio_bass", 0f)
    }

    private fun param(recipe: Boolean, op: ModulationOperator = ModulationOperator.ADD): ModulatableParameter {
        CVRegistry.updatePushedValue("audio_bass", 0.5f)
        return ModulatableParameter(0.4f, minClamp = 0f, maxClamp = 1f).also {
            it.modulators.add(CvModulator(sourceId = "audio_bass", operator = op, depth = 0.5f, recipe = recipe))
        }
    }

    @Test
    fun recipeFollowsAmount() {
        val p = param(recipe = true)
        Reactivity.amount = 1f
        assertEquals(0.4f + 0.5f * 0.5f, p.evaluate(), 1e-5f)
        Reactivity.amount = 0f
        assertEquals(0.4f, p.evaluate(), 1e-6f)
        Reactivity.amount = 2f
        assertEquals(0.4f + 0.5f * 1.0f, p.evaluate(), 1e-5f)
    }

    @Test
    fun scaleOperatorIsBaseExactAtZero() {
        val p = param(recipe = true, op = ModulationOperator.SCALE)
        Reactivity.amount = 0f
        assertEquals(0.4f, p.evaluate(), 1e-6f)
    }

    @Test
    fun handBuiltModulatorsIgnoreAmount() {
        val p = param(recipe = false)
        Reactivity.amount = 0f
        assertEquals(0.4f + 0.25f, p.evaluate(), 1e-5f)
    }

    @Test
    fun amountIsClamped() {
        Reactivity.amount = 9f
        assertEquals(Reactivity.MAX, Reactivity.amount)
        Reactivity.amount = -1f
        assertEquals(0f, Reactivity.amount)
    }

    @Test
    fun dcOffsetScalesWithAmountSoZeroIsBaseExact() {
        CVRegistry.updatePushedValue("audio_bass", 0.5f)
        val p = ModulatableParameter(0.4f, minClamp = 0f, maxClamp = 1f).also {
            it.modulators.add(CvModulator(sourceId = "audio_bass", depth = 0.2f, dcOffset = 0.1f, recipe = true))
        }
        Reactivity.amount = 1f
        assertEquals(0.4f + 0.5f * 0.2f + 0.1f, p.evaluate(), 1e-5f)
        Reactivity.amount = 2f
        assertEquals(0.4f + 0.5f * 0.4f + 0.2f, p.evaluate(), 1e-5f)
        Reactivity.amount = 0f
        assertEquals(0.4f, p.evaluate(), 1e-6f)
    }
}
