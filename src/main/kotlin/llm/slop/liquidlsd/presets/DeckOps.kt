package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBankSerializer
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.ExternalVideoSource
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

/** One change to what a deck holds; see [DeckOps.request]. */
sealed interface DeckChange {
    /** A bare generator. [force] re-applies even when the deck already holds [source] (reset to defaults). */
    data class Source(val source: VisualSource, val force: Boolean = false) : DeckChange
    data class Preset(val file: File) : DeckChange
    object Eject : DeckChange
    data class CopyFrom(val from: DeckSlot) : DeckChange
    data class MoveFrom(val from: DeckSlot) : DeckChange
    data class SwapWith(val other: DeckSlot) : DeckChange
}

/** Who asked: [MANUAL] follows the manual-load dirty preference and pushes undo; [QUEUE] (A/B, BG and FX queue advances, AutoVJ, session restore) follows the AutoVJ one and doesn't. */
enum class LoadOrigin { MANUAL, QUEUE }

/**
 * The single entry point for changing what a deck holds, the counterpart of [FxOps] and [TransitionOps].
 *
 * [request] runs on the calling (UI or queue) thread: it skips no-op changes, applies the dirty
 * guard, reads preset files on [PresetManager.presetIoExecutor], and queues the result.
 * [drainOnGlThread] applies the queue once per frame before rendering, so a deck is never swapped
 * mid-frame and GL resources are only touched on the GL thread. Applying a change also owns undo,
 * the macro-bank policy, the per-deck active-preset bookkeeping and the toast, so no caller has to
 * remember any of it.
 */
object DeckOps {
    private val logger = KotlinLogging.logger {}

    /** Supplies the mixer for the dirty check at request time. Wired by the UI; null skips the guard. */
    var mixerProvider: () -> Mixer? = { null }

    /** Asks the user about a dirty [DeckSlot], then runs the continuation if they agree. Null (tests) = proceed. */
    var prompt: ((DeckSlot, () -> Unit) -> Unit)? = null

    /** Receives a restore lambda for each undoable MANUAL change, called before the change is applied. */
    var undoSink: (((() -> Unit)) -> Unit)? = null

    /** Called after a change is applied, for UI-side follow-up (selection, sub-tab) that [presets] mustn't reach into. */
    var postApply: ((DeckSlot, DeckChange) -> Unit)? = null

    private class Op(
        val slot: DeckSlot,
        val change: DeckChange,
        val origin: LoadOrigin,
        val dto: DeckPresetDto? = null,
        val mtime: Long? = null
    )

    private val pending = ConcurrentLinkedQueue<Op>()

    /** Queues [change] for [slot]. Safe to call from any thread. */
    fun request(slot: DeckSlot, change: DeckChange, origin: LoadOrigin = LoadOrigin.MANUAL) {
        val mixer = mixerProvider()
        if (change is DeckChange.Source && mixer != null && isNoOp(slot.deck(mixer), change)) return
        guard(slot, mixer, origin) {
            when (change) {
                is DeckChange.Preset -> readPreset(slot, change.file, origin)
                else -> pending.offer(Op(slot, change, origin))
            }
        }
    }

    /** Re-picking the generator a deck already runs must not reset it (external video is always re-applied). */
    private fun isNoOp(deck: Deck, change: DeckChange.Source): Boolean =
        !change.force && !deck.isEmpty && deck.source !is ExternalVideoSource && deck.source.id == change.source.id

    private fun guard(slot: DeckSlot, mixer: Mixer?, origin: LoadOrigin, proceed: () -> Unit) {
        val deck = mixer?.let { slot.deck(it) }
        if (deck == null || !isDirty(deck, mixer)) return proceed()
        when (origin) {
            LoadOrigin.QUEUE -> when (UITheme.autoVjDirtyBehavior) {
                UITheme.AutoVjDirtyBehavior.SKIP -> logger.info { "Skipping queue load: ${slot.label} has unsaved changes" }
                UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> { autoSave(slot, deck); proceed() }
                UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> proceed()
            }
            LoadOrigin.MANUAL -> when (UITheme.manualLoadDirtyBehavior) {
                UITheme.ManualLoadDirtyBehavior.PROMPT -> prompt.let { if (it == null) proceed() else it(slot, proceed) }
                UITheme.ManualLoadDirtyBehavior.AUTO_SAVE -> { autoSave(slot, deck); proceed() }
                UITheme.ManualLoadDirtyBehavior.DISCARD -> proceed()
            }
        }
    }

    private fun autoSave(slot: DeckSlot, deck: Deck) {
        if (deck.source is ExternalVideoSource) return
        val active = PresetManager.activePreset(slot)
        val name = if (!active.isNullOrBlank() && active != "None") active
        else "AutoSave_${slot.label.replace(" ", "")}_${System.currentTimeMillis()}"
        PresetRepository.saveDeckPresetAsync(
            File("library/presets/$name.lsd"), deck, name, PresetManager.cachedDto(slot)?.tags ?: emptyList(), slot.index
        )
    }

