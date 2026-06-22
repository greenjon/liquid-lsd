package llm.slop.spirals.parameters

import kotlinx.serialization.Serializable

@Serializable
enum class ModulationOperator {
    ADD, MUL, SCALE
}

@Serializable
enum class Waveform {
    SINE, TRIANGLE, SQUARE
}

@Serializable
enum class LfoSpeedMode {
    SLOW, MEDIUM, FAST
}
