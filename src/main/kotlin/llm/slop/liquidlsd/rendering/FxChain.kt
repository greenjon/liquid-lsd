package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.SoftTakeover
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.rendering.isf.toBinding
import llm.slop.liquidlsd.rendering.isf.toDto

/**
 * An individual FX chain holding [SLOT_COUNT] serial ISF filter slots,
 * with its own chain-level [dryWet] blend and [enabled] bypass toggle.
 * Each deck owns one ([Deck.fxChain]), and so does the master bus ([Mixer.masterFxChain]).
 *
 * [superKnob] is a Traktor/Mixxx-style chain macro: when a slot is linked
 * ([slotSuperKnobLink]), moving the Super Knob drives that slot's own [ISFFilter.metaKnob].
 * How it drives it is [superKnobMode] (see [propagateSuperKnob]); neither mode ever yanks a
 * Metaknob to wherever the Super Knob currently sits when a slot is linked, loaded or hand-set.
 */
class FxChain(val label: String) {

    var name: String = ""
    /** Tags of the chain file this came from, written back on Save so overwriting a file keeps them. */
    var tags: List<String> = emptyList()
    val slots = arrayOfNulls<ISFFilter>(SLOT_COUNT)
    val dryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    var enabled: Boolean = true

    val superKnob = ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val slotSuperKnobLink: BooleanArray = BooleanArray(SLOT_COUNT) { defaultLinked() }

    // -- Focus Mode (Traktor / Mixxx style) ---------------------------------------------------
    /**
     * Focused slot index (0 until [SLOT_COUNT]), or null if the chain is in Group mode.
     * In Focus Mode, Knob 1 maps to this slot's Metaknob, and Knobs 2-4 map to its top parameters.
     */
    var focusedSlot: Int? = null

    /**
     * Each slot's parameter page (0-indexed; a page exposes up to 3 parameters). Per slot so the focus-mode FX row and the
     * slot's own Chain-view row ([llm.slop.liquidlsd.macro.FxMacroSync.slotBankId]) always show the same page. A page
     * follows its effect: it resets when a slot gets a new effect and moves with the effect on a swap.
     */
    val slotParamPage = IntArray(SLOT_COUNT)

    /** The focused slot's [slotParamPage] entry; 0 (and not writable) in Group mode. */
    var focusParamPage: Int
        get() = focusedSlot?.let { slotParamPage[it] } ?: 0
        set(value) { focusedSlot?.let { slotParamPage[it] = value } }

    fun isFocused(): Boolean = focusedSlot != null

    /** The enabled slots' Dry/Wets and inputs, for the dice; each one rolls only where its randomizeBase flag is on. */
    fun slotRandomizableParameters(): List<ModulatableParameter> =
        slots.filterNotNull().filter { it.enabled }.flatMap { listOf(it.dryWet) + it.parameters.values }

    fun focusSlot(slotIndex: Int?) {
        focusedSlot = if (slotIndex != null && slotIndex in 0 until SLOT_COUNT) slotIndex else null
    }

    fun totalParamPages(slotIndex: Int = focusedSlot ?: 0): Int {
        val count = slots.getOrNull(slotIndex)?.focusParameters?.size ?: 0
        return if (count == 0) 1 else kotlin.math.ceil(count / 3.0).toInt().coerceAtLeast(1)
    }

    /** Pulls every slot's [slotParamPage] back onto its last page after an effect or knob layout changed. */
    fun clampParamPage() {
        for (i in 0 until SLOT_COUNT) slotParamPage[i] = slotParamPage[i].coerceIn(0, totalParamPages(i) - 1)
    }

    /** Steps [slotIndex]'s parameter page by [dir], wrapping; defaults to the focused slot (no-op in Group mode). */
    fun stepParamPage(dir: Int, slotIndex: Int? = focusedSlot) {
        if (slotIndex == null || slotIndex !in 0 until SLOT_COUNT) return
        val total = totalParamPages(slotIndex)
        if (total <= 1) return
        slotParamPage[slotIndex] = Math.floorMod(slotParamPage[slotIndex] + dir, total)
    }

