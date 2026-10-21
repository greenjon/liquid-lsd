package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.Test
import kotlin.test.assertEquals

class ParamLabelsTest {
    @Test
    fun humanizesCamelAndSnakeCase() {
        assertEquals("Cell scale", ParamLabels.humanize("CellScale"))
        assertEquals("Edge glow", ParamLabels.humanize("EdgeGlow"))
        assertEquals("Rotate Z", ParamLabels.humanize("rotateZ"))
        assertEquals("RGB shift", ParamLabels.humanize("RGBShift"))
        assertEquals("RGB shift", ParamLabels.humanize("rgb_shift"))
        assertEquals("FX amount", ParamLabels.humanize("fx_amount"))
        assertEquals("LFO rate", ParamLabels.humanize("LFO_rate"))
        assertEquals("Color 1", ParamLabels.humanize("color1"))
        assertEquals("Zoom 3D", ParamLabels.humanize("zoom3D"))
        assertEquals("Speed", ParamLabels.humanize("speed"))
        assertEquals("Hue Sweep", ParamLabels.humanize("Hue Sweep"))
    }

    @Test
    fun prefersTheIsfLabel() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "CellScale", TYPE = "float", LABEL = "Scale"),
            ISFInput(NAME = "EdgeGlow", TYPE = "float"),
            ISFInput(NAME = "center", TYPE = "point2D", LABEL = "Centre"),
            ISFInput(NAME = "tint", TYPE = "color")
        ))
        assertEquals("Scale", ParamLabels.of(header, "CellScale"))
        assertEquals("Edge glow", ParamLabels.of(header, "EdgeGlow"))
        assertEquals("Centre X", ParamLabels.of(header, "center X"))
        assertEquals("Tint R", ParamLabels.of(header, "tint R"))
        assertEquals("Hue offset", ParamLabels.of(null, "HueOffset"))
    }
}
