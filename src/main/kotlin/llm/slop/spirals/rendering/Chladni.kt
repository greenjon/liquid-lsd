package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.MeterType

/**
 * Encapsulates the state and parameters of a 2D/3D Chladni Resonance Volume.
 * Implements VisualSource for desktop rendering.
 */
class Chladni : VisualSource {
    override val parameters = linkedMapOf(
        "Mode" to ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f), // < 0.5 = 2D, >= 0.5 = 3D
        "Frequency N" to ModulatableParameter(3.0f, minClamp = 1.0f, maxClamp = 20.0f),
        "Frequency M" to ModulatableParameter(5.0f, minClamp = 1.0f, maxClamp = 20.0f),
        "Frequency L" to ModulatableParameter(2.0f, minClamp = 1.0f, maxClamp = 20.0f),
        "Thickness" to ModulatableParameter(0.0f, minClamp = -1.0f, maxClamp = 1.0f),
        "Wall Width" to ModulatableParameter(0.1f, minClamp = 0.01f, maxClamp = 0.5f),
        "Scale" to ModulatableParameter(3.0f, minClamp = 0.5f, maxClamp = 15.0f),
        "Speed" to ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 5.0f),
        "Zoom" to ModulatableParameter(1.0f, minClamp = 0.1f, maxClamp = 5.0f),
        "Color Shift" to ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f, meterType = MeterType.ENDLESS),
        "Yaw" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Pitch" to ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = MeterType.ENDLESS),
        "Glow" to ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
    )

    override val globalAlpha = ModulatableParameter(1.0f)
}
