package llm.slop.liquidlsd.macro

import kotlin.test.*

class MacroOscBridgeTest {

    @BeforeTest
    fun setUp() {
        MacroEngine.registerBank(null, MacroBank())
    }

    @AfterTest
    fun tearDown() {
        MacroEngine.registerBank(null, MacroBank())
    }

    @Test
    fun testHandleOscKnobMessage() {
        val bank = MacroEngine.globalBank()
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
            val handled = MacroOscBridge.handleOscMessage("/macro/knob/1", 0.72f)
            assertTrue(handled)
            assertEquals(0.72f, knob1.value, absoluteTolerance = 1e-4f)
            assertEquals("/macro/knob/1", receivedAddress)
            assertEquals(0.72f, receivedValue)
        } finally {
            MacroOscBridge.removeListener(listener)
        }
    }

    @Test
    fun testHandleOscSwitchMessage() {
        val bank = MacroEngine.globalBank()
        val switch2 = bank.switches[1]
        switch2.switchBehavior = SwitchBehavior.MOMENTARY
        switch2.value = 0f

        // High (> 0.5f) -> press
        val handledPress = MacroOscBridge.handleOscMessage("/macro/switch/2", 1.0f)
        assertTrue(handledPress)
        assertEquals(1.0f, switch2.value)

        // Low (<= 0.5f) -> release
        val handledRelease = MacroOscBridge.handleOscMessage("/macro/switch/2", 0.0f)
        assertTrue(handledRelease)
        assertEquals(0.0f, switch2.value)
    }

    @Test
    fun testUnrecognizedAddressReturnsFalse() {
        val handled = MacroOscBridge.handleOscMessage("/unrecognized/address", 1.0f)
        assertFalse(handled)
    }
}
