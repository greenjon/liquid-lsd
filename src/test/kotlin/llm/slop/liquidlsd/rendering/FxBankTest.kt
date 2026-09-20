package llm.slop.liquidlsd.rendering

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
}
