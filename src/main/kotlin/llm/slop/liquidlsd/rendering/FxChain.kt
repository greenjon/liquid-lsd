package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.rendering.isf.toBinding
import llm.slop.liquidlsd.rendering.isf.toDto

/**
 * An individual FX chain holding [SLOT_COUNT] serial ISF filter slots,
 * with its own chain-level [dryWet] blend and [enabled] bypass toggle.
 * Each deck owns one ([Deck.fxChain]), and so does the master bus ([Mixer.masterFxChain]).
 *
 * [superKnob] is a Traktor/Mixxx-style "chain macro": when a slot is linked
 * ([slotSuperKnobLink]), moving the Super Knob drives that slot's own [ISFFilter.metaKnob].
 * Linking uses soft-takeover (see [propagateSuperKnob]) rather than a hard snap, so relinking
 * or loading a new filter into an already-linked slot never yanks its Metaknob to wherever the
 * Super Knob currently sits -- the link only takes effect once the Super Knob's own movement
 * crosses (or comes within tolerance of) that slot's current Metaknob value.
 */
class FxChain(val label: String) {

    var name: String = ""
    val slots = arrayOfNulls<ISFFilter>(SLOT_COUNT)
    val dryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    var enabled: Boolean = true

    val superKnob = ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val slotSuperKnobLink: BooleanArray = booleanArrayOf(true, true, true)

    // Armed (false) until the Super Knob's movement converges with a linked slot's current
    // Metaknob value -- see propagateSuperKnob(). Starts false for every slot: a freshly
    // constructed chain has nothing to "already be in sync" with.
    private val hasTakenOver = BooleanArray(SLOT_COUNT) { false }
    private var lastSuperKnobValue = superKnob.value

    /**
     * Arms soft-takeover for [slotIndex] so its (possibly freshly-loaded) filter's Metaknob
     * doesn't snap to the Super Knob's current value until the Super Knob next moves. Callers
     * that assign directly into [slots] (bypassing [applyFxSlot], e.g. the slot picker UI) must
     * call this after doing so.
     */
    fun armSlotTakeover(slotIndex: Int) {
        if (slotIndex in hasTakenOver.indices) hasTakenOver[slotIndex] = false
    }

    /**
     * Links or unlinks [slotIndex] to/from the Super Knob. Linking (false -> true) arms
     * soft-takeover for that slot rather than snapping it to the Super Knob's current value.
     */
    fun setSlotLinked(slotIndex: Int, linked: Boolean) {
        if (slotIndex !in slotSuperKnobLink.indices) return
        val wasLinked = slotSuperKnobLink[slotIndex]
        slotSuperKnobLink[slotIndex] = linked
        if (linked && !wasLinked) {
            hasTakenOver[slotIndex] = false
        }
    }

    fun update() {
        superKnob.evaluate()
        propagateSuperKnob()
        slots.forEach { it?.update() }
        dryWet.evaluate()
    }

    private fun propagateSuperKnob() {
        val superValue = superKnob.value
        val superMoved = superValue != lastSuperKnobValue
        for (i in 0 until SLOT_COUNT) {
            if (!slotSuperKnobLink[i]) continue
            val slot = slots[i] ?: continue
            if (!hasTakenOver[i]) {
                if (!superMoved) continue
                val current = slot.metaKnob.value
                val crossed = (lastSuperKnobValue <= current && superValue >= current) ||
                    (lastSuperKnobValue >= current && superValue <= current)
                val closeEnough = kotlin.math.abs(superValue - current) <= TAKEOVER_TOLERANCE
                if (!crossed && !closeEnough) continue
                hasTakenOver[i] = true
            }
            slot.metaKnob.baseValue = superValue
        }
        lastSuperKnobValue = superValue
    }

    fun reset() {
        slots.forEach { it?.reset() }
        enabled = true
        dryWet.reset()
        superKnob.reset()
        for (i in 0 until SLOT_COUNT) {
            slotSuperKnobLink[i] = true
            hasTakenOver[i] = false
        }
        name = ""
    }

    fun dispose() {
        slots.forEach { it?.dispose() }
    }

    /** Exposes this chain's dry/wet, Super Knob, and slot parameters for macro/MIDI binding. */
    fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/DryWet" to dryWet)
        list.add("$prefix/Super" to superKnob)
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
            parameters = fx.parameters.mapValues { p -> p.value.toDto() },
            metaKnob = fx.metaKnob.toDto(),
            metaBinding = fx.metaBinding.toDto(),
            metaBindings = fx.metaBindings.map { it.toDto() }
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
                // Baked snapshot semantics: a saved slot's Metaknob binding/position is restored
                // exactly as saved, not re-resolved from the (possibly since-changed) auto-bind engine.
                if (!dto.metaBindings.isNullOrEmpty()) {
                    filter.applyMetaBindingsFromPreset(dto.metaBindings.map { it.toBinding() })
                } else {
                    dto.metaBinding?.let { filter.applyMetaBindingFromPreset(it.toBinding()) }
                }
                dto.metaKnob?.let { filter.metaKnob.applyDto(it) }
                slots[slotIndex] = filter
            }
        }
        // Loading a new filter into a linked slot shouldn't snap it to the Super Knob's current
        // position -- arm takeover the same way relinking does.
        armSlotTakeover(slotIndex)
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
        dto.superKnob?.let { superKnob.applyDto(it) }
        val linkFlags = dto.slotSuperKnobLink
        for (i in 0 until SLOT_COUNT) {
            slotSuperKnobLink[i] = linkFlags?.getOrNull(i) ?: true
            hasTakenOver[i] = false
        }
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
            slots = slotsList,
            superKnob = superKnob.toDto(),
            slotSuperKnobLink = slotSuperKnobLink.toList()
        )
    }

    companion object {
        const val SLOT_COUNT = 3
        private const val TAKEOVER_TOLERANCE = 0.04f
    }
}
