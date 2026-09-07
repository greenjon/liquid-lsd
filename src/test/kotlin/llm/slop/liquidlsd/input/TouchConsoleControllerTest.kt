package llm.slop.liquidlsd.input

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TouchConsoleControllerTest {

    private lateinit var controller: TouchConsoleController
    private lateinit var mixer: Mixer
    private lateinit var crossfadeParam: ModulatableParameter

    private var mockLevelA = 1.0f
    private var mockLevelB = 1.0f
    private var mockLevelBG = 1.0f

    @BeforeTest
    fun setUp() {
        crossfadeParam = ModulatableParameter(-1.0f, minClamp = -1.0f, maxClamp = 1.0f, meterType = MeterType.BIPOLAR)
        mixer = mockk(relaxed = true)

        every { mixer.crossfade } returns crossfadeParam
        every { mixer.levelA } answers { mockLevelA }
        every { mixer.levelA = any() } answers { mockLevelA = firstArg() }
        every { mixer.levelB } answers { mockLevelB }
        every { mixer.levelB = any() } answers { mockLevelB = firstArg() }
        every { mixer.levelBG } answers { mockLevelBG }
        every { mixer.levelBG = any() } answers { mockLevelBG = firstArg() }

        controller = TouchConsoleController()
        controller.initialize(0L, mixer)
        controller.setBackendForTesting(object : TouchStripBackend {
            override var state: TouchBackendState = TouchBackendState.READY
            override fun start(): Boolean = true
            override fun setGrabbed(grabbed: Boolean) {}
            override fun stop() {}
        })
        // Force active for test processing
        controller.toggleActive(true)
    }

    @AfterTest
    fun tearDown() {
        controller.shutdown()
        unmockkAll()
    }

    @Test
    fun testZoneRoutingAndDirectJump() {
        // 1. Crossfader Touch (Y <= 0.28)
        // X = 0.50 (exact center detent)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(1L, 0.50f, 0.15f))
        controller.processPendingEvents()
        assertEquals(0.0f, crossfadeParam.baseValue, 0.001f)

        // 2. Deadzone Touch (Y in 0.28..0.45) - should be ignored on touch down
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(2L, 0.20f, 0.35f))
        controller.processPendingEvents()
        assertEquals(1.0f, mockLevelA, "Deadzone touch down should not alter Level A")

        // 3. Deck A Alpha (X < 0.33, Y >= 0.45)
        // Y = 0.48 maps to 0.0 (blackout)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(3L, 0.20f, 0.48f))
        controller.processPendingEvents()
        assertEquals(0.0f, mockLevelA, 0.001f)

        // 4. Deck BG Alpha (X in 0.33..0.67, Y >= 0.45)
        // Y = 0.94 maps to 1.0 (full brightness)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(4L, 0.50f, 0.94f))
        controller.processPendingEvents()
        assertEquals(1.0f, mockLevelBG, 0.001f)

        // 5. Deck B Alpha (X > 0.67, Y >= 0.45)
        // Y = 0.71 (midway: (0.71 - 0.48) / 0.46 = 0.5)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(5L, 0.85f, 0.71f))
        controller.processPendingEvents()
        assertEquals(0.5f, mockLevelB, 0.01f)
    }

    @Test
    fun testZoneAffinityAndDeadzoneDrift() {
        // Touch down in Crossfader zone
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(10L, 0.20f, 0.10f))
        controller.processPendingEvents()
        assertTrue(crossfadeParam.baseValue < 0f)

        // Vigorous scratch drifts up into the deadzone (Y = 0.35) and towards right edge (X = 0.96)
        controller.eventQueue.add(TouchConsoleEvent.TouchMove(10L, 0.96f, 0.35f))
        controller.processPendingEvents()

        // Zone Affinity keeps touch 10 bound to crossfader, clamped to +1.0
        assertEquals(1.0f, crossfadeParam.baseValue, 0.001f)
    }

    @Test
    fun testBezelClampingAndCenterDetent() {
        // Crossfader left bezel clamp: X <= 0.05 -> -1.0
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(20L, 0.03f, 0.10f))
        controller.processPendingEvents()
        assertEquals(-1.0f, crossfadeParam.baseValue, 0.001f)

        // Crossfader right bezel clamp: X >= 0.95 -> +1.0
        controller.eventQueue.add(TouchConsoleEvent.TouchMove(20L, 0.97f, 0.10f))
        controller.processPendingEvents()
        assertEquals(1.0f, crossfadeParam.baseValue, 0.001f)

        // Crossfader center detent: |X - 0.50| <= 0.02 -> 0.0
        controller.eventQueue.add(TouchConsoleEvent.TouchMove(20L, 0.515f, 0.10f))
        controller.processPendingEvents()
        assertEquals(0.0f, crossfadeParam.baseValue, 0.001f)

        // Alpha bottom clamp: Y <= 0.48 -> 0.0
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(21L, 0.15f, 0.46f))
        controller.processPendingEvents()
        assertEquals(0.0f, mockLevelA, 0.001f)

        // Alpha top clamp: Y >= 0.94 -> 1.0
        controller.eventQueue.add(TouchConsoleEvent.TouchMove(21L, 0.15f, 0.98f))
        controller.processPendingEvents()
        assertEquals(1.0f, mockLevelA, 0.001f)
    }

    @Test
    fun testMultiFingerLIFOStutter() {
        // Finger 1 (Anchor): Holds Deck A (-1.0)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(100L, 0.02f, 0.10f))
        controller.processPendingEvents()
        assertEquals(-1.0f, crossfadeParam.baseValue, 0.001f)

        // Finger 2 (Stutter Tap): Taps Deck B (+1.0)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(101L, 0.98f, 0.10f))
        controller.processPendingEvents()
        assertEquals(1.0f, crossfadeParam.baseValue, 0.001f)

        // Finger 2 released -> Stutter snap back to Anchor Finger 1 (-1.0)
        controller.eventQueue.add(TouchConsoleEvent.TouchUp(101L))
        controller.processPendingEvents()
        assertEquals(-1.0f, crossfadeParam.baseValue, 0.001f)

        // Finger 1 released -> Sticky at -1.0
        controller.eventQueue.add(TouchConsoleEvent.TouchUp(100L))
        controller.processPendingEvents()
        assertEquals(-1.0f, crossfadeParam.baseValue, 0.001f)
    }

    @Test
    fun testAlphaVideoStrobe() {
        // Finger 1 (Anchor): Blackout at bottom of Deck A (0.0)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(200L, 0.10f, 0.48f))
        controller.processPendingEvents()
        assertEquals(0.0f, mockLevelA, 0.001f)
        assertEquals(0.0f, controller.holdLevelA, 0.001f)

        // Finger 2 (Strobe flash): Tap at top of Deck A (1.0)
        controller.eventQueue.add(TouchConsoleEvent.TouchDown(201L, 0.10f, 0.96f))
        controller.processPendingEvents()
        assertEquals(1.0f, mockLevelA, 0.001f)

        // Finger 2 released -> Snaps back to blackout (0.0)
        controller.eventQueue.add(TouchConsoleEvent.TouchUp(201L))
        controller.processPendingEvents()
        assertEquals(0.0f, mockLevelA, 0.001f)

        // Finger 1 released -> Sticky hold level preserved
        controller.eventQueue.add(TouchConsoleEvent.TouchUp(200L))
        controller.processPendingEvents()
        assertEquals(0.0f, controller.holdLevelA, 0.001f)
    }

    @Test
    fun testFocusLossSafetyUngrab() {
        assertTrue(controller.isActive)
        controller.onFocusLost()
        assertFalse(controller.isActive, "Focus loss must immediately deactivate touch console and ungrab hardware")
    }
}
