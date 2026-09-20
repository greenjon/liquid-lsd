package llm.slop.liquidlsd.models

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for an individual FX slot preset (.lsdfx).
 */
@Serializable
data class FXPresetDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val slot: FXSlotDto
)

/**
 * Data Transfer Object for a 3-slot FX chain preset (.lsdfxchain).
 */
@Serializable
data class FXChainDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val dryWet: ParameterDto? = null,
    val slots: List<FXSlotDto?> = emptyList() // Size 3; null = empty slot
)

/**
 * Data Transfer Object for a 3-chain FX bank preset (.lsdfxbank).
 */
@Serializable
data class FXBankDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val masterWetDry: ParameterDto? = null,
    val chains: List<FXChainDto?> = emptyList() // Size 3; null = empty chain
)

/**
 * Data Transfer Object for an individual Transition Preset (.lsdtrans).
 */
@Serializable
data class TransitionPresetDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val slot: FXSlotDto
)

/**
 * Data Transfer Object for a Transition Playlist (.lsdtransplay).
 */
@Serializable
data class TransitionPlaylistDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val items: List<String> = emptyList() // List of .lsdtrans file paths or stock shader IDs
)

/**
 * Data Transfer Object for an FX Playlist (.lsdfxplay).
 * A curated, ordered sequence of saved FX items only — single FX presets
 * (.lsdfx) or FX chains (.lsdfxchain). Stock/unconfigured filters have no
 * persisted parameters and are never eligible for playlist membership.
 */
@Serializable
data class FXPlaylistDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val items: List<String> = emptyList() // List of .lsdfx or .lsdfxchain file paths
)

