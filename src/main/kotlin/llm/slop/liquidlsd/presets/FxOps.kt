package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The single entry point for changing what's loaded in an [FxChain] (a deck's or the master bus's).
 *
 * Every mutation is queued and applied on the GL/main thread in [drainOnGlThread], once per frame
 * before rendering, for two reasons:
 *  - Replacing a slot disposes the old [llm.slop.liquidlsd.rendering.isf.ISFFilter], which deletes
 *    its GL framebuffers -- that must never happen on [PresetManager.presetIoExecutor], where async
 *    file loads complete.
 *  - The renderer walks [FxChain.slots] during the frame; swapping them from another thread races it.
 *
 * After each applied op the chain's FX row knobs are re-synced ([FxMacroSync.syncFor]) so their
 * labels/bindings always reflect what's actually loaded, whichever UI triggered the change.
 */
object FxOps {
    private val logger = KotlinLogging.logger {}

    private val pending = ConcurrentLinkedQueue<(Mixer) -> Unit>()

    /**
     * Total length of the fade-out/fade-in "dip" wrapped around effect replacements, in seconds
     * (see [FxChain.scheduleSlotChange]). 0 = hard cut. Reads the user preference by default.
     */
    var fadeSec: () -> Float = { llm.slop.liquidlsd.ui.UITheme.fxSwapFadeMs / 1000f }

    /** Queues [op] for the next [drainOnGlThread]. Safe to call from any thread. */
    private fun post(op: (Mixer) -> Unit) {
        pending.offer(op)
    }

    /** Re-syncs [chain]'s FX row knobs; runs after the change itself has been applied. */
    private fun resync(chain: FxChain, mixer: Mixer) {
        FxMacroSync.bankIdFor(chain, mixer)?.let { FxMacroSync.syncFor(it, mixer) }
    }

    /** Queues a change to one slot, applied behind a slot dip. */
    private fun postSlotChange(chain: FxChain, slotIndex: Int, change: (FxChain) -> Unit) = post { mixer ->
        chain.scheduleSlotChange(slotIndex, fadeSec()) {
            val start = System.nanoTime()
            change(chain)
            logger.debug { "FX slot ${slotIndex + 1} change applied in ${"%.2f".format((System.nanoTime() - start) / 1e6)} ms" }
            resync(chain, mixer)
        }
    }

    /** Queues a change touching the whole chain, applied behind a chain dip. */
    private fun postChainChange(chain: FxChain, change: (FxChain) -> Unit) = post { mixer ->
        chain.scheduleChainChange(fadeSec()) {
            change(chain)
            resync(chain, mixer)
        }
    }

    /** Queues a change applied instantly, with no dip (toggles, link changes). */
    private fun postInstant(chain: FxChain, change: (FxChain) -> Unit) = post { mixer ->
        change(chain)
        resync(chain, mixer)
    }

    /** Applies every queued op. Main/GL thread only -- called once per frame from the render loop. */
    fun drainOnGlThread(mixer: Mixer) {
        while (true) {
            val op = pending.poll() ?: break
            try {
                op(mixer)
            } catch (e: Exception) {
                logger.error(e) { "Failed to apply queued FX operation" }
            }
        }
    }

    /** Replaces [chain]'s whole contents with [dto]. */
    fun applyChain(chain: FxChain, dto: FXChainDto, source: File? = null, isBaseline: Boolean = false) =
        postChainChange(chain) { it.applyFxChain(dto, source, isBaseline) }

    /** Empties all of [chain]'s slots. */
    fun clearChain(chain: FxChain) = postChainChange(chain) { c ->
        for (i in 0 until FxChain.SLOT_COUNT) c.clearFxSlot(i)
        c.baselineDto = null
        c.sourceFile = null
    }

    /** Empties all slots and resets name to Untitled. */
    fun newChain(chain: FxChain) = postChainChange(chain) { c ->
        for (i in 0 until FxChain.SLOT_COUNT) c.clearFxSlot(i)
        c.name = "Untitled"
        c.sourceFile = null
        c.baselineDto = null
    }

    /** Reverts [chain] to its loaded baseline DTO, if any. */
    fun revertChain(chain: FxChain) = postChainChange(chain) { c ->
        c.baselineDto?.let { baseline ->
            c.applyFxChain(baseline, c.sourceFile, isBaseline = true)
        }
    }

    /** Loads a saved chain file (.lsdfxchain) into [chain]. */
    fun loadChain(session: SessionContext, file: File, chain: FxChain) {
        session.presetRepository.loadFxChainAsync(file)
            .thenAccept { dto -> applyChain(chain, dto, source = file, isBaseline = true) }
            .exceptionally { e -> logger.error(e) { "Failed to load FX chain ${file.name}" }; null }
    }

    /** Puts a fresh instance of stock ISF filter [filterId] into [slotIndex], or clears the slot if null. */
    fun setSlotFilter(chain: FxChain, slotIndex: Int, filterId: String?) = postSlotChange(chain, slotIndex) { c ->
        replaceWithStock(c, slotIndex, filterId)
    }

