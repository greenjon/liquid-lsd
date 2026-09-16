package llm.slop.liquidlsd.rack

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RackMicroMonitorTest {

    private lateinit var rackPipeline: RackPipeline
    private val dummyMixer = mockk<Mixer>(relaxed = true)

    @BeforeEach
    fun setUp() {
        rackPipeline = RackPipeline(width = 640, height = 360, allocateGlBuffers = false)
    }

    @AfterEach
    fun tearDown() {
        rackPipeline.dispose()
    }

    @Test
    fun testUnitTracksOutputTextureAcrossPipelineStages() {
        val u1 = GenericRackUnit(
            id = "u1",
            label = "Stage 1",
            unitType = RackUnitType.GENERATOR,
            onProcess = { _ -> 101 }
        )

        val u2 = GenericRackUnit(
            id = "u2",
            label = "Stage 2",
            unitType = RackUnitType.PROCESSOR,
            onProcess = { input -> input + 200 }
        )

        val units = listOf(u1, u2)

        assertEquals(0, u1.lastOutputTexture)
        assertEquals(0, u2.lastOutputTexture)

        val finalOut = rackPipeline.process(units, null)

        assertEquals(301, finalOut)
        assertEquals(101, u1.lastOutputTexture, "Stage 1 should expose texture 101 to its micro-monitor")
        assertEquals(301, u2.lastOutputTexture, "Stage 2 should expose texture 301 to its micro-monitor")
    }

    @Test
    fun testBypassedUnitPreservesPassthroughInMonitor() {
        val u1 = GenericRackUnit(
            id = "u1",
            label = "Stage 1",
            unitType = RackUnitType.GENERATOR,
            onProcess = { 55 }
        )

        val u2 = GenericRackUnit(
            id = "u2",
            label = "Stage 2 (Bypassed)",
            unitType = RackUnitType.PROCESSOR,
            onProcess = { input -> input + 999 }
        )
        u2.isBypassed = true

        val units = listOf(u1, u2)
        val finalOut = rackPipeline.process(units, null)

        assertEquals(55, finalOut)
        assertEquals(55, u1.lastOutputTexture)
        assertEquals(55, u2.lastOutputTexture, "Bypassed unit passes through upstream texture and sets lastOutputTexture")
        assertTrue(u2.isBypassed)
    }

    @Test
    fun testPoweredOffUnitResetsMonitorTextureToZero() {
        val u1 = GenericRackUnit(
            id = "u1",
            label = "Stage 1",
            unitType = RackUnitType.GENERATOR,
            onProcess = { 77 }
        )
        u1.lastOutputTexture = 77
        u1.isPowered = false

        val units = listOf(u1)
        val finalOut = rackPipeline.process(units, null)

        assertEquals(0, finalOut)
        assertEquals(0, u1.lastOutputTexture, "Powered off unit must reset lastOutputTexture to 0")
        assertFalse(u1.isPowered)
    }

    @Test
    fun testSoloUnitDirectsToMasterWithoutDisruptingStageTextures() {
        val u1 = GenericRackUnit(id = "u1", label = "Stage 1", unitType = RackUnitType.GENERATOR, onProcess = { 10 })
        val u2 = GenericRackUnit(id = "u2", label = "Stage 2 Solo", unitType = RackUnitType.PROCESSOR, onProcess = { input -> input + 20 })
        val u3 = GenericRackUnit(id = "u3", label = "Stage 3", unitType = RackUnitType.PROCESSOR, onProcess = { input -> input + 50 })

        u2.isSoloed = true

        val units = listOf(u1, u2, u3)
        val masterOut = rackPipeline.process(units, null)

        // Master output is captured from solo unit (10 + 20 = 30)
        assertEquals(30, masterOut)

        // Each individual unit's micro-monitor receives its exact stage output
        assertEquals(10, u1.lastOutputTexture)
        assertEquals(30, u2.lastOutputTexture)
        assertEquals(80, u3.lastOutputTexture)
    }
}
