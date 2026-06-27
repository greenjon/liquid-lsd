package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.MeterType

/**
 * Encapsulates the state and parameters of a 3D Kaleidoscopic Iterated Function System (KIFS).
 * Implements VisualSource for desktop rendering.
 */
class Kifs : VisualSource {
    override val parameters = linkedMapOf(
        "Iterations" to ModulatableParameter(5.0f, minClamp = 1.0f, maxClamp = 8.0f),
        "Scale" to ModulatableParameter(2.0f, minClamp = 1.0f, maxClamp = 4.0f),
        "Fold X" to ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 2.0f),
        "Fold Y" to ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 2.0f),
        "Fold Z" to ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 2.0f),
        "Fold Angle X" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Fold Angle Y" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Fold Angle Z" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Shape Morph" to ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f),
        "Zoom" to ModulatableParameter(1.0f, minClamp = 0.1f, maxClamp = 5.0f),
        "Color Shift" to ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f, meterType = MeterType.ENDLESS),
        "Yaw" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Pitch" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Glow" to ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
    )

    override val globalAlpha = ModulatableParameter(1.0f)
}
