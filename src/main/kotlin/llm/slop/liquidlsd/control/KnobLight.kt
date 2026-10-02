package llm.slop.liquidlsd.control

/**
 * What one knob's ring and LED should show: the ring position ([value], 0..1) and the LED colour
 * ([r], [g], [b], 0..1), or dark when not [lit] (a bypassed or empty slot still shows its value).
 */
data class KnobLight(
    val value: Float,
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val lit: Boolean = true
)

/** The 16 Perform-view knobs' lights, row-major; null = nothing there (ring at zero, LED off). */
interface KnobLightSource {
    fun knobLights(): List<KnobLight?>
}
