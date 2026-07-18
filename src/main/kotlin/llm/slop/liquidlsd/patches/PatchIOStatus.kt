package llm.slop.liquidlsd.patches

enum class PatchIOState { IDLE, LOADING, SAVING, ERROR }

data class PatchIOStatus(
    val state: PatchIOState = PatchIOState.IDLE,
    val errorMessage: String? = null
)
