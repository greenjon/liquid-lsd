package llm.slop.liquidlsd.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalCrossfadeCommandsTest {
    private class FakeCrossfade(var base: Float = -1f) : CrossfadeControl {
        override var isAutoFading = false
        override var targetCrossfade = -1f
        override val crossfadeBase get() = base
        var takeovers = 0
        var mutes = 0
        override fun setCrossfade(value: Float) { base = value }
        override fun onCrossfadeManualTakeover() { takeovers++; isAutoFading = false }
        override fun muteCrossfadeNonMidiCv() { mutes++ }
    }

    private val registry = CommandRegistry().also { GlobalCommands.registerAll(it) }
    private val fake = FakeCrossfade()
    private val ctx = CommandContext(fake)

    private fun press(id: String) {
        registry.execute(id, CommandInput.Press.DOWN, ctx)
        registry.execute(id, CommandInput.Press.UP, ctx)
    }

    @Test
    fun snapCommandsTakeOverAndSetTheCrossfader() {
        press(GlobalCommands.SNAP_B)
        assertEquals(1f, fake.base)
        press(GlobalCommands.SNAP_A)
        assertEquals(-1f, fake.base)
        assertEquals(2, fake.takeovers)
    }

    @Test
    fun autoCrossfadeTargetsTheOppositeDeckThenCancels() {
        press(GlobalCommands.AUTO_CROSSFADE)
        assertTrue(fake.isAutoFading)
        assertEquals(1f, fake.targetCrossfade)
        assertEquals(1, fake.mutes)
        press(GlobalCommands.AUTO_CROSSFADE)
        assertFalse(fake.isAutoFading)
        assertEquals(1, fake.takeovers)
    }
}