    // Armed (false) until the Super Knob's movement converges with a linked slot's current
    // Metaknob value -- see propagateSuperKnob(). Starts false for every slot: a freshly
    // constructed chain has nothing to "already be in sync" with.
    private val hasTakenOver = BooleanArray(SLOT_COUNT) { false }
    private var lastSuperKnobValue = superKnob.value
    // The Metaknob value the last propagation wrote, per slot. A taken-over Metaknob that no longer holds it was moved
    // by something else (its own knob, a slider, MIDI, an edit of a parameter it owns), which re-arms the takeover.
    private val lastPropagated = FloatArray(SLOT_COUNT) { Float.NaN }
    // RELATIVE mode anchors: the Super Knob position and Metaknob value a slot's line passes through. NaN = re-anchor.
    private val anchorSuper = FloatArray(SLOT_COUNT) { Float.NaN }
    private val anchorMeta = FloatArray(SLOT_COUNT)

    /**
     * Arms soft-takeover for [slotIndex] so its (possibly freshly-loaded) filter's Metaknob
     * doesn't snap to the Super Knob's current value until the Super Knob next moves. Callers
     * that assign directly into [slots] (bypassing [applyFxSlot], e.g. the slot picker UI) must
     * call this after doing so.
     */
    fun armSlotTakeover(slotIndex: Int) {
        if (slotIndex !in hasTakenOver.indices) return
        hasTakenOver[slotIndex] = false
        lastPropagated[slotIndex] = Float.NaN
        anchorSuper[slotIndex] = Float.NaN
    }

    /** True while linked slot [slotIndex] waits for the Super Knob to reach its Metaknob (soft takeover not caught yet). */
    fun isAwaitingPickup(slotIndex: Int): Boolean =
        superKnobMode() == SuperKnobMode.PICKUP &&
            slotSuperKnobLink.getOrNull(slotIndex) == true && slots.getOrNull(slotIndex) != null && !hasTakenOver[slotIndex]

    /**
     * Links or unlinks [slotIndex] to/from the Super Knob. Linking (false -> true) arms
     * soft-takeover for that slot rather than snapping it to the Super Knob's current value.
     */
    fun setSlotLinked(slotIndex: Int, linked: Boolean) {
        if (slotIndex !in slotSuperKnobLink.indices) return
        val wasLinked = slotSuperKnobLink[slotIndex]
        slotSuperKnobLink[slotIndex] = linked
        if (linked && !wasLinked) armSlotTakeover(slotIndex)
    }

    /** Filled slots only: an empty slot has nothing to drive, so it never makes the chain "partially" linked. */
    private fun filledSlotIndices() = (0 until SLOT_COUNT).filter { slots[it] != null }

    fun linkedSlotCount(): Int = filledSlotIndices().count { slotSuperKnobLink[it] }

    fun filledSlotCount(): Int = filledSlotIndices().size

    fun areAllSlotsLinked(): Boolean = filledSlotCount() > 0 && linkedSlotCount() == filledSlotCount()

    fun areAllSlotsUnlinked(): Boolean = linkedSlotCount() == 0

    /** Goes through [setSlotLinked] so linking arms soft-takeover instead of snapping slots to the Super Knob. */
    fun setAllSlotsLinked(linked: Boolean) {
        for (i in 0 until SLOT_COUNT) setSlotLinked(i, linked)
    }

