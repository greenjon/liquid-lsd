package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry

/**
 * An individual FX chain holding [SLOT_COUNT] serial ISF filter slots,
 * with its own chain-level [dryWet] blend and [enabled] bypass toggle.
 * Owned by an [FxBank] as one of its 3 serial processing stages.
 */
class FxChain(val label: String) {

    var name: String = ""
    val slots = arrayOfNulls<ISFFilter>(SLOT_COUNT)
    val dryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    var enabled: Boolean = true

    fun update() {
        slots.forEach { it?.update() }
        dryWet.evaluate()
    }

    fun reset() {
        slots.forEach { it?.reset() }
        enabled = true
        dryWet.reset()
        name = ""
    }

    fun dispose() {
        slots.forEach { it?.dispose() }
    }

    /** Exposes this chain's dry/wet and slot parameters for macro/MIDI binding. */
    fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/DryWet" to dryWet)
        slots.forEachIndexed { i, fx ->
            fx?.getParameterPaths("$prefix/FX${i + 1}")?.let { list.addAll(it) }
        }
        return list
    }

    fun toFxSlotDto(slotIndex: Int): FXSlotDto? {
        val fx = slots.getOrNull(slotIndex) ?: return null
        if (fx.id.isEmpty()) return null
        return FXSlotDto(
            filterId = fx.id,
            enabled = fx.enabled,
            dryWet = fx.dryWet.toDto(),
            parameters = fx.parameters.mapValues { p -> p.value.toDto() }
        )
    }

    fun applyFxSlot(slotIndex: Int, dto: FXSlotDto) {
        if (slotIndex !in slots.indices) return
        slots[slotIndex]?.dispose()
        slots[slotIndex] = null

        if (dto.filterId.isNotBlank()) {
            val filter = ISFFilterRegistry.createFilter(dto.filterId)
            if (filter != null) {
                filter.enabled = dto.enabled
                filter.dryWet.applyDto(dto.dryWet)
                for ((key, paramDto) in dto.parameters) {
                    filter.parameters[key]?.applyDto(paramDto)
                }
                slots[slotIndex] = filter
            }
        }
    }

    fun clearFxSlot(slotIndex: Int) {
        if (slotIndex in slots.indices) {
            slots[slotIndex]?.dispose()
            slots[slotIndex] = null
        }
    }

    fun applyFxChain(dto: FXChainDto) {
        name = dto.name
        dto.dryWet?.let { dryWet.applyDto(it) }
        for (i in slots.indices) {
            clearFxSlot(i)
            val slotDto = dto.slots.getOrNull(i)
            if (slotDto != null && slotDto.filterId.isNotBlank()) {
                applyFxSlot(i, slotDto)
            }
        }
    }

    fun toFxChainDto(chainName: String = name, tags: List<String> = emptyList()): FXChainDto {
        val slotsList = (0 until SLOT_COUNT).map { toFxSlotDto(it) }
        return FXChainDto(
            name = chainName.ifBlank { "fx_chain" },
            tags = tags,
            dryWet = dryWet.toDto(),
            slots = slotsList
        )
    }

    companion object {
        const val SLOT_COUNT = 3
    }
}
