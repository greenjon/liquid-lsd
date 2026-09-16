package llm.slop.liquidlsd.rack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RackPatchBayTest {

    @Test
    fun testConnectValidPorts() {
        val patchBay = RackPatchBay()
        val outPort = PatchPort("u1", "video_out", "Out", PortDirection.OUTPUT)
        val inPort = PatchPort("u2", "video_in", "In", PortDirection.INPUT)

        val cable = patchBay.connect(outPort, inPort)
        assertNotNull(cable)
        assertEquals(1, patchBay.getCables().size)
        assertEquals(outPort, cable?.fromPort)
        assertEquals(inPort, cable?.toPort)

        // Find cable for input
        val foundCable = patchBay.findCableInputFor("u2", "video_in")
        assertNotNull(foundCable)
        assertEquals(outPort, foundCable?.fromPort)
    }

    @Test
    fun testRejectInvalidPortDirections() {
        val patchBay = RackPatchBay()
        val inPort1 = PatchPort("u1", "video_in1", "In 1", PortDirection.INPUT)
        val inPort2 = PatchPort("u2", "video_in2", "In 2", PortDirection.INPUT)
        val outPort1 = PatchPort("u1", "video_out1", "Out 1", PortDirection.OUTPUT)
        val outPort2 = PatchPort("u2", "video_out2", "Out 2", PortDirection.OUTPUT)

        // INPUT -> INPUT should fail
        assertNull(patchBay.connect(inPort1, inPort2))
        assertEquals(0, patchBay.getCables().size)

        // OUTPUT -> OUTPUT should fail
        assertNull(patchBay.connect(outPort1, outPort2))
        assertEquals(0, patchBay.getCables().size)

        // Same unit self-loop should be rejected
        val sameUnitOut = PatchPort("u1", "video_out", "Out", PortDirection.OUTPUT)
        assertNull(patchBay.connect(sameUnitOut, inPort1))
        assertEquals(0, patchBay.getCables().size)
    }

    @Test
    fun testReplacesExistingConnectionOnSameInput() {
        val patchBay = RackPatchBay()
        val outPort1 = PatchPort("u1", "video_out1", "Out 1", PortDirection.OUTPUT)
        val outPort2 = PatchPort("u3", "video_out2", "Out 2", PortDirection.OUTPUT)
        val inPort = PatchPort("u2", "video_in", "In", PortDirection.INPUT)

        patchBay.connect(outPort1, inPort)
        assertEquals(1, patchBay.getCables().size)
        assertEquals("u1", patchBay.getCables()[0].fromPort.unitId)

        // Connecting second output to same input should replace the previous cable
        patchBay.connect(outPort2, inPort)
        assertEquals(1, patchBay.getCables().size)
        assertEquals("u3", patchBay.getCables()[0].fromPort.unitId)
    }

    @Test
    fun testDisconnectAndRemoveUnitConnections() {
        val patchBay = RackPatchBay()
        val outPort = PatchPort("u1", "video_out", "Out", PortDirection.OUTPUT)
        val inPort = PatchPort("u2", "video_in", "In", PortDirection.INPUT)

        val cable = patchBay.connect(outPort, inPort)
        assertNotNull(cable)

        // Disconnect
        cable?.let { patchBay.disconnect(it.id) }
        assertTrue(patchBay.getCables().isEmpty())
        assertNull(patchBay.findCableInputFor("u2", "video_in"))

        // Connect again and remove unit
        patchBay.connect(outPort, inPort)
        assertEquals(1, patchBay.getCables().size)
        patchBay.removeUnitConnections("u1")
        assertTrue(patchBay.getCables().isEmpty())
    }

    @Test
    fun testPipelineWithPatchBayOverride() {
        val pipeline = RackPipeline(allocateGlBuffers = false)
        val patchBay = RackPatchBay()

        // Chain: unit0 (Source = 10) -> unit1 (FX +5) -> unit2 (FX *2)
        // Normalled output: (10 + 5) * 2 = 30
        val unit0 = GenericRackUnit(id = "u0", label = "Source", onProcess = { _ -> 10 })
        val unit1 = GenericRackUnit(id = "u1", label = "FX Add 5", onProcess = { inTex -> inTex + 5 })
        val unit2 = GenericRackUnit(id = "u2", label = "FX Mult 2", onProcess = { inTex -> inTex * 2 })

        // Patch cable: unit0 output directly into unit2 video_in (bypassing unit1's modification)
        val out0 = PatchPort("u0", "video_out", "Out", PortDirection.OUTPUT)
        val in2 = PatchPort("u2", "video_in", "In", PortDirection.INPUT)
        patchBay.connect(out0, in2)

        val units = listOf(unit0, unit1, unit2)
        val out = pipeline.process(units, null, patchBay)

        // unit0 outputs 10.
        // unit1 processes normalled (unit0 = 10) -> outputs 15.
        // unit2 receives input overridden by cable from unit0 -> input is 10 (not 15).
        // unit2 processes 10 * 2 = 20.
        assertEquals(20, out)
    }
}
