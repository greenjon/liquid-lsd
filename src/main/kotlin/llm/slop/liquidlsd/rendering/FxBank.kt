package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXBankDto
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.isf.ISFFilter

/**
 * A shared FX processing unit that decks route into (e.g. FX1/FX2/MFX).
 * The bank owns 3 **alternative** [FxChain] instances (A/B/C preset-slot model), plus a single
 * [masterWetDry] shared by every deck currently routed to it.
 *
 * Only one chain is live at a time, selected by [activeChainIndex] -- picking a chain switches
 * which one processes the signal, it does not run all 3 in series.
 */
class FxBank(val label: String) {

    val chains = Array(CHAIN_COUNT) { i -> FxChain("Chain ${i + 1}") }
    val masterWetDry = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    var enabled: Boolean = true

    var activeChainIndex: Int = 0
        set(value) { field = value.coerceIn(0, CHAIN_COUNT - 1) }

    val activeChain: FxChain get() = chains[activeChainIndex]

    fun update() {
        chains.forEach { it.update() }
        masterWetDry.evaluate()
    }

    fun reset() {
        chains.forEach { it.reset() }
        enabled = true
        activeChainIndex = 0
        masterWetDry.reset()
    }

    fun dispose() {
        chains.forEach { it.dispose() }
    }

    /** Exposes this bank's master wet/dry and each chain's parameters at "$prefix/DryWet" and "$prefix/C1..3/..." for macro/MIDI binding. */
    fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/DryWet" to masterWetDry)
        chains.forEachIndexed { i, chain ->
            list.addAll(chain.getParameterPaths("$prefix/C${i + 1}"))
        }
        return list
    }

    /**
     * Backward-compatible slot accessor targeting the active chain ([activeChain]).
     */
    val slots: Array<ISFFilter?>
        get() = activeChain.slots

    fun toFxSlotDto(slotIndex: Int): FXSlotDto? = activeChain.toFxSlotDto(slotIndex)

    fun applyFxSlot(slotIndex: Int, dto: FXSlotDto) = activeChain.applyFxSlot(slotIndex, dto)

    fun clearFxSlot(slotIndex: Int) = activeChain.clearFxSlot(slotIndex)

    fun applyFxChain(dto: FXChainDto) = activeChain.applyFxChain(dto)

    fun toFxChainDto(name: String, tags: List<String> = emptyList()): FXChainDto =
        activeChain.toFxChainDto(name, tags)

    /** Indexed slot/chain accessors targeting a specific chain, for the Parameters panel's per-chain subtabs. */
    fun toFxSlotDto(chainIndex: Int, slotIndex: Int): FXSlotDto? =
        chains.getOrNull(chainIndex)?.toFxSlotDto(slotIndex)

    fun applyFxSlot(chainIndex: Int, slotIndex: Int, dto: FXSlotDto) {
        chains.getOrNull(chainIndex)?.applyFxSlot(slotIndex, dto)
    }

    fun clearFxSlot(chainIndex: Int, slotIndex: Int) {
        chains.getOrNull(chainIndex)?.clearFxSlot(slotIndex)
    }

    fun applyFxChain(chainIndex: Int, dto: FXChainDto) {
        chains.getOrNull(chainIndex)?.applyFxChain(dto)
    }

    fun applyFxBank(dto: FXBankDto) {
        dto.masterWetDry?.let { masterWetDry.applyDto(it) }
        for (i in chains.indices) {
            chains[i].reset()
            val chainDto = dto.chains.getOrNull(i)
            if (chainDto != null) {
                chains[i].applyFxChain(chainDto)
            }
        }
        activeChainIndex = dto.activeChainIndex
    }

    fun toFxBankDto(name: String, tags: List<String> = emptyList()): FXBankDto {
        val chainsList = chains.map { it.toFxChainDto() }
        return FXBankDto(
            name = name,
            tags = tags,
            masterWetDry = masterWetDry.toDto(),
            chains = chainsList,
            activeChainIndex = activeChainIndex
        )
    }

    companion object {
        const val CHAIN_COUNT = 3
        const val SLOT_COUNT = FxChain.SLOT_COUNT
    }
}
