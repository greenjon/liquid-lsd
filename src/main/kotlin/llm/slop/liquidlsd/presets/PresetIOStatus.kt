package llm.slop.liquidlsd.presets

enum class PresetIOState { IDLE, LOADING, SAVING, ERROR }


data class PresetIOStatus(
    val state: PresetIOState = PresetIOState.IDLE,
    val errorMessage: String? = null
)



