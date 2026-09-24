package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FxBankTest {

    @Test
    fun testDefaultState() {
        val bank = FxBank("BANK_1")

        assertEquals(3, FxBank.SLOT_COUNT)
        assertEquals(FxBank.SLOT_COUNT, bank.slots.size)
        assertTrue(bank.enabled)
        assertEquals(1.0f, bank.masterWetDry.value)
        assertEquals(0.0f, bank.masterWetDry.minClamp)
        assertEquals(1.0f, bank.masterWetDry.maxClamp)
        bank.slots.forEach { assertNull(it) }
    }

    @Test
    fun testEmptySlotsProduceNoDto() {
        val bank = FxBank("BANK_1")

        for (i in 0 until FxBank.SLOT_COUNT) {
            assertNull(bank.toFxSlotDto(i))
        }

        val chainDto = bank.toFxChainDto("Empty Bank")
        assertEquals("Empty Bank", chainDto.name)
        assertEquals(FxBank.SLOT_COUNT, chainDto.slots.size)
        chainDto.slots.forEach { assertNull(it) }
    }

    @Test
    fun testClearAndResetOnEmptyBankDoNotThrow() {
        val bank = FxBank("BANK_2")

        bank.clearFxSlot(0)
        bank.reset()
        bank.update()
        bank.dispose()

        assertTrue(bank.enabled)
    }

    @Test
    fun testApplyFxSlotOutOfRangeIsNoOp() {
        val bank = FxBank("BANK_1")

        bank.clearFxSlot(-1)
        bank.clearFxSlot(FxBank.SLOT_COUNT)

        bank.slots.forEach { assertNull(it) }
    }

    @Test
    fun testFxBankLabelsAndParameterPaths() {
        val bank1 = FxBank("FX1")
        val bank2 = FxBank("FX2")

        assertEquals("FX1", bank1.label)
        assertEquals("FX2", bank2.label)

        val paths1 = bank1.getParameterPaths(bank1.label).map { it.first }
        assertTrue(paths1.contains("FX1/DryWet"))

        val paths2 = bank2.getParameterPaths(bank2.label).map { it.first }
        assertTrue(paths2.contains("FX2/DryWet"))
    }

    @Test
    fun testMacroEngineCanonicalIdForFxBanks() {
        assertEquals(llm.slop.liquidlsd.macro.MacroEngine.MASTER_FX, llm.slop.liquidlsd.macro.MacroEngine.canonicalIdForDeckLabel("MFX"))
        // FX1/FX2 banks were removed; their labels no longer map to a dedicated macro bank.
        assertTrue(llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS.none { it == "fxBank1" || it == "fxBank2" })
    }

    @Test
    fun testThreeChainsPerBank() {
        val bank = FxBank("FX1")
        assertEquals(3, bank.chains.size)
        assertEquals(FxBank.CHAIN_COUNT, bank.chains.size)
        for (i in 0 until FxBank.CHAIN_COUNT) {
            val chain = bank.chains[i]
            assertEquals("Chain ${i + 1}", chain.label)
            assertEquals(3, chain.slots.size)
            assertTrue(chain.enabled)
            assertEquals(1.0f, chain.dryWet.value)
        }
    }

    @Test
    fun testThreeChainParameterPaths() {
        val bank = FxBank("FX1")
        val paths = bank.getParameterPaths(bank.label).map { it.first }

        assertTrue(paths.contains("FX1/DryWet"))
        assertTrue(paths.contains("FX1/C1/DryWet"))
        assertTrue(paths.contains("FX1/C2/DryWet"))
        assertTrue(paths.contains("FX1/C3/DryWet"))
    }

    @Test
    fun testFxBankDtoRoundTrip() {
        val bank = FxBank("FX1")
        bank.masterWetDry.baseValue = 0.75f
        bank.chains[0].dryWet.baseValue = 0.5f
        bank.chains[1].dryWet.baseValue = 0.25f

        val dto = bank.toFxBankDto("MyBank", listOf("custom", "psychedelic"))
        assertEquals("MyBank", dto.name)
        assertEquals(listOf("custom", "psychedelic"), dto.tags)
        assertEquals(3, dto.chains.size)
        assertEquals(0.75f, dto.masterWetDry?.baseValue)
        assertEquals(0.5f, dto.chains[0]?.dryWet?.baseValue)
        assertEquals(0.25f, dto.chains[1]?.dryWet?.baseValue)

        val targetBank = FxBank("FX2")
        targetBank.applyFxBank(dto)
        assertEquals(0.75f, targetBank.masterWetDry.baseValue)
        assertEquals(0.5f, targetBank.chains[0].dryWet.baseValue)
        assertEquals(0.25f, targetBank.chains[1].dryWet.baseValue)
    }

    @Test
    fun testActiveChainIndexDefaultsToZeroAndCoerces() {
        val bank = FxBank("FX1")
        assertEquals(0, bank.activeChainIndex)
        assertEquals(bank.chains[0], bank.activeChain)

        bank.activeChainIndex = 1
        assertEquals(1, bank.activeChainIndex)
        assertEquals(bank.chains[1], bank.activeChain)

        bank.activeChainIndex = 99
        assertEquals(2, bank.activeChainIndex)

        bank.activeChainIndex = -5
        assertEquals(0, bank.activeChainIndex)
    }

    @Test
    fun testBackwardCompatAccessorsFollowActiveChainIndex() {
        val bank = FxBank("FX1")
        bank.activeChainIndex = 1

        assertEquals(bank.chains[1].slots, bank.slots)

        val dto = bank.toFxChainDto("Chain2Name")
        assertEquals(bank.chains[1].toFxChainDto("Chain2Name").name, dto.name)

        bank.applyFxChain(FXChainDto(name = "applied", dryWet = null, slots = emptyList()))
        assertEquals("applied", bank.chains[1].name)
        assertEquals("", bank.chains[0].name)
    }

    @Test
    fun testActiveChainIndexRoundTripsThroughDto() {
        val bank = FxBank("FX1")
        bank.activeChainIndex = 2

        val dto = bank.toFxBankDto("MyBank")
        assertEquals(2, dto.activeChainIndex)

        val targetBank = FxBank("FX2")
        targetBank.applyFxBank(dto)
        assertEquals(2, targetBank.activeChainIndex)
    }

    @Test
    fun testResetRestoresActiveChainIndexToZero() {
        val bank = FxBank("FX1")
        bank.activeChainIndex = 2
        bank.reset()
        assertEquals(0, bank.activeChainIndex)
    }

    @Test
    fun testFxChainDefaultStateAndDto() {
        val chain = FxChain("TestChain")
        assertEquals("TestChain", chain.label)
        assertEquals(3, chain.slots.size)
        assertTrue(chain.enabled)
        assertEquals(1.0f, chain.dryWet.baseValue)

        chain.dryWet.baseValue = 0.42f
        val dto = chain.toFxChainDto("MyChain", listOf("reverb"))
        assertEquals("MyChain", dto.name)
        assertEquals(0.42f, dto.dryWet?.baseValue)
        assertEquals(3, dto.slots.size)

        val newChain = FxChain("NewChain")
        newChain.applyFxChain(dto)
        assertEquals(0.42f, newChain.dryWet.baseValue)
    }
}

