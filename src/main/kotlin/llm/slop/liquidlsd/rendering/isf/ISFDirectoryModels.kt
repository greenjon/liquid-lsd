package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable

/**
 * Enumerates the origin type of an ISF directory source.
 * Higher priority values override lower priority values when unique shader identifiers collide:
 * Custom (4) > UserStandard (3) > SystemStandard (2) > BuiltIn (1).
 */
enum class DirectorySourceType(val priority: Int) {
    BUILT_IN(1),
    SYSTEM_STANDARD(2),
    USER_STANDARD(3),
    CUSTOM(4)
}

/**
 * Runtime availability status of a registered ISF directory source on the local filesystem.
 */
enum class DirectoryStatus {
    ACTIVE,
    MISSING,
    UNREADABLE
}

/**
 * Persisted configuration for an ISF directory source location.
 */
@Serializable
data class DirectorySourceConfig(
    val path: String,
    val type: DirectorySourceType,
    val isEnabled: Boolean = true
)

/**
 * Root serialized container for ISF directory configurations.
 */
@Serializable
data class ISFDirectorySettings(
    val sources: List<DirectorySourceConfig> = emptyList()
)

/**
 * Classification of an ISF shader asset.
 */
enum class ISFAssetType {
    GENERATOR,
    FILTER,
    TRANSITION
}

/**
 * Descriptor representing a discovered Interactive Shader Format (ISF) asset.
 */
data class ISFAsset(
    val id: String,
    val displayName: String,
    val sourcePath: String,
    val vertexPath: String? = null,
    val category: String,
    val type: ISFAssetType,
    val sourceType: DirectorySourceType,
    val sourceDirectoryPath: String
)
