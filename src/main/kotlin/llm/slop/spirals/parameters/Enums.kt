package llm.slop.spirals.parameters

import kotlinx.serialization.Serializable

@Serializable
enum class ModulationOperator {
    ADD, MUL, SCALE
}

@Serializable
enum class Waveform {
    SINE, TRIANGLE, SQUARE, RANDOM
}

@Serializable
enum class LfoSpeedMode {
    SLOW, MEDIUM, FAST
}

@Serializable
enum class GenUnit {
    TIME, BEAT
}