    /** All linked -> unlink all; anything else (none or some linked) -> link all. */
    fun toggleAllSlotsLinked() = setAllSlotsLinked(!areAllSlotsLinked())

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
     * instances move, so modulation on their parameters travels with them. Super Knob link flags
     * belong to the slot position and stay put, as they do for duplicate, paste and load. Both slots
     * re-arm soft takeover (or re-anchor, in Relative mode).
     */
    fun swapSlotWith(a: Int, other: FxChain, b: Int) {
        if (a !in 0 until SLOT_COUNT || b !in 0 until SLOT_COUNT) return
        if (other === this && a == b) return
        val filter = slots[a]
        slots[a] = other.slots[b]
        other.slots[b] = filter
        val page = slotParamPage[a]
        slotParamPage[a] = other.slotParamPage[b]
        other.slotParamPage[b] = page
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

    // A slot that sat out frames (bypassed, at 0% wet, its chain off, or its deck not drawn) still holds the feedback/trail
    // buffers from when it stopped; drawn as they are, they flash that old picture. Frames are counted in [update].
    private var updateCount = 0L
    private val lastRenderedUpdate = LongArray(SLOT_COUNT) { Long.MIN_VALUE }

    /** Notes that slot [slotIndex] renders this frame; true when it did not render last frame, so its history is stale. */
    fun noteSlotRendered(slotIndex: Int): Boolean {
        val resumed = lastRenderedUpdate[slotIndex] < updateCount - 1
        lastRenderedUpdate[slotIndex] = updateCount
        return resumed
    }

    fun update() {
        updateCount++
        val now = llm.slop.liquidlsd.utils.TimeSource.getTimeNanos()
        if (lastFadeNanos != 0L && isFading) {
            advanceFade(((now - lastFadeNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f))
        }
        lastFadeNanos = now
        superKnob.evaluate()
        propagateSuperKnob()
        slots.forEach { it?.update() }
        dryWet.evaluate()
    }

    /**
     * [SuperKnobMode.PICKUP]: a linked slot waits until the Super Knob reaches its Metaknob (soft takeover), then
     * follows it exactly. [SuperKnobMode.RELATIVE]: each linked slot keeps an anchor (Super Knob position, Metaknob
     * value) and follows a two-piece line through it from 0 to 0 and 1 to 1, so it moves at once, keeps its own
     * position relative to the others and still reaches both ends. Any outside write to a Metaknob (its knob, a
     * slider, MIDI) re-arms the takeover or re-anchors the slot.
     */
    private fun propagateSuperKnob() {
        val superValue = superKnob.value
        val superMoved = superValue != lastSuperKnobValue
        val relative = superKnobMode() == SuperKnobMode.RELATIVE
        for (i in 0 until SLOT_COUNT) {
            if (!slotSuperKnobLink[i]) continue
            val slot = slots[i] ?: continue
            if (relative) {
                propagateRelative(i, slot, superValue, superMoved)
                continue
            }
            if (hasTakenOver[i] && slot.metaKnob.baseValue != lastPropagated[i]) armSlotTakeover(i)
            if (!hasTakenOver[i]) {
                if (!superMoved) continue
                val current = slot.metaKnob.value
                if (!SoftTakeover.caught(lastSuperKnobValue, superValue, current, TAKEOVER_TOLERANCE)) continue
                hasTakenOver[i] = true
            }
            slot.metaKnob.baseValue = superValue
            lastPropagated[i] = slot.metaKnob.baseValue
        }
        lastSuperKnobValue = superValue
    }

    private fun propagateRelative(i: Int, slot: ISFFilter, superValue: Float, superMoved: Boolean) {
        // Pickup state is meaningless here; clear it so switching to PICKUP mid-show asks for a pickup instead of snapping.
        hasTakenOver[i] = false
        val meta = slot.metaKnob.baseValue
        if (anchorSuper[i].isNaN() || meta != lastPropagated[i]) {
            anchorSuper[i] = superValue
            anchorMeta[i] = meta
            lastPropagated[i] = meta
            return
        }
        if (!superMoved) return
        slot.metaKnob.baseValue = relativeValue(superValue, anchorSuper[i], anchorMeta[i])
        lastPropagated[i] = slot.metaKnob.baseValue
    }

    fun reset() {
        slots.forEach { it?.reset() }
        enabled = true
        dryWet.reset()
        superKnob.reset()
        for (i in 0 until SLOT_COUNT) {
            slotSuperKnobLink[i] = defaultLinked()
            armSlotTakeover(i)
        }
        focusedSlot = null
        slotParamPage.fill(0)
        name = ""
        tags = emptyList()
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
            metaBinding = fx.metaBindings.firstOrNull()?.toDto(),
            metaBindings = fx.metaBindings.map { it.toDto() },
            knobLayout = fx.knobLayout
        )
    }

    fun applyFxSlot(slotIndex: Int, dto: FXSlotDto) {
        if (slotIndex !in slots.indices) return
        slots[slotIndex]?.dispose()
        slots[slotIndex] = null
        slotParamPage[slotIndex] = 0

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
                // An empty list is a saved "nothing linked"; only files from before multi-links (no list) use metaBinding.
                if (dto.metaBindings != null) {
                    filter.applyMetaBindingsFromPreset(dto.metaBindings.map { it.toBinding() })
                } else {
                    dto.metaBinding?.let { filter.applyMetaBindingFromPreset(it.toBinding()) }
                }
                dto.metaKnob?.let { filter.metaKnob.applyDto(it) }
                filter.applyKnobLayoutFromPreset(dto.knobLayout)
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
            slotParamPage[slotIndex] = 0
        }
    }

