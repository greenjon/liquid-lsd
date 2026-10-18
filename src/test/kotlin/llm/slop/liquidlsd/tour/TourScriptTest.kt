package llm.slop.liquidlsd.tour

import kotlin.test.Test
import kotlin.test.assertEquals

class TourScriptTest {
    @Test
    fun `parses chapters, burnCaptions and the FX action fields`() {
        val s = TourScript.parse(
            """{"durationSec":5,"burnCaptions":true,"chapters":[{"t":0,"id":"a","title":"A","clipSec":4}],
            "steps":[{"t":1,"action":"fx_filter","deck":"MASTER","slot":2,"target":"bloom"},{"t":2,"action":"drag","dx":10,"dy":-40}]}"""
        )
        assertEquals(true, s.burnCaptions)
        assertEquals(4.0, s.chapters.single().clipSec)
        assertEquals(2, s.steps[0].slot)
        assertEquals(-40f, s.steps[1].dy)
    }
}
