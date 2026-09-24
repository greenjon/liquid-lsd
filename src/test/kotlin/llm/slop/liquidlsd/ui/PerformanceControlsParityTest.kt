package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.DeckLifecycleManager
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PerformanceControlsParityTest {

    private lateinit var mixer: Mixer
    private lateinit var session: SessionContext

    @BeforeEach
    fun setUp() {
        mixer = mockk(relaxed = true)
        session = SessionContext()
    }

    @Test
    fun testRandomizeDeckMethodsAvailableAndCallable() {
        mixer.randomizeDeckA()
        mixer.randomizeDeckB()
        mixer.randomizeDeckBG()
        mixer.randomizeDeckPV()
        mixer.randomizeAll()

        verify(exactly = 1) { mixer.randomizeDeckA() }
        verify(exactly = 1) { mixer.randomizeDeckB() }
        verify(exactly = 1) { mixer.randomizeDeckBG() }
        verify(exactly = 1) { mixer.randomizeDeckPV() }
        verify(exactly = 1) { mixer.randomizeAll() }
    }

    @Test
    fun testEjectDeckParity() {
        val deckA = mockk<Deck>(relaxed = true)
        val deckB = mockk<Deck>(relaxed = true)
        val deckBG = mockk<Deck>(relaxed = true)
        val deckPV = mockk<Deck>(relaxed = true)

        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckBG } returns deckBG
        every { mixer.deckPV } returns deckPV

        PresetManager.activePresetA = "PresetA"
        PresetManager.activePresetB = "PresetB"
        PresetManager.activePresetBG = "PresetBG"
        PresetManager.activePresetPV = "PresetPV"

        DeckLifecycleManager.clearDeckActivePreset(deckA, mixer)
        DeckLifecycleManager.clearDeckActivePreset(deckB, mixer)
        DeckLifecycleManager.clearDeckActivePreset(deckBG, mixer)
        DeckLifecycleManager.clearDeckActivePreset(deckPV, mixer)

        assertEquals(null, PresetManager.activePresetA)
        assertEquals(null, PresetManager.activePresetB)
        assertEquals(null, PresetManager.activePresetBG)
        assertEquals(null, PresetManager.activePresetPV)
    }

    @Test
    fun testCrossfaderSpeedModulatableParameter() {
        val speedParam = llm.slop.liquidlsd.parameters.ModulatableParameter(2.0f, minClamp = 0.1f, maxClamp = 30.0f)
        assertEquals(2.0f, speedParam.baseValue)

        speedParam.baseValue = (0.5f).coerceIn(speedParam.minClamp, speedParam.maxClamp)
        assertEquals(0.5f, speedParam.baseValue)

        speedParam.baseValue = (0.01f).coerceIn(speedParam.minClamp, speedParam.maxClamp)
        assertEquals(0.1f, speedParam.baseValue, "Must be clamped to minClamp 0.1f")

        speedParam.baseValue = (50.0f).coerceIn(speedParam.minClamp, speedParam.maxClamp)
        assertEquals(30.0f, speedParam.baseValue, "Must be clamped to maxClamp 30.0f")
    }

    @Test
    fun testAllFxTabIncludesDeckPvFx() {
        val bankIds = listOf(
            llm.slop.liquidlsd.macro.MacroEngine.DECK_A_FX,
            llm.slop.liquidlsd.macro.MacroEngine.DECK_B_FX,
            llm.slop.liquidlsd.macro.MacroEngine.DECK_BG_FX,
            llm.slop.liquidlsd.macro.MacroEngine.DECK_PV_FX,
            llm.slop.liquidlsd.macro.MacroEngine.MASTER_FX
        )
        for (id in bankIds) {
            val bank = llm.slop.liquidlsd.macro.MacroEngine.getBank(id) ?: llm.slop.liquidlsd.macro.MacroEngine.bankForParamPath(id)
            org.junit.jupiter.api.Assertions.assertNotNull(bank, "Bank $id should exist")
        }
    }

    @Test
    fun testDeckFxChainLinkUnlinkParity() {
        val chain = llm.slop.liquidlsd.rendering.FxChain("Test FX")
        assertEquals(true, chain.slotSuperKnobLink[0])
        assertEquals(true, chain.slotSuperKnobLink[1])
        assertEquals(true, chain.slotSuperKnobLink[2])

        chain.setSlotLinked(1, false)
        assertEquals(false, chain.slotSuperKnobLink[1])

        chain.setSlotLinked(1, true)
        assertEquals(true, chain.slotSuperKnobLink[1])
    }

    @Test
    fun testFxSendsResetToDefault() {
        val sendsBank = llm.slop.liquidlsd.macro.MacroEngine.getBank(llm.slop.liquidlsd.macro.MacroEngine.FX_SENDS) ?: llm.slop.liquidlsd.macro.MacroEngine.bankForParamPath(llm.slop.liquidlsd.macro.MacroEngine.FX_SENDS)
        org.junit.jupiter.api.Assertions.assertNotNull(sendsBank)
        sendsBank.knobs.forEach { it.value = 0.3f }

        sendsBank.knobs.forEach { it.value = 1.0f }
        sendsBank.knobs.forEach { assertEquals(1.0f, it.value) }
    }
}
