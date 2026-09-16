package llm.slop.liquidlsd.macro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacroControlSwitchTest {

    @Test
    fun testToggleAlternatesOnRepeatedPress() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.TOGGLE)
        assertEquals(0f, control.value)

        control.onPress()
        assertEquals(1f, control.value)

        control.onPress()
        assertEquals(0f, control.value)

        control.onPress()
        assertEquals(1f, control.value)
    }

    @Test
    fun testToggleIgnoresOnRelease() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.TOGGLE)
        control.onPress()
        assertEquals(1f, control.value)
        control.onRelease()
        assertEquals(1f, control.value, "TOGGLE should not react to onRelease")
    }

    @Test
    fun testMomentaryIsOneAfterPressAndZeroAfterRelease() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.MOMENTARY)
        assertEquals(0f, control.value)

        control.onPress()
        assertEquals(1f, control.value)

        control.onRelease()
        assertEquals(0f, control.value)
    }

    @Test
    fun testMomentaryRepeatedPressReleaseCycles() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.MOMENTARY)
        repeat(3) {
            control.onPress()
            assertEquals(1f, control.value)
            control.onRelease()
            assertEquals(0f, control.value)
        }
    }

    @Test
    fun testTriggerSetsValueOnPressAndResetsExactlyOnceViaConsume() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.TRIGGER)
        assertEquals(0f, control.value)

        control.onPress()
        assertEquals(1f, control.value, "TRIGGER should set value to 1 immediately on press")

        val consumedFirst = control.consumeTriggerReset()
        assertTrue(consumedFirst, "First consumeTriggerReset() after a press should return true")
        assertEquals(0f, control.value, "consumeTriggerReset() should reset value back to 0")

        val consumedSecond = control.consumeTriggerReset()
        assertFalse(consumedSecond, "Second consumeTriggerReset() with no new press should return false")
        assertEquals(0f, control.value)
    }

    @Test
    fun testTriggerIgnoresOnRelease() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.TRIGGER)
        control.onPress()
        control.onRelease()
        assertEquals(1f, control.value, "TRIGGER should not react to onRelease")
    }

    @Test
    fun testTriggerCanBeArmedAgainAfterConsume() {
        val control = MacroControl(isSwitch = true, switchBehavior = SwitchBehavior.TRIGGER)
        control.onPress()
        control.consumeTriggerReset()

        control.onPress()
        assertEquals(1f, control.value)
        assertTrue(control.consumeTriggerReset())
        assertEquals(0f, control.value)
    }
}
