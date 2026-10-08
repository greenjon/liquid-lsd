package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.parameters.MeterType

/**
 * What one knob's ring and LED should show: the ring position ([value], 0..1) and the LED colour
 * ([r], [g], [b], 0..1), or dark when not [lit] (a bypassed or empty slot still shows its value).
 * [meterType] indicates if the parameter is monopolar, bipolar, endless, or discrete.
 * [ringBrightness] (0..1) dims the ring on devices that can ([KnobFeedbackDef.indicatorChannel]); 1 = full.
 */
data class KnobLight(
    val value: Float,
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val lit: Boolean = true,
    val meterType: MeterType = MeterType.MONOPOLAR,
    val ringBrightness: Float = 1f
)

/** The 16 Perform-view knobs' lights, row-major; null = nothing there (ring at zero, LED off). */
interface KnobLightSource {
    fun knobLights(): List<KnobLight?>

    /**
     * Fills [out] (index = knob; entries past the source's lights become null). Per-frame feedback
     * uses this with a reused buffer; override it to avoid building a list. Defaults to [knobLights].
     */
    fun fillKnobLights(out: Array<KnobLight?>) {
        val lights = knobLights()
        for (i in out.indices) out[i] = lights.getOrNull(i)
    }
}
