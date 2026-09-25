package llm.slop.liquidlsd.presets

import io.mockk.mockk
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FxOpsTest {
    private val mixer = mockk<Mixer>(relaxed = true)

    @AfterTest
    fun drain() = FxOps.drainOnGlThread(mixer)

    @Test
    fun opsPostedFromAnotherThreadOnlyApplyWhenDrained() {
        val chain = FxChain("Deck A FX")
        chain.setSlotLinked(1, true)
        val io = Executors.newSingleThreadExecutor()
        try {
            // Mimics an async file load completing on the preset IO thread.
            CompletableFuture.runAsync({
                FxOps.applyChain(chain, FXChainDto(name = "Loaded", slotSuperKnobLink = listOf(true, false, true)))
            }, io).get(5, TimeUnit.SECONDS)
        } finally {
            io.shutdown()
        }

        assertEquals("", chain.name, "Nothing may touch the chain off the GL thread")
        assertEquals(1, FxOps.pendingCount)

        FxOps.drainOnGlThread(mixer)

        assertEquals("Loaded", chain.name)
        assertFalse(chain.slotSuperKnobLink[1])
        assertEquals(0, FxOps.pendingCount)
    }
}
