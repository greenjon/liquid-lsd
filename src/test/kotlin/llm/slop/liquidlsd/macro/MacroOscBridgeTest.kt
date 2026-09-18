package llm.slop.liquidlsd.macro

import kotlin.test.*

class MacroOscBridgeTest {

    @BeforeTest
    fun setUp() {
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
    }

    @AfterTest
    fun tearDown() {
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
    }

    @Test
    fun testHandleOscKnobMessage() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob1 = bank.knobs[0]
        knob1.value = 0f

        var receivedAddress: String? = null
        var receivedValue: Float? = null

        val listener = MacroOscBridge.MacroFeedbackListener { address, value ->
            receivedAddress = address
            receivedValue = value
        }
        MacroOscBridge.addListener(listener)

        try {
            val handled = MacroOscBridge.handleOscMessage("/macro/deckA/knob/1", 0.72f)
            assertTrue(handled)
            assertEquals(0.72f, knob1.value, absoluteTolerance = 1e-4f)
            assertEquals("/macro/deckA/knob/1", receivedAddress)
            assertEquals(0.72f, receivedValue)
        } finally {
            MacroOscBridge.removeListener(listener)
        }
    }

    @Test
    fun testUnrecognizedAddressReturnsFalse() {
        val handled = MacroOscBridge.handleOscMessage("/unrecognized/address", 1.0f)
        assertFalse(handled)
    }
}
