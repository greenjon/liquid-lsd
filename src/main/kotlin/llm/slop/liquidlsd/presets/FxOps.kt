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

    /** Queues [op] for the next [drainOnGlThread]. Safe to call from any thread. */
    private fun post(chain: FxChain, op: (FxChain) -> Unit) {
        pending.offer { mixer ->
            op(chain)
            FxMacroSync.bankIdFor(chain, mixer)?.let { FxMacroSync.syncFor(it, mixer) }
        }
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
    fun applyChain(chain: FxChain, dto: FXChainDto) = post(chain) { it.applyFxChain(dto) }

    /** Empties all of [chain]'s slots. */
    fun clearChain(chain: FxChain) = post(chain) { c ->
        for (i in 0 until FxChain.SLOT_COUNT) c.clearFxSlot(i)
    }

    /** Loads a saved chain file (.lsdfxchain) into [chain]. */
    fun loadChain(session: SessionContext, file: File, chain: FxChain) {
        session.presetRepository.loadFxChainAsync(file)
            .thenAccept { dto -> applyChain(chain, dto) }
            .exceptionally { e -> logger.error(e) { "Failed to load FX chain ${file.name}" }; null }
    }

    /** Puts a fresh instance of stock ISF filter [filterId] into [slotIndex], or clears the slot if null. */
    fun setSlotFilter(chain: FxChain, slotIndex: Int, filterId: String?) = post(chain) { c ->
        if (filterId == null) {
            c.clearFxSlot(slotIndex)
        } else {
            val filter = ISFFilterRegistry.createFilter(filterId)
            if (filter == null) {
                logger.warn { "Unknown ISF filter '$filterId', slot left unchanged" }
            } else {
                c.slots[slotIndex]?.dispose()
                c.slots[slotIndex] = filter
                c.armSlotTakeover(slotIndex)
            }
        }
    }

    /** Clears [slotIndex]. */
    fun clearSlot(chain: FxChain, slotIndex: Int) = post(chain) { it.clearFxSlot(slotIndex) }

    /** Loads a saved single-FX file (.lsdfx) into [slotIndex], leaving the other slots alone. */
    fun loadSlot(session: SessionContext, file: File, chain: FxChain, slotIndex: Int) {
        session.presetRepository.loadFxPresetAsync(file)
            .thenAccept { dto -> post(chain) { it.applyFxSlot(slotIndex, dto.slot) } }
            .exceptionally { e -> logger.error(e) { "Failed to load FX preset ${file.name}" }; null }
    }

    /** Applies an already-loaded slot DTO (e.g. from the clipboard) to [slotIndex]. */
    fun applySlot(chain: FxChain, slotIndex: Int, dto: llm.slop.liquidlsd.models.FXSlotDto) =
        post(chain) { it.applyFxSlot(slotIndex, dto) }

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
