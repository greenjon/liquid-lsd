package llm.slop.liquidlsd.presets

import java.io.File

data class SessionState(
    val queue: List<File> = emptyList(),
    val loadedPresets: Map<Int, File> = emptyMap(),
    val unresolvedItems: List<String> = emptyList()
) {
}