    var sourceFile: java.io.File? = null
    var baselineDto: FXChainDto? = null

    private var lastDirtyCheckTimeMs = 0L
    private var cachedIsDirty = false

    fun isDirty(): Boolean {
        val now = llm.slop.liquidlsd.utils.TimeSource.getEpochMillis()
        if (now - lastDirtyCheckTimeMs >= 250) {
            lastDirtyCheckTimeMs = now
            cachedIsDirty = computeIsDirty()
        }
        return cachedIsDirty
    }

    /**
     * True when the chain differs from its baseline in anything a chain file stores, except the positions a performance moves:
     * the Super Knob, each Metaknob, and the values the Metaknobs drive. Their modulators, links and targets still count.
     * Floats compare with a small tolerance, since a knob placed on a value and mapped back can land a rounding error away.
     */
    fun computeIsDirty(): Boolean {
        val base = baselineDto ?: return slots.any { it != null && it.id.isNotBlank() }
        val current = toFxChainDto()
        if (!sameParam(current.dryWet, base.dryWet)) return true
        if (!sameParam(current.superKnob, base.superKnob, ignoreValue = true)) return true
        if (linkFlags(current) != linkFlags(base)) return true
        for (i in 0 until SLOT_COUNT) {
            if (!sameSlot(current.slots.getOrNull(i), base.slots.getOrNull(i))) return true
        }
        return false
    }

    private fun linkFlags(dto: FXChainDto): List<Boolean> = List(SLOT_COUNT) { dto.slotSuperKnobLink?.getOrNull(it) ?: defaultLinked() }

    private fun sameSlot(a: FXSlotDto?, b: FXSlotDto?): Boolean {
        val aId = a?.filterId?.takeIf { it.isNotBlank() }
        val bId = b?.filterId?.takeIf { it.isNotBlank() }
        if (aId != bId) return false
        if (a == null || b == null || aId == null) return true
        if (a.enabled != b.enabled || a.knobLayout != b.knobLayout) return false
        val links = metaLinks(a)
        if (links != metaLinks(b)) return false
        if (!sameParam(a.metaKnob, b.metaKnob, ignoreValue = true)) return false
        // What an enabled Metaknob target drives follows the Metaknob, so only its modulators etc. count (null = Dry/Wet).
        val owned = links.filter { it.enabled }.map { it.targetParamName }.toSet()
        if (!sameParam(a.dryWet, b.dryWet, ignoreValue = null in owned)) return false
        // Parameters on one side only (a shader that gained or lost an input since saving) have nothing to compare against.
        for ((name, p) in a.parameters) {
            val q = b.parameters[name] ?: continue
            if (!sameParam(p, q, ignoreValue = name in owned)) return false
        }
        return true
    }

