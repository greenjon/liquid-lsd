package llm.slop.liquidlsd.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `CustomRangeSlider.drawCompactSlider` / `drawCustomRangeSlider` end on a zero-size dummy, so an `itemTooltip` or
 * `controlTooltip` right after one tests that empty item and never shows (audit L5-1). Pass the text as the
 * slider's `description` instead; it shows in the slider's label and track tooltips.
 */
class SliderTooltipTest {
    private val root = File("src/main/kotlin/llm/slop/liquidlsd")
    private val call = Regex("""\b(drawCompactSlider|drawCustomRangeSlider)\(""")
    private val tooltip = Regex("""^\s*(itemTooltip|controlTooltip)\b""")

    @Test
    fun noTooltipDirectlyAfterASlider() {
        val hits = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val lines = f.readLines()
            var i = 0
            while (i < lines.size) {
                if (!call.containsMatchIn(lines[i]) || "fun " in lines[i]) { i++; continue }
                // find the line closing the call
                var depth = 0
                var started = false
                var j = i
                outer@ while (j < lines.size) {
                    for (c in lines[j]) {
                        if (c == '(') { depth++; started = true } else if (c == ')') depth--
                    }
                    if (started && depth <= 0) break@outer
                    j++
                }
                var k = j + 1
                while (k < lines.size && lines[k].isBlank()) k++
                if (k < lines.size && tooltip.containsMatchIn(lines[k])) hits += "${f.path}:${k + 1}"
                i = j + 1
            }
        }
        assertTrue(hits.isEmpty(), "tooltip after a slider never shows; pass it as `description`: $hits")
    }
}