    /** Reads, migrates and queues [file]. The returned future completes once queued (not applied); it exists for tests. */
    private fun readPreset(slot: DeckSlot, file: File, origin: LoadOrigin): CompletableFuture<Void> {
        PresetManager.deckStatus[slot.index].set(PresetIOStatus(PresetIOState.LOADING))
        val fileMtime = file.lastModified().takeIf { it > 0L }
        return CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Loading deck preset from ${file.absolutePath} in background..." }
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                val rawDto = PresetManager.json.decodeFromString<DeckPresetDto>(file.readText())
                val (dto, wasMigrated) = PresetMigrator.sanitizePresetDto(rawDto.copy(name = file.nameWithoutExtension))
                if (wasMigrated && file.canWrite()) {
                    try {
                        file.writeText(PresetManager.json.encodeToString(dto))
                        logger.info { "Auto-healed and migrated preset '${file.name}' to latest schema" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Could not auto-save migrated preset '${file.name}'" }
                    }
                }
                pending.offer(Op(slot, DeckChange.Preset(file), origin, dto, fileMtime))
                PresetManager.deckStatus[slot.index].set(PresetIOStatus(PresetIOState.IDLE))
            } catch (e: Exception) {
                logger.error(e) { "Failed to load deck preset from ${file.absolutePath}" }
                PresetManager.deckStatus[slot.index].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, PresetManager.presetIoExecutor)
    }

    /** Test hook: queues an already-decoded preset as if it had just been read from disk. */
    internal fun postLoaded(slot: DeckSlot, dto: DeckPresetDto, origin: LoadOrigin = LoadOrigin.MANUAL, mtime: Long? = null) {
        pending.offer(Op(slot, DeckChange.Preset(File("${dto.name}.lsd")), origin, dto, mtime))
    }

    /** Test hook: number of changes waiting for [drainOnGlThread]. */
    internal val pendingCount: Int get() = pending.size

    /** True when [deck] differs from the preset or generator it was loaded as, including its macro bank's labels and bindings. */
    fun isDirty(deck: Deck, mixer: Mixer): Boolean {
        val slot = DeckSlot.of(deck, mixer) ?: return false
        val cached = PresetManager.cachedDto(slot) ?: return false
        if (runCatching { deck.source is ExternalVideoSource }.getOrDefault(false)) return false
        if (deck.toDto(cached.name) != cached) return true
        val baseline = PresetManager.bankBaseline(slot) ?: return false
        return PresetManager.bankSignature(slot)?.let { it != baseline } ?: false
    }

    /** Applies every queued change. Main/GL thread only -- called once per frame from the render loop. */
    fun drainOnGlThread(mixer: Mixer) {
        var appliedAny = false
        while (true) {
            val op = pending.poll() ?: break
            appliedAny = true
            try {
                apply(op, mixer)
            } catch (e: Exception) {
                logger.error(e) { "Error applying ${op.change::class.simpleName} to ${op.slot.label}" }
            }
        }
        if (appliedAny) {
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
            // Notify broadcast engine if connected so full state is pushed immediately
            llm.slop.liquidlsd.broadcast.BroadcastEngine.notifyStateChanged()
        }
    }

