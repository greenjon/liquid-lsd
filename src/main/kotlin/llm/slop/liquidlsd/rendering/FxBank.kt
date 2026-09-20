package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry

/**
 * A shared FX processing unit that decks route into (two of these exist: BANK_1/BANK_2).
 * The bank owns the 3 chained filters and their macro-driven parameters plus a single
 * [masterWetDry] shared by every deck currently routed to it. A routed deck contributes only
 * its own send level on top of that, applied when the deck mixes the bank's output back into
 * its own per-deck FBOs -- FBOs stay deck-owned since each deck feeds the shared filters a
 * different source texture on the same frame.
 */
class FxBank(val label: String) {

    val slots = arrayOfNulls<ISFFilter>(SLOT_COUNT)
    val masterWetDry = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    var enabled: Boolean = true

    fun update() {
        slots.forEach { it?.update() }
        masterWetDry.evaluate()
    }

    fun reset() {
        slots.forEach { it?.reset() }
        enabled = true
        masterWetDry.reset()
    }

    fun dispose() {
        slots.forEach { it?.dispose() }
    }

    /** Exposes this bank's filter/wet-dry parameters at "$prefix/FX1..3" and "$prefix/DryWet" for macro/MIDI binding. */
    fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/DryWet" to masterWetDry)
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
        for (i in slots.indices) {
            clearFxSlot(i)
            val slotDto = dto.slots.getOrNull(i)
            if (slotDto != null && slotDto.filterId.isNotBlank()) {
                applyFxSlot(i, slotDto)
            }
        }
    }

    fun toFxChainDto(name: String, tags: List<String> = emptyList()): FXChainDto {
        val slotsList = (0 until SLOT_COUNT).map { toFxSlotDto(it) }
        return FXChainDto(name = name, tags = tags, slots = slotsList)
    }

    companion object {
        const val SLOT_COUNT = 3
    }
}
