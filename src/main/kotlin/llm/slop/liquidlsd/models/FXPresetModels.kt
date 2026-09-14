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
 * Data Transfer Object for a 4-slot FX chain preset (.lsdfxchain).
 */
@Serializable
data class FXChainDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val slots: List<FXSlotDto?> = emptyList() // Size 4; null = empty slot
)
