package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.MeterType

/**
 * Encapsulates the state and parameters of a 3D Mandelbulb.
 * Implements VisualSource for desktop rendering.
 */
class Mandelbulb : VisualSource {
    override val parameters = linkedMapOf(
        "Power" to ModulatableParameter(8.0f, minClamp = 1.0f, maxClamp = 20.0f),
        "Iterations" to ModulatableParameter(6.0f, minClamp = 1.0f, maxClamp = 15.0f),
        "Glow" to ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f),
        "Zoom" to ModulatableParameter(1.0f, minClamp = 0.1f, maxClamp = 5.0f),
        "Color Shift" to ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f, meterType = MeterType.ENDLESS),
        "Bailout" to ModulatableParameter(2.0f, minClamp = 1.0f, maxClamp = 5.0f),
        "Yaw" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Pitch" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS)
    )

    override val globalAlpha = ModulatableParameter(1.0f)
}
