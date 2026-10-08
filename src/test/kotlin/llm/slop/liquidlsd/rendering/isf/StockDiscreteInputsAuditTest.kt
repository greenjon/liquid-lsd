package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * Every bundled input that is discrete (TYPE long/int/bool, or a float with a STEP) must come out of both ISF
 * parameter constructors with a step set that matches its authored range, and its authored default must sit on a step.
 */
class StockDiscreteInputsAuditTest {

    private fun resourceDir(name: String): File =
        File(checkNotNull(ISFFilter::class.java.classLoader.getResource(name)) { "$name missing" }.toURI())

    private fun shaders(): List<File> =
        listOf("default_filters", "default_sources", "default_transitions")
            .flatMap { resourceDir(it).walkTopDown().filter { f -> f.extension == "fs" }.toList() }
            .sortedBy { it.path }

    private fun num(e: kotlinx.serialization.json.JsonElement?): Float? = (e as? JsonPrimitive)?.floatOrNull

    @Test
    fun `discrete stock inputs have steps matching their range and an on-step default`() {
        val problems = mutableListOf<String>()
        var audited = 0
        for (file in shaders()) {
            val header = ISFParser.parseHeader(file.readText()) ?: run { problems += "${file.name}: header does not parse"; continue }
            val isSource = header.INPUTS.none { it.TYPE.equals("image", true) }
            val params = if (isSource) ISFVisualSource.createParameters(header)
                         else ISFFilter(file.nameWithoutExtension, file.nameWithoutExtension, header, mockk<Shader>(relaxed = true)).parameters
            for (input in header.INPUTS) {
                val type = input.TYPE.lowercase()
                val discrete = type in setOf("long", "int", "bool") || input.STEP != null
                if (!discrete) continue
                audited++
                val where = "${file.name}:${input.NAME}"
                val p = params[input.NAME] ?: run { problems += "$where: no parameter"; continue }
                val min = p.minClamp
                val max = p.maxClamp
                val expected = when {
                    type == "bool" -> 2
                    input.STEP != null -> Math.round((max - min) / num(input.STEP)!!) + 1
                    input.VALUES != null -> input.VALUES.size
                    else -> Math.round(max - min) + 1
                }
                if (p.steps != expected) problems += "$where: steps=${p.steps}, expected $expected"
                if (p.steps != null && p.meterType != llm.slop.liquidlsd.parameters.MeterType.DISCRETE) problems += "$where: meter ${p.meterType}"
                val authored = num(input.DEFAULT) ?: if (type == "bool") 0f else min
                if (p.steps != null && p.snap(authored) != authored) problems += "$where: default $authored is off-step (snaps to ${p.snap(authored)})"
            }
        }
        assertEquals(emptyList<String>(), problems)
        assertTrue(audited >= 20, "audit looked at too few inputs ($audited) in ${shaders().map { it.parentFile.name }.distinct()}")
    }
}