    private fun apply(op: Op, mixer: Mixer) {
        val slot = op.slot
        val deck = slot.deck(mixer)
        val change = op.change
        val bindingsBefore = bindingsOf(slot)

        val undoPushed = op.origin == LoadOrigin.MANUAL && (change is DeckChange.Source || change is DeckChange.Preset) &&
            undoSink?.let { it(captureUndo(slot, deck, change)); true } == true

        val what = when (change) {
            is DeckChange.Source -> {
                deck.source = change.source.clone()
                deck.isEmpty = false
                GeneratorDefaults.applyToDeck(deck, slot.label, slot.bankId)
                // A fresh generator is its own baseline, so tweaking it counts as unsaved.
                PresetManager.setActive(slot, null, deck.toDto(deck.source.displayName))
                PresetManager.setMtime(slot, null)
                "${deck.source.displayName} defaults"
            }
            is DeckChange.Preset -> {
                val dto = op.dto ?: return
                deck.applyDto(dto)
                if (dto.macroBank != null) {
                    MacroBankSerializer.installPresetBank(slot.bankId, dto.macroBank, slot.label)
                } else {
                    GeneratorDefaults.installDefaultBank(deck, slot.label, slot.bankId)
                }
                PresetManager.setActive(
                    slot, dto.name,
                    deck.toDto(dto.name, dto.tags).copy(presetNotes = dto.presetNotes, paramNotes = dto.paramNotes)
                )
                PresetManager.setMtime(slot, op.mtime)
                NotesManager.syncFromDto(slot.label, dto)
                if (op.origin == LoadOrigin.MANUAL && (slot == DeckSlot.A || slot == DeckSlot.B)) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = slot == DeckSlot.A, isDeckPV = false, mixer = mixer)
                }
                logger.info { "Applied ${slot.label} preset: ${dto.name}" }
                "preset '${dto.name}'"
            }
            DeckChange.Eject -> {
                deck.reset()
                PresetManager.clearActive(slot)
                PresetManager.setMtime(slot, null)
                null
            }
            is DeckChange.CopyFrom -> {
                put(slot, snapshot(change.from, mixer), mixer)
                "${change.from.label}'s knobs"
            }
            is DeckChange.MoveFrom -> {
                put(slot, snapshot(change.from, mixer), mixer)
                clear(change.from, mixer)
                "${change.from.label}'s knobs"
            }
            is DeckChange.SwapWith -> {
                val mine = snapshot(slot, mixer)
                val theirs = snapshot(change.other, mixer)
                put(slot, theirs, mixer)
                put(change.other, mine, mixer)
                "${change.other.label}'s knobs"
            }
        }

        if (what != null && bindingsBefore.any { it.isNotEmpty() } && bindingsBefore != bindingsOf(slot)) {
            ToastOverlay.show("${slot.label} macro knobs replaced by $what (previous bindings replaced)" + if (undoPushed) ". Ctrl+Z to undo" else "")
        }
        postApply?.invoke(slot, change)
    }

    private fun bindingsOf(slot: DeckSlot) = PresetManager.bankSignature(slot)?.map { it.second } ?: emptyList()

    private fun bankFor(slot: DeckSlot): MacroBank =
        MacroEngine.getBank(slot.bankId) ?: MacroEngine.newBankFor(slot.bankId).also { MacroEngine.registerBank(slot.bankId, it) }

    /** What a copy, move or swap carries from one deck to another. */
    private class Snapshot(val dto: DeckPresetDto, val name: String?, val bank: MacroBank?, val empty: Boolean, val mtime: Long?)

    private fun snapshot(slot: DeckSlot, mixer: Mixer): Snapshot {
        val deck = slot.deck(mixer)
        val name = PresetManager.activePreset(slot)
        val dto = if (deck.isEmpty) PresetManager.emptyDeckDto(deck, mixer) else deck.toDto(name ?: deck.source.displayName)
        val bank = MacroEngine.getBank(slot.bankId)?.let { MacroBankSerializer.snapshotForPreset(it) }
        return Snapshot(dto, name, bank, deck.isEmpty, PresetManager.mtime(slot))
    }

    private fun put(slot: DeckSlot, snap: Snapshot, mixer: Mixer) {
        slot.deck(mixer).applyDto(snap.dto)
        // The bank's bindings are remapped from the source deck's path to this one (fixes Copy/Move/Swap leaving stale knobs).
        MacroBankSerializer.installBankForDeck(snap.bank, bankFor(slot), slot.label)
        if (snap.empty) {
            PresetManager.clearActive(slot)
            PresetManager.setMtime(slot, null)
        } else {
            PresetManager.setActive(slot, snap.name, snap.dto)
            PresetManager.setMtime(slot, snap.mtime)
        }
    }

    private fun clear(slot: DeckSlot, mixer: Mixer) {
        val deck = slot.deck(mixer)
        deck.applyDto(PresetManager.emptyDeckDto(deck, mixer))
        MacroBankSerializer.installBankForDeck(null, bankFor(slot), slot.label)
        PresetManager.clearActive(slot)
        PresetManager.setMtime(slot, null)
    }

    /**
     * Snapshot of everything a source change or preset load replaces; the returned lambda restores it.
     * A source change keeps the old source instance; a preset load rebuilds the deck from its DTO.
     * Eject and copy/move/swap push no undo: they can also reset the deck's FX chain, which isn't captured.
     */
    private fun captureUndo(slot: DeckSlot, deck: Deck, change: DeckChange): () -> Unit {
        val oldSource = deck.source
        val wasEmpty = deck.isEmpty
        val oldDto = if (change is DeckChange.Preset && oldSource !is ExternalVideoSource) deck.toDto(oldSource.displayName) else null
        val oldName = PresetManager.activePreset(slot)
        val oldCached = PresetManager.cachedDto(slot)
        val oldMtime = PresetManager.mtime(slot)
        val bank = MacroEngine.getBank(slot.bankId)
        val savedKnobs = bank?.knobs?.map { Triple(it.label, it.value, it.bindings.toList()) }
        return {
            if (oldDto != null && !wasEmpty) {
                deck.applyDto(oldDto)
            } else {
                deck.source = oldSource
                deck.isEmpty = wasEmpty
            }
            if (bank != null && savedKnobs != null) {
                bank.knobs.forEachIndexed { i, knob ->
                    val (label, value, bindings) = savedKnobs[i]
                    knob.label = label
                    knob.value = value
                    knob.bindings.clear()
                    knob.bindings.addAll(bindings)
                }
                MacroEngine.noteBankReplaced() // tell MacroUndoTracker this wasn't a hand edit
                MacroEngine.invalidate()
            }
            // After the bank, so the restored bank becomes the dirty baseline.
            PresetManager.setActive(slot, oldName, oldCached)
            PresetManager.setMtime(slot, oldMtime)
        }
    }
}