    private fun replaceWithStock(c: FxChain, slotIndex: Int, filterId: String?) {
        if (filterId == null) {
            c.clearFxSlot(slotIndex)
            return
        }
        val filter = ISFFilterRegistry.createFilter(filterId)
        if (filter == null) {
            logger.warn { "Unknown ISF filter '$filterId', slot left unchanged" }
            return
        }
        c.slots[slotIndex]?.dispose()
        c.slots[slotIndex] = filter
        c.armSlotTakeover(slotIndex)
    }

    /**
     * Steps slot [slotIndex] to the next (+1) or previous (-1) effect in the [FxShortlist]. The
     * target is resolved when the change applies, not when it's requested, so several quick
     * steps queued behind one dip each advance from the previous step's result.
     */
    fun stepSlot(chain: FxChain, slotIndex: Int, dir: Int) = postSlotChange(chain, slotIndex) { c ->
        val candidates = ISFFilterRegistry.availableFilters.map { FxShortlist.Candidate(it.id, it.displayName, it.categories) }
        val nextId = FxShortlist.next(c.slots[slotIndex]?.id, dir, candidates)
        if (nextId != null && nextId != c.slots[slotIndex]?.id) replaceWithStock(c, slotIndex, nextId)
    }

    /** Clears [slotIndex]. */
    fun clearSlot(chain: FxChain, slotIndex: Int) = postSlotChange(chain, slotIndex) { it.clearFxSlot(slotIndex) }

    /** Resets [slotIndex]'s effect to its authored defaults, keeping the effect loaded. */
    fun resetSlot(chain: FxChain, slotIndex: Int) = postInstant(chain) { it.slots[slotIndex]?.reset() }

    /** Turns slot [slotIndex] on or off (instantly -- this is a performance kill switch). */
    fun setSlotEnabled(chain: FxChain, slotIndex: Int, enabled: Boolean) = postInstant(chain) { c ->
        c.slots[slotIndex]?.enabled = enabled
    }

    /**
     * Swaps slot [a] of [chainA] with slot [b] of [chainB]; within one chain this is a reorder.
     * With [copy], slot [a]'s effect is duplicated into [b] instead and [a] is left alone.
     */
    fun swapSlots(chainA: FxChain, a: Int, chainB: FxChain, b: Int, copy: Boolean = false) = post { mixer ->
        if (copy) {
            val dto = chainA.toFxSlotDto(a) ?: return@post
            chainB.scheduleSlotChange(b, fadeSec()) {
                chainB.applyFxSlot(b, dto)
                resync(chainB, mixer)
            }
        } else if (chainA === chainB) {
            chainA.scheduleChainChange(fadeSec()) {
                chainA.swapSlotWith(a, chainA, b)
                resync(chainA, mixer)
            }
        } else {
            // Across chains there's no single dip that covers both, so swap instantly and fade
            // both slots back in from silence.
            chainA.swapSlotWith(a, chainB, b)
            chainA.startFadeIn(a, fadeSec() / 2f)
            chainB.startFadeIn(b, fadeSec() / 2f)
            resync(chainA, mixer)
            resync(chainB, mixer)
        }
    }

    /** Loads a saved single-FX file (.lsdfx) into [slotIndex], leaving the other slots alone. */
    fun loadSlot(session: SessionContext, file: File, chain: FxChain, slotIndex: Int) {
        session.presetRepository.loadFxPresetAsync(file)
            .thenAccept { dto -> applySlot(chain, slotIndex, dto.slot) }
            .exceptionally { e -> logger.error(e) { "Failed to load FX preset ${file.name}" }; null }
    }

    /** Applies an already-loaded slot DTO (e.g. from the clipboard) to [slotIndex]. */
    fun applySlot(chain: FxChain, slotIndex: Int, dto: llm.slop.liquidlsd.models.FXSlotDto) =
        postSlotChange(chain, slotIndex) { it.applyFxSlot(slotIndex, dto) }

    /**
     * Applies a playlist/queue FX item deterministically -- always all 3 slots, never "first vacant
     * slot" -- so a playlist/queue step reproduces the exact same FX state every time:
     * a .lsdfxchain replaces the chain, a .lsdfx becomes slot 1 of an otherwise empty chain.
     */
    fun applyItem(session: SessionContext, file: File, chain: FxChain) {
        when (file.extension.lowercase()) {
            "lsdfxchain" -> loadChain(session, file, chain)
            "lsdfx" -> session.presetRepository.loadFxPresetAsync(file)
                .thenAccept { dto ->
                    applyChain(chain, FXChainDto(name = dto.name, tags = dto.tags, slots = listOf(dto.slot, null, null)))
                }
                .exceptionally { e -> logger.error(e) { "Failed to load FX preset ${file.name}" }; null }
            else -> logger.warn { "Unrecognized FX item extension for ${file.name}, ignoring" }
        }
    }

    /** Test hook: number of ops waiting for [drainOnGlThread]. */
    internal val pendingCount: Int get() = pending.size
}
