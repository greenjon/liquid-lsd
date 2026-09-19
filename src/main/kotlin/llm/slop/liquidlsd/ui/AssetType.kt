package llm.slop.liquidlsd.ui

/**
 * Represents the type of asset in the unified browser.
 */
enum class AssetType {
    PRESET,
    PLAYLIST,
    FOLDER,
    FX_STOCK,
    FX_PRESET,
    FX_CHAIN,
    FX_PLAYLIST,
    TRANSITION_PRESET,
    TRANSITION_PLAYLIST
}

/**
 * Represents a file system item in the asset browser.
 */
data class AssetItem(
    val path: String,
    val name: String,
    val type: AssetType,
    val isValid: Boolean = true,
    val errorMessage: String? = null,
    val tags: List<String> = emptyList(),
    val dependencies: llm.slop.liquidlsd.presets.PresetDependencies? = null
) {
    val displayName: String
        get() = if (isValid) name else "[!] $name"
}
