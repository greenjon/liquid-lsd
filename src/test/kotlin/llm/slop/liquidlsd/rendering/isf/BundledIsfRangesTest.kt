package llm.slop.liquidlsd.rendering.isf

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Bundled ISF inputs stay 0..1 / -1..1 (shown as percent) unless they are enums, counts or angles; see DECISIONS.md. */
class BundledIsfRangesTest {
    private val discrete = setOf(
        "colorMode", "palette", "mode", "segments", "pixelSize", "latticeMode", "colorDepth", "symmetry",
        "scanlineCount", "strobeMode", "rate", "spiralArms", "fbKaleido", "blockiness",
        "Symmetries", "ColorMode", "FrequencyM", "FrequencyN", "FrequencyL", "PaletteMode", "Detail",
        "MaxPoints", "SurfaceType", "ColorMethod"
    )
    private val inputRegex = Regex("""\{[^{}]*"TYPE"\s*:\s*"float"[^{}]*\}""")
    private fun num(obj: String, key: String) =
        Regex(""""$key"\s*:\s*(-?[0-9.]+)""").find(obj)?.groupValues?.get(1)?.toFloatOrNull()

    @Test
    fun bundledInputsAreNormalizedUnlessDiscreteOrAngle() {
        val offenders = mutableListOf<String>()
        for (dir in listOf("default_sources", "default_filters", "default_transitions")) {
            File("src/main/resources/$dir").walkTopDown().filter { it.extension == "fs" }.forEach { f ->
                val header = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).find(f.readText())?.value ?: return@forEach
                for (m in inputRegex.findAll(header)) {
                    val name = Regex(""""NAME"\s*:\s*"(\w+)"""").find(m.value)!!.groupValues[1]
                    val lo = num(m.value, "MIN") ?: continue
                    val hi = num(m.value, "MAX") ?: continue
                    val unit = (lo == 0f || lo == -1f) && hi == 1f
                    val angle = abs(lo + 3.14159f) < 0.01f && abs(hi - 3.14159f) < 0.01f
                    if (!unit && !angle && name !in discrete) offenders += "${f.name}: $name [$lo, $hi]"
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Inputs outside 0..1 / -1..1 that are not discrete or angles:\n" + offenders.joinToString("\n"))
    }
}
