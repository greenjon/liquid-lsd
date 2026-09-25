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

    // -- Focus Mode (Traktor / Mixxx style) ---------------------------------------------------
    /**
     * Focused slot index (0 until [SLOT_COUNT]), or null if the chain is in standard Group/Chain mode.
     * In Focus Mode, Knob 1 maps to this slot's individual Dry/Wet, and Knobs 2-4 map to its top parameters.
     */
    var focusedSlot: Int? = null

    /**
     * Active parameter page (0-indexed) when in Focus Mode. Each page exposes up to 3 parameters.
     */
    var focusParamPage: Int = 0

    fun isFocused(): Boolean = focusedSlot != null

    fun focusSlot(slotIndex: Int?) {
        focusedSlot = if (slotIndex != null && slotIndex in 0 until SLOT_COUNT) slotIndex else null
        focusParamPage = 0
    }

    fun totalParamPages(slotIndex: Int = focusedSlot ?: 0): Int {
        val count = slots.getOrNull(slotIndex)?.parameters?.size ?: 0
        return if (count == 0) 1 else kotlin.math.ceil(count / 3.0).toInt().coerceAtLeast(1)
    }

    fun stepParamPage(dir: Int) {
        val total = totalParamPages()
        if (total <= 1) return
        focusParamPage = Math.floorMod(focusParamPage + dir, total)
    }

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

    // -- Fade on swap -------------------------------------------------------------------------
    // Replacing an effect mid-show is a hard visual cut (and feedback/trail effects restart from
    // an empty buffer), so FxOps routes replacements through a short "dip": ramp the slot's (or
    // the whole chain's) output gain to 0, apply the change at 0, ramp back to 1. The gains are
    // multiplied into the slot/chain wet in Renderer.renderFxChainPass, so the user's own dryWet
    // values and their modulation are never touched.

    /** Per-slot output gain, 0..1, animated by the dip. See [effectiveSlotWet]. */
    val slotGain = FloatArray(SLOT_COUNT) { 1f }

    /** Whole-chain output gain, 0..1, animated by the dip. See [effectiveChainWet]. */
    var chainGain = 1f
        private set

    private val slotPhase = IntArray(SLOT_COUNT) { FADE_IDLE }
    private val slotHalfSec = FloatArray(SLOT_COUNT)
    private val slotPending = arrayOfNulls<() -> Unit>(SLOT_COUNT)
    private var chainPhase = FADE_IDLE
    private var chainHalfSec = 0f
    private var chainPending: (() -> Unit)? = null
    private var lastFadeNanos = 0L

    /** Slot [slotIndex]'s wet amount as rendered: 0 if empty/disabled, else its dryWet times the dip gain. */
    fun effectiveSlotWet(slotIndex: Int): Float {
        val slot = slots[slotIndex] ?: return 0f
        if (!slot.enabled) return 0f
        return slot.dryWet.value * slotGain[slotIndex]
    }

    /** The chain's wet amount as rendered: 0 if bypassed, else its dryWet times the dip gain. */
    fun effectiveChainWet(): Float = if (enabled) dryWet.value * chainGain else 0f

    /** True while any dip is in progress (or waiting to apply its change). */
    val isFading: Boolean
        get() = chainPhase != FADE_IDLE || slotPhase.any { it != FADE_IDLE }

    /**
     * Applies [change] to slot [slotIndex] behind a dip lasting [fadeSec] in total (half out, half
     * in). If the slot is currently invisible (empty/disabled) or [fadeSec] <= 0 the change applies
     * immediately, fading the new effect in when there is a fade. Changes requested while a dip is
     * still fading out are chained and applied together at the bottom of the dip, so rapid
     * stepping never skips a request.
     */
    fun scheduleSlotChange(slotIndex: Int, fadeSec: Float, change: () -> Unit) {
        if (slotIndex !in 0 until SLOT_COUNT) return
        if (chainPhase == FADE_OUT) {
            // A whole-chain dip is already heading to 0: ride along with it.
            chainPending = chainPending.then(change)
            return
        }
        val visible = slots[slotIndex]?.enabled == true
        if (fadeSec <= 0f || (!visible && slotPhase[slotIndex] == FADE_IDLE)) {
            runSlotPending(slotIndex)
            change()
            if (fadeSec > 0f && slots[slotIndex] != null) startFadeIn(slotIndex, fadeSec / 2f) else resetSlotFade(slotIndex)
            return
        }
        slotPending[slotIndex] = slotPending[slotIndex].then(change)
        slotHalfSec[slotIndex] = fadeSec / 2f
        slotPhase[slotIndex] = FADE_OUT
    }

    /** Like [scheduleSlotChange], but dips the whole chain -- for loads/clears/reorders that touch every slot. */
    fun scheduleChainChange(fadeSec: Float, change: () -> Unit) {
        // Slot dips still waiting to apply go first, so requests stay in order.
        for (i in 0 until SLOT_COUNT) runSlotPending(i)
        if (fadeSec <= 0f || !enabled || (chainPhase == FADE_IDLE && (0 until SLOT_COUNT).none { effectiveSlotWet(it) > 0f })) {
            chainPending?.let { chainPending = null; it() }
            change()
            chainPhase = FADE_IDLE
            chainGain = 1f
            return
        }
        chainPending = chainPending.then(change)
        chainHalfSec = fadeSec / 2f
        chainPhase = FADE_OUT
    }

    /** Fades slot [slotIndex] in from silence over [sec] (used after an instant change, e.g. a cross-chain swap). */
    fun startFadeIn(slotIndex: Int, sec: Float) {
        if (sec <= 0f) { resetSlotFade(slotIndex); return }
        slotGain[slotIndex] = 0f
        slotHalfSec[slotIndex] = sec
        slotPhase[slotIndex] = FADE_IN
    }

    /** Advances every dip by [dtSec], applying pending changes at the bottom of each dip. GL thread only. */
    fun advanceFade(dtSec: Float) {
        if (chainPhase != FADE_IDLE) {
            val step = if (chainHalfSec > 0f) dtSec / chainHalfSec else 1f
            if (chainPhase == FADE_OUT) {
                chainGain = (chainGain - step).coerceAtLeast(0f)
                if (chainGain <= 0f) {
                    chainPending?.let { chainPending = null; it() }
                    chainPhase = FADE_IN
                }
            } else {
                chainGain = (chainGain + step).coerceAtMost(1f)
                if (chainGain >= 1f) chainPhase = FADE_IDLE
            }
        }
        for (i in 0 until SLOT_COUNT) {
            val phase = slotPhase[i]
            if (phase == FADE_IDLE) continue
            val step = if (slotHalfSec[i] > 0f) dtSec / slotHalfSec[i] else 1f
            if (phase == FADE_OUT) {
                slotGain[i] = (slotGain[i] - step).coerceAtLeast(0f)
                if (slotGain[i] <= 0f) {
                    runSlotPending(i)
                    if (slots[i] != null) slotPhase[i] = FADE_IN else resetSlotFade(i)
                }
            } else {
                slotGain[i] = (slotGain[i] + step).coerceAtMost(1f)
                if (slotGain[i] >= 1f) slotPhase[i] = FADE_IDLE
            }
        }
    }

    private fun runSlotPending(slotIndex: Int) {
        val pending = slotPending[slotIndex] ?: return
        slotPending[slotIndex] = null
        pending()
    }

    private fun resetSlotFade(slotIndex: Int) {
        slotPhase[slotIndex] = FADE_IDLE
        slotGain[slotIndex] = 1f
    }

    private fun (() -> Unit)?.then(next: () -> Unit): () -> Unit {
        val first = this ?: return next
        return { first(); next() }
    }

    /**
     * Swaps slot [a] of this chain with slot [b] of [other] (which may be this chain): the effect
     * instances move together with their Super Knob link flags, so modulation on their parameters
     * travels with them. Both slots re-arm soft takeover.
     */
    fun swapSlotWith(a: Int, other: FxChain, b: Int) {
        if (a !in 0 until SLOT_COUNT || b !in 0 until SLOT_COUNT) return
        if (other === this && a == b) return
        val filter = slots[a]
        slots[a] = other.slots[b]
        other.slots[b] = filter
        val link = slotSuperKnobLink[a]
        slotSuperKnobLink[a] = other.slotSuperKnobLink[b]
        other.slotSuperKnobLink[b] = link
        if (other === this) {
            if (focusedSlot == a) focusedSlot = b
            else if (focusedSlot == b) focusedSlot = a
        } else {
            if (focusedSlot == a) focusedSlot = null
            if (other.focusedSlot == b) other.focusedSlot = null
        }
        armSlotTakeover(a)
        other.armSlotTakeover(b)
    }

    fun update() {
        val now = System.nanoTime()
        if (lastFadeNanos != 0L && isFading) {
            advanceFade(((now - lastFadeNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f))
        }
        lastFadeNanos = now
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
        focusedSlot = null
        focusParamPage = 0
        name = ""
        for (i in 0 until SLOT_COUNT) { slotPending[i] = null; resetSlotFade(i) }
        chainPending = null
        chainPhase = FADE_IDLE
        chainGain = 1f
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

    var sourceFile: java.io.File? = null
    var baselineDto: FXChainDto? = null

    private var lastDirtyCheckTimeMs = 0L
    private var cachedIsDirty = false

    fun isDirty(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastDirtyCheckTimeMs >= 250) {
            lastDirtyCheckTimeMs = now
            cachedIsDirty = computeIsDirty()
        }
        return cachedIsDirty
    }

    fun computeIsDirty(): Boolean {
        val base = baselineDto ?: return slots.any { it != null }
        val current = toFxChainDto(base.name, base.tags)
        return current != base
    }

    fun markClean(file: java.io.File? = sourceFile) {
        sourceFile = file
        baselineDto = toFxChainDto(name)
        cachedIsDirty = false
        lastDirtyCheckTimeMs = System.currentTimeMillis()
    }

    fun applyFxChain(dto: FXChainDto, source: java.io.File? = null, isBaseline: Boolean = false) {
        name = dto.name
        sourceFile = source
        baselineDto = if (isBaseline) dto else null
        cachedIsDirty = false
        lastDirtyCheckTimeMs = 0L
        focusedSlot = null
        focusParamPage = 0
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
        private const val FADE_IDLE = 0
        private const val FADE_OUT = 1
        private const val FADE_IN = 2
    }
}
