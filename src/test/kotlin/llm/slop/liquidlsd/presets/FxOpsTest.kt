package llm.slop.liquidlsd.presets

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.inactiveDeck
import llm.slop.liquidlsd.rendering.liveDeck
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
import kotlin.test.assertTrue

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

    @Test
    fun unusableFxItemsToastInsteadOfFailingSilently() {
        val session = mockk<llm.slop.liquidlsd.SessionContext>(relaxed = true)
        FxOps.applyItem(session, java.io.File("notes.txt"), FxChain("Deck A FX"))
        assertTrue(llm.slop.liquidlsd.ui.ToastOverlay.active()?.contains("notes.txt") == true)
    }

    @Test
    fun firstVacantSlotIsNullWhenTheChainIsFull() {
        val chain = FxChain("Deck A FX")
        assertEquals(0, FxOps.firstVacantSlot(chain))
        for (i in chain.slots.indices) chain.slots[i] = mockk(relaxed = true)
        assertEquals(null, FxOps.firstVacantSlot(chain))
        chain.slots[1] = null
        assertEquals(1, FxOps.firstVacantSlot(chain))
    }

    @Test
    fun dropAssetWithoutASlotRefusesAFullChainAndUnknownFiles() {
        val chain = FxChain("Deck A FX")
        for (i in chain.slots.indices) chain.slots[i] = mockk(relaxed = true)
        val session = mockk<llm.slop.liquidlsd.SessionContext>(relaxed = true)
        assertFalse(FxOps.dropAsset(session, java.io.File("x.lsdfx"), chain))
        assertFalse(FxOps.dropAsset(session, java.io.File("x.txt"), chain, 0))
    }

    @Test
    fun liveAndInactiveDecksUseOneTieBreakAtZero() {
        val mixer = mockk<Mixer>(relaxed = true)
        val deckA = mockk<llm.slop.liquidlsd.rendering.Deck>(relaxed = true)
        val deckB = mockk<llm.slop.liquidlsd.rendering.Deck>(relaxed = true)
        io.mockk.every { mixer.deckA } returns deckA
        io.mockk.every { mixer.deckB } returns deckB
        fun at(v: Float) { io.mockk.every { mixer.crossfade.value } returns v }
        at(-1f); assertEquals(mixer.deckA, mixer.liveDeck); assertEquals(mixer.deckB, mixer.inactiveDeck)
        at(0f);  assertEquals(mixer.deckA, mixer.liveDeck); assertEquals(mixer.deckB, mixer.inactiveDeck)
        at(1f);  assertEquals(mixer.deckB, mixer.liveDeck); assertEquals(mixer.deckA, mixer.inactiveDeck)
    }

    @Test
    fun anUndoableChainChangePushesARestoreAndAPlainOneDoesNot() {
        FxOps.drainOnGlThread(mixer)
        val chain = FxChain("Deck A FX")
        chain.name = "Before"
        val restores = mutableListOf<() -> Unit>()
        FxOps.undoSink = { restores += it }
        try {
            FxOps.applyChain(chain, FXChainDto(name = "Loaded"))
            FxOps.drainOnGlThread(mixer)
            assertEquals("Loaded", chain.name)
            assertTrue(restores.isEmpty(), "queue/macro-style changes must not touch the undo stack")

            FxOps.applyChain(chain, FXChainDto(name = "Picked"), undoable = true)
            FxOps.drainOnGlThread(mixer)
            assertEquals("Picked", chain.name)
            assertEquals(1, restores.size)

            restores.single().invoke()
            FxOps.drainOnGlThread(mixer)
            assertEquals("Loaded", chain.name)
            assertEquals(1, restores.size, "the restore itself is not undoable")
        } finally {
            FxOps.undoSink = null
        }
    }

    @Test
    fun anUndoableSlotClearRestoresTheEmptySlotState() {
        val chain = FxChain("Deck A FX")
        val restores = mutableListOf<() -> Unit>()
        FxOps.undoSink = { restores += it }
        try {
            FxOps.clearSlot(chain, 1, undoable = true) // already empty
            FxOps.drainOnGlThread(mixer)
            assertEquals(1, restores.size)
            restores.single().invoke()
            FxOps.drainOnGlThread(mixer)
            assertEquals(null, chain.slots[1])
        } finally {
            FxOps.undoSink = null
        }
    }
}
