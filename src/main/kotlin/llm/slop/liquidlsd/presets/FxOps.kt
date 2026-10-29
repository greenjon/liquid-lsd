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

    /** The first empty slot of [chain], or null when all [FxChain.SLOT_COUNT] are in use. Where a bare single FX goes when no slot is named. */
    fun firstVacantSlot(chain: FxChain): Int? = chain.slots.indexOfFirst { it == null }.takeIf { it >= 0 }

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

    /**
     * Receives a restore action just before an `undoable` change is applied (wired in `UIManager` to Ctrl+Z). Only changes the UI
     * asks to be undoable push one (picks, clears, pastes, drops, swaps, resets); queues, shortlist stepping, macros and session
     * restore never do.
     */
    var undoSink: (((() -> Unit)) -> Unit)? = null

    /** Hands [undoSink] a restore for [slotIndex]'s current contents (the saved slot DTO, or an empty slot). */
    private fun pushSlotUndo(chain: FxChain, slotIndex: Int) {
        val dto = chain.toFxSlotDto(slotIndex)
        undoSink?.invoke { if (dto != null) applySlot(chain, slotIndex, dto) else clearSlot(chain, slotIndex) }
    }

    /** Hands [undoSink] one restore putting back both slots of a swap. */
    private fun pushSwapUndo(chainA: FxChain, a: Int, chainB: FxChain, b: Int) {
        val dtoA = chainA.toFxSlotDto(a)
        val dtoB = chainB.toFxSlotDto(b)
        undoSink?.invoke {
            if (dtoA != null) applySlot(chainA, a, dtoA) else clearSlot(chainA, a)
            if (dtoB != null) applySlot(chainB, b, dtoB) else clearSlot(chainB, b)
        }
    }

    /** Hands [undoSink] a restore for the whole chain: its DTO, the file it came from and its dirty baseline. */
    private fun pushChainUndo(chain: FxChain) {
        val dto = chain.toFxChainDto()
        val file = chain.sourceFile
        val baseline = chain.baselineDto
        undoSink?.invoke {
            postChainChange(chain) { c ->
                c.applyFxChain(dto, file, isBaseline = false)
                c.baselineDto = baseline
            }
        }
    }

    /** Queues a change to one slot, applied behind a slot dip. */
    private fun postSlotChange(chain: FxChain, slotIndex: Int, undoable: Boolean = false, change: (FxChain) -> Unit) = post { mixer ->
        chain.scheduleSlotChange(slotIndex, fadeSec()) {
            val start = System.nanoTime()
            if (undoable) pushSlotUndo(chain, slotIndex)
            change(chain)
            logger.debug { "FX slot ${slotIndex + 1} change applied in ${"%.2f".format((System.nanoTime() - start) / 1e6)} ms" }
            resync(chain, mixer)
        }
    }

    /** Queues a change touching the whole chain, applied behind a chain dip. */
    private fun postChainChange(chain: FxChain, undoable: Boolean = false, change: (FxChain) -> Unit) = post { mixer ->
        chain.scheduleChainChange(fadeSec()) {
            if (undoable) pushChainUndo(chain)
            change(chain)
            resync(chain, mixer)
        }
    }

    /** Queues a change applied instantly, with no dip (toggles, link changes). */
    private fun postInstant(chain: FxChain, change: (FxChain) -> Unit) = post { mixer ->
        change(chain)
        resync(chain, mixer)
    }

    /** Logs a failed async load and tells the user; always returns null for `.exceptionally`. */
    private fun <T> reportFailure(message: String, e: Throwable): T? {
        logger.error(e) { message }
        llm.slop.liquidlsd.ui.ToastOverlay.show(message)
        return null
    }

    /** Applies every queued op. Main/GL thread only -- called once per frame from the render loop. */
    fun drainOnGlThread(mixer: Mixer) {
        while (true) {
            val op = pending.poll() ?: break
            try {
                op(mixer)
            } catch (e: Exception) {
                logger.error(e) { "Failed to apply queued FX operation" }
                llm.slop.liquidlsd.ui.ToastOverlay.show("An FX change could not be applied (see log)")
            }
        }
    }

    /**
     * Replaces [chain]'s whole contents with [dto]. A non-null [baseline] becomes the dirty baseline instead of what
     * [isBaseline] picks, so a chain carried over from another deck keeps its saved state.
     */
    fun applyChain(
        chain: FxChain, dto: FXChainDto, source: File? = null, isBaseline: Boolean = false, undoable: Boolean = false,
        baseline: FXChainDto? = null
    ) = postChainChange(chain, undoable) { c ->
        c.applyFxChain(dto, source, isBaseline)
        if (baseline != null) c.baselineDto = baseline
    }

    /** Empties all of [chain]'s slots and makes it a new, unsaved "Untitled" chain (so a later Save can't overwrite the old file). */
    fun clearChain(chain: FxChain, undoable: Boolean = false) = postChainChange(chain, undoable) { c ->
        for (i in 0 until FxChain.SLOT_COUNT) c.clearFxSlot(i)
        c.name = "Untitled"
        c.tags = emptyList()
        c.baselineDto = null
        c.sourceFile = null
    }

    /** Reverts [chain] to its loaded baseline DTO, if any. */
    fun revertChain(chain: FxChain, undoable: Boolean = false) = postChainChange(chain, undoable) { c ->
        c.baselineDto?.let { baseline ->
            c.applyFxChain(baseline, c.sourceFile, isBaseline = true)
        }
    }

    /**
     * Saves [chain] to [file] as [name] with [tags] and marks it clean against exactly what was written. The chain takes
     * the saved name and tags. A failed write puts back the previous file and baseline, so the unsaved marker returns.
     */
    fun saveChain(session: SessionContext, chain: FxChain, file: File, name: String = chain.name, tags: List<String> = chain.tags) {
        val prevSource = chain.sourceFile
        val prevBaseline = chain.baselineDto
        chain.name = name
        chain.tags = tags
        val dto = chain.toFxChainDto(name, tags)
        chain.markClean(file)
        session.presetRepository.saveFxChainAsync(file, name, dto, tags).thenAccept { ok ->
            if (!ok) post { chain.sourceFile = prevSource; chain.baselineDto = prevBaseline }
        }
    }

    /** Loads a saved chain file (.lsdfxchain) into [chain]. */
    fun loadChain(session: SessionContext, file: File, chain: FxChain, undoable: Boolean = false) {
        session.presetRepository.loadFxChainAsync(file)
            .thenAccept { dto -> applyChain(chain, dto, source = file, isBaseline = true, undoable = undoable) }
            .exceptionally { e -> reportFailure("Could not load FX chain ${file.name}", e) }
    }

    /** Puts a fresh instance of stock ISF filter [filterId] into [slotIndex], or clears the slot if null. */
    fun setSlotFilter(chain: FxChain, slotIndex: Int, filterId: String?, undoable: Boolean = false) = postSlotChange(chain, slotIndex, undoable) { c ->
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
            llm.slop.liquidlsd.ui.ToastOverlay.show("Unknown effect '$filterId'; the slot was left unchanged")
            return
        }
        c.slots[slotIndex]?.dispose()
        c.slots[slotIndex] = filter
        c.slotParamPage[slotIndex] = 0
        c.armSlotTakeover(slotIndex)
    }

    /**
     * Steps slot [slotIndex] to the next (+1) or previous (-1) effect in the [FxShortlist]. The
     * target is resolved when the change applies, not when it's requested, so several quick
     * steps queued behind one dip each advance from the previous step's result.
     */
    fun stepSlot(chain: FxChain, slotIndex: Int, dir: Int) = postSlotChange(chain, slotIndex) { c ->
        val nextId = FxShortlist.next(c.slots[slotIndex]?.id, dir, shortlistCandidates())
        if (nextId != null && nextId != c.slots[slotIndex]?.id) replaceWithStock(c, slotIndex, nextId)
    }

    /** Where slot [slotIndex]'s effect sits in the list ◀ / ▶ step through (see [FxShortlist.position]). */
    fun shortlistPosition(chain: FxChain, slotIndex: Int): String? =
        chain.slots.getOrNull(slotIndex)?.let { FxShortlist.position(it.id, shortlistCandidates()) }

    private fun shortlistCandidates() = ISFFilterRegistry.availableFilters.map { FxShortlist.Candidate(it.id, it.displayName, it.categories) }

    /** Clears [slotIndex]. */
    fun clearSlot(chain: FxChain, slotIndex: Int, undoable: Boolean = false) = postSlotChange(chain, slotIndex, undoable) { it.clearFxSlot(slotIndex) }

    /** Resets [slotIndex]'s effect to its authored defaults, keeping the effect loaded. */
    fun resetSlot(chain: FxChain, slotIndex: Int, undoable: Boolean = false) = postInstant(chain) { c ->
        if (undoable) pushSlotUndo(c, slotIndex)
        c.slots[slotIndex]?.reset()
    }

    /** Turns slot [slotIndex] on or off (instantly -- this is a performance kill switch). */
    fun setSlotEnabled(chain: FxChain, slotIndex: Int, enabled: Boolean) = postInstant(chain) { c ->
        c.slots[slotIndex]?.enabled = enabled
    }

    /**
     * Swaps slot [a] of [chainA] with slot [b] of [chainB]; within one chain this is a reorder.
     * With [copy], slot [a]'s effect is duplicated into [b] instead and [a] is left alone.
     */
    fun swapSlots(chainA: FxChain, a: Int, chainB: FxChain, b: Int, copy: Boolean = false, undoable: Boolean = false) = post { mixer ->
        if (copy) {
            val dto = chainA.toFxSlotDto(a) ?: return@post
            chainB.scheduleSlotChange(b, fadeSec()) {
                if (undoable) pushSlotUndo(chainB, b)
                chainB.applyFxSlot(b, dto)
                resync(chainB, mixer)
            }
        } else if (chainA === chainB) {
            chainA.scheduleChainChange(fadeSec()) {
                if (undoable) pushSwapUndo(chainA, a, chainB, b)
                chainA.swapSlotWith(a, chainA, b)
                resync(chainA, mixer)
            }
        } else {
            // Across chains there's no single dip that covers both, so swap instantly and fade
            // both slots back in from silence.
            if (undoable) pushSwapUndo(chainA, a, chainB, b)
            chainA.swapSlotWith(a, chainB, b)
            chainA.startFadeIn(a, fadeSec() / 2f)
            chainB.startFadeIn(b, fadeSec() / 2f)
            resync(chainA, mixer)
            resync(chainB, mixer)
        }
    }

    /** Loads a saved single-FX file (.lsdfx) into [slotIndex], leaving the other slots alone. */
    fun loadSlot(session: SessionContext, file: File, chain: FxChain, slotIndex: Int, undoable: Boolean = false) {
        session.presetRepository.loadFxPresetAsync(file)
            .thenAccept { dto ->
                postSlotChange(chain, slotIndex, undoable) { c ->
                    c.applyFxSlot(slotIndex, dto.slot)
                    c.slots[slotIndex]?.let { slotSources[it] = file }
                }
            }
            .exceptionally { e -> reportFailure("Could not load FX preset ${file.name}", e) }
    }

    /**
     * The saved `.lsdfx` each slot's effect was loaded from, keyed by the effect instance: any later change to the slot puts a new
     * instance there (so the entry no longer matches), and a swap or move carries its origin along. Weak, so cleared effects drop out.
     */
    private val slotSources: MutableMap<Any, File> = java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    /** The saved FX preset [chain]'s [slotIndex] was loaded from, or null for a stock effect, an empty slot or a slot changed since. */
    fun slotSourceFile(chain: FxChain, slotIndex: Int): File? = chain.slots.getOrNull(slotIndex)?.let { slotSources[it] }

    /** Follows renamed or moved `.lsdfx` files ([moves]: old absolute path to new) in every slot's recorded origin. */
    fun relocateSlotSources(moves: Map<String, String>) = synchronized(slotSources) {
        for (entry in slotSources.entries) moves[entry.value.absolutePath]?.let { entry.setValue(File(it)) }
    }

    /** Drops deleted `.lsdfx` files ([paths], absolute) from the slots' recorded origins. */
    fun forgetSlotSources(paths: Set<String>) = synchronized(slotSources) {
        slotSources.entries.removeIf { it.value.absolutePath in paths }
    }

    /** Applies an already-loaded slot DTO (e.g. from the clipboard) to [slotIndex]. */
    fun applySlot(chain: FxChain, slotIndex: Int, dto: llm.slop.liquidlsd.models.FXSlotDto, undoable: Boolean = false) =
        postSlotChange(chain, slotIndex, undoable) { it.applyFxSlot(slotIndex, dto) }

    /**
     * Applies a dropped library file to [chain]: a `.lsdfxchain` replaces the chain, a `.lsdfx` goes to
     * [slot], or to the first vacant slot when [slot] is null.
     * @return false when nothing was applied (unknown extension, or no slot named and the chain is full).
     */
    fun dropAsset(session: SessionContext, file: File, chain: FxChain, slot: Int? = null, undoable: Boolean = false): Boolean {
        when (file.extension.lowercase()) {
            "lsdfxchain" -> loadChain(session, file, chain, undoable)
            "lsdfx" -> loadSlot(session, file, chain, slot ?: firstVacantSlot(chain) ?: return false, undoable)
            else -> return false
        }
        return true
    }

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
                .exceptionally { e -> reportFailure("Could not load FX preset ${file.name}", e) }
            else -> {
                logger.warn { "Unrecognized FX item extension for ${file.name}, ignoring" }
                llm.slop.liquidlsd.ui.ToastOverlay.show("Can't apply ${file.name}: not an FX file")
            }
        }
    }

    /** Test hook: number of ops waiting for [drainOnGlThread]. */
    internal val pendingCount: Int get() = pending.size
}
