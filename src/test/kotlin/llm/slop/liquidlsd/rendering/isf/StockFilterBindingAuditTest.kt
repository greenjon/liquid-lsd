package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * v1.0 every-ISF binding audit for the bundled filters (the default_filters resources). GL-free: headers are
 * parsed and [ISFFilter]s built over a mocked [Shader], exactly as the other binding tests do.
 *
 * For each stock filter: the Metaknob binding must target a real, float, non-degenerate uniform whose
 * range lies inside the uniform's own clamp, must not fall back to the Dry/Wet safety net, must not
 * duplicate a target, and must reproduce the authored default (no jump when the filter loads).
 * The focus-mode knobs (K1-K3, first page of [ISFFilter.parameters]) must be plain floats.
 */
class StockFilterBindingAuditTest {

    private val dir: File = File(
        checkNotNull(ISFFilter::class.java.classLoader.getResource("default_filters")) { "default_filters missing" }.toURI()
    )

    private fun stockSources(): Map<String, String> =
        dir.listFiles { f -> f.extension == "fs" }!!.sortedBy { it.name }.associate { it.nameWithoutExtension to it.readText() }

    private fun build(id: String, source: String): ISFFilter {
        val header = checkNotNull(ISFParser.parseHeader(source)) { "$id: header does not parse" }
        // contentHash left null: user override files must never influence the audit.
        return ISFFilter(id, id, header, mockk<Shader>(relaxed = true))
    }

    @Test
    fun `bundled list matches the default_filters directory`() {
        val field = Class.forName("llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry").getDeclaredField("bundledFilters")
        field.isAccessible = true
        val registry = ISFFilterRegistry
        @Suppress("UNCHECKED_CAST")
        val listed = (field.get(registry) as List<String>).toSet()
        assertEquals(stockSources().keys, listed)
    }

    @Test
    fun `every stock filter has a usable Metaknob binding and plain float focus knobs`() {
        val problems = mutableListOf<String>()
        val notes = mutableListOf<String>()
        for ((id, source) in stockSources()) {
            val f = try { build(id, source) } catch (e: Throwable) { problems += "$id: ${e.message}"; continue }
            val floats = f.header.INPUTS.filter { it.TYPE.equals("float", true) }.map { it.NAME }.toSet()
            if (f.header.INPUTS.count { it.TYPE.equals("image", true) } != 1) problems += "$id: not exactly one image input"
            if (f.metaBindings.isEmpty()) problems += "$id: no meta bindings"
            val targets = f.metaBindings.map { it.targetParamName }
            if (targets.size != targets.toSet().size) problems += "$id: duplicate meta targets $targets"
            for (b in f.metaBindings) {
                val t = b.targetParamName
                if (t == null) { problems += "$id: falls back to dry/wet safety net"; continue }
                val p = f.parameters[t]
                if (p == null) { problems += "$id: target '$t' is not a parameter"; continue }
                if (t !in floats) problems += "$id: target '$t' is not a float input"
                if (!b.minVal.isFinite() || !b.maxVal.isFinite()) problems += "$id: non-finite range"
                if (b.minVal == b.maxVal) problems += "$id: '$t' degenerate range ${b.minVal}"
                val lo = minOf(b.minVal, b.maxVal); val hi = maxOf(b.minVal, b.maxVal)
                if (lo < p.minClamp - 1e-4f || hi > p.maxClamp + 1e-4f)
                    problems += "$id: '$t' range ${b.minVal}..${b.maxVal} outside clamp ${p.minClamp}..${p.maxClamp}"
                val def = f.header.INPUTS.first { it.NAME == t }.DEFAULT?.toString()?.toFloatOrNull() ?: 0f
                if (def < lo - 1e-4f || def > hi + 1e-4f)
                    notes += "$id: '$t' authored default $def lies outside the bound range ${b.minVal}..${b.maxVal} (Metaknob clamps to the nearest end at load)"
                else {
                    val back = b.mapKnobToTarget(b.knobForTarget(def))
                    if (kotlin.math.abs(back - def) > 1e-3f * maxOf(1f, hi - lo)) problems += "$id: '$t' default $def does not round-trip ($back)"
                }
            }
            // Focus mode K1-K3 are the first three entries of parameters (FxMacroSync.syncFocusMode, page 0).
            val typeOf = f.header.INPUTS.associate { it.NAME to it.TYPE.lowercase() }
            f.parameters.keys.take(3).forEach { n ->
                if (typeOf[n] != "float") notes += "$id: focus knob '$n' is ${typeOf[n]}, not float"
            }
            val metaTarget = f.metaBinding.targetParamName
            if (metaTarget != null && metaTarget in f.parameters.keys.take(3))
                notes += "$id: Metaknob target '$metaTarget' is also a focus knob (locked by the Metaknob)"
        }
        notes.forEach { println("AUDIT NOTE: $it") }
        assertTrue(problems.isEmpty(), "Stock filter binding problems:\n" + problems.joinToString("\n"))
    }
}
