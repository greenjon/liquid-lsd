package llm.slop.liquidlsd.presets

enum class PresetIOState { IDLE, LOADING, SAVING, ERROR }

typealias PatchIOState = PresetIOState

data class PresetIOStatus(
    val state: PresetIOState = PresetIOState.IDLE,
    val errorMessage: String? = null
)

typealias PatchIOStatus = PresetIOStatus

