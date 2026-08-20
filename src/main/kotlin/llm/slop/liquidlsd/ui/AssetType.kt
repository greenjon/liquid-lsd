package llm.slop.liquidlsd.ui

/**
 * Represents the type of asset in the unified browser.
 */
enum class AssetType {
    PRESET,
    PLAYLIST,
    FOLDER
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
    val tags: List<String> = emptyList()
) {
    val displayName: String
        get() = if (isValid) name else "[!] $name"
}