    /** A slot's Metaknob targets; files from before multi-targets have only the single [FXSlotDto.metaBinding]. */
    private fun metaLinks(dto: FXSlotDto) = dto.metaBindings ?: listOfNotNull(dto.metaBinding)

    private fun sameParam(a: ParameterDto?, b: ParameterDto?, ignoreValue: Boolean = false): Boolean {
        if (a == null || b == null) return a == b
        if (a.copy(baseValue = 0f) != b.copy(baseValue = 0f)) return false
        if (ignoreValue) return true
        val scale = maxOf(1f, kotlin.math.abs(a.baseValue), kotlin.math.abs(b.baseValue))
        return kotlin.math.abs(a.baseValue - b.baseValue) <= DIRTY_TOLERANCE * scale
    }

    fun markClean(file: java.io.File? = sourceFile) {
        sourceFile = file
        baselineDto = toFxChainDto(name)
        cachedIsDirty = false
        lastDirtyCheckTimeMs = llm.slop.liquidlsd.utils.TimeSource.getEpochMillis()
    }

    fun applyFxChain(dto: FXChainDto, source: java.io.File? = null, isBaseline: Boolean = false) {
        name = dto.name
        tags = dto.tags
        sourceFile = source
        baselineDto = if (isBaseline) dto else null
        cachedIsDirty = false
        lastDirtyCheckTimeMs = 0L
        focusedSlot = null
        slotParamPage.fill(0)
        dto.dryWet?.let { dryWet.applyDto(it) }
        dto.superKnob?.let { superKnob.applyDto(it) }
        val linkFlags = dto.slotSuperKnobLink
        for (i in 0 until SLOT_COUNT) {
            slotSuperKnobLink[i] = linkFlags?.getOrNull(i) ?: defaultLinked()
            armSlotTakeover(i)
        }
        for (i in slots.indices) {
            clearFxSlot(i)
            val slotDto = dto.slots.getOrNull(i)
            if (slotDto != null && slotDto.filterId.isNotBlank()) {
                applyFxSlot(i, slotDto)
            }
        }
    }

    fun toFxChainDto(chainName: String = name, tags: List<String> = this.tags): FXChainDto {
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

        /**
         * Whether a chain with no saved link flags starts with its slots linked to the Super Knob.
         * Set from the "Default FX chain linking" preference at startup; tests get a fixed `true`.
         */
        var defaultLinked: () -> Boolean = { true }

        /** How linked slots follow the Super Knob. Set from the "Super Knob response" preference at startup; tests get PICKUP. */
        var superKnobMode: () -> SuperKnobMode = { SuperKnobMode.PICKUP }

        /** RELATIVE mode: the Metaknob value at Super Knob [s] on the line 0 -> 0, ([s0], [m0]), 1 -> 1. */
        fun relativeValue(s: Float, s0: Float, m0: Float): Float = when {
            s <= s0 -> if (s0 <= 0f) m0 else m0 * (s / s0)
            s0 >= 1f -> m0
            else -> m0 + (1f - m0) * (s - s0) / (1f - s0)
        }.coerceIn(0f, 1f)
        private const val TAKEOVER_TOLERANCE = 0.04f
        /** Relative float tolerance of [computeIsDirty]. */
        private const val DIRTY_TOLERANCE = 1e-4f
        private const val FADE_IDLE = 0
        private const val FADE_OUT = 1
        private const val FADE_IN = 2
    }
}

/** How a linked FX slot's Metaknob follows its chain's Super Knob (see [FxChain.propagateSuperKnob]). */
enum class SuperKnobMode { PICKUP, RELATIVE }
