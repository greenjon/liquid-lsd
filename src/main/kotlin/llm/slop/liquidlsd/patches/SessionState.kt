package llm.slop.liquidlsd.patches

import java.io.File

data class SessionState(
    val queue: List<File> = emptyList(),
    val loadedPatches: Map<Int, File> = emptyMap(),
    val unresolvedItems: List<String> = emptyList()
)
