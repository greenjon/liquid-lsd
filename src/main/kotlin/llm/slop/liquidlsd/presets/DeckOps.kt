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
import java.util.concurrent.atomic.AtomicLong

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

    /** Asks the user about a dirty [DeckSlot], then runs the first continuation if they agree and the second if they cancel. Null (tests) = proceed. */
    var prompt: ((DeckSlot, () -> Unit, () -> Unit) -> Unit)? = null

    /** Receives a restore lambda for each undoable MANUAL change, called before the change is applied. */
    var undoSink: (((() -> Unit)) -> Unit)? = null

    /** Called after a change is applied, for UI-side follow-up (selection, sub-tab) that [presets] mustn't reach into. */
    var postApply: ((DeckSlot, DeckChange) -> Unit)? = null

    private class Op(
        val slot: DeckSlot,
        val change: DeckChange,
        val origin: LoadOrigin,
        val dto: DeckPresetDto? = null,
        val mtime: Long? = null,
        val onResult: ((Boolean) -> Unit)? = null,
        val seq: Long = 0L
    )

    private val pending = ConcurrentLinkedQueue<Op>()

    /** Per-slot request order. Stamped when a request is accepted, so a slow preset read can't land after a newer change. */
    private val requestSeq = Array(DeckSlot.entries.size) { AtomicLong() }
    private val lastApplied = LongArray(DeckSlot.entries.size)

    private fun nextSeq(slot: DeckSlot): Long = requestSeq[slot.index].incrementAndGet()

    /**
     * Queues [change] for [slot]. Safe to call from any thread.
     * @return false if the change was dropped (a no-op source re-pick, or a QUEUE load skipped by
     * the AutoVJ dirty setting), so queue managers can leave their position unchanged. A MANUAL
     * change waiting on the dirty prompt counts as accepted.
     *
     * [onResult] is called exactly once when the outcome is known: true after the change has been
     * applied on the GL thread, false if it was dropped, the prompt was cancelled, the preset
     * couldn't be read, or applying failed. It may run on any thread.
     */
    fun request(
        slot: DeckSlot, change: DeckChange, origin: LoadOrigin = LoadOrigin.MANUAL,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val mixer = mixerProvider()
        if (change is DeckChange.Source && mixer != null && isNoOp(slot.deck(mixer), change)) {
            onResult?.invoke(false)
            return false
        }
        val accepted = guard(slot, mixer, origin, { onResult?.invoke(false) }) {
            val seq = nextSeq(slot)
            when (change) {
                is DeckChange.Preset -> readPreset(slot, change.file, origin, onResult, seq)
                else -> pending.offer(Op(slot, change, origin, onResult = onResult, seq = seq))
            }
        }
        if (!accepted) onResult?.invoke(false)
        return accepted
    }

    /**
     * True when a QUEUE load to [slot] would be dropped for unsaved changes (AutoVJ "skip"). Queue
     * managers that delay the load, like BG's dip-to-black, ask up front so they don't fade a deck they won't reload.
     */
    fun wouldSkipQueueLoad(slot: DeckSlot, mixer: Mixer): Boolean =
        UITheme.autoVjDirtyBehavior == UITheme.AutoVjDirtyBehavior.SKIP && isDirty(slot.deck(mixer), mixer)

    /** Re-picking the generator a deck already runs must not reset it (external video is always re-applied). */
    private fun isNoOp(deck: Deck, change: DeckChange.Source): Boolean =
        !change.force && !deck.isEmpty && deck.source !is ExternalVideoSource && deck.source.id == change.source.id

    private fun guard(slot: DeckSlot, mixer: Mixer?, origin: LoadOrigin, onCancel: () -> Unit = {}, proceed: () -> Unit): Boolean {
        val deck = mixer?.let { slot.deck(it) }
        if (deck == null || !isDirty(deck, mixer)) { proceed(); return true }
        when (origin) {
            LoadOrigin.QUEUE -> when (UITheme.autoVjDirtyBehavior) {
                UITheme.AutoVjDirtyBehavior.SKIP -> {
                    logger.info { "Skipping queue load: ${slot.label} has unsaved changes" }
                    return false
                }
                UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> { autoSave(slot, deck); proceed() }
                UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> proceed()
            }
            LoadOrigin.MANUAL -> when (UITheme.manualLoadDirtyBehavior) {
                UITheme.ManualLoadDirtyBehavior.PROMPT -> prompt.let { if (it == null) proceed() else it(slot, proceed, onCancel) }
                UITheme.ManualLoadDirtyBehavior.AUTO_SAVE -> { autoSave(slot, deck); proceed() }
                UITheme.ManualLoadDirtyBehavior.DISCARD -> proceed()
            }
        }
        return true
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
    private fun readPreset(slot: DeckSlot, file: File, origin: LoadOrigin, onResult: ((Boolean) -> Unit)?, seq: Long): CompletableFuture<Void> {
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
                pending.offer(Op(slot, DeckChange.Preset(file), origin, dto, fileMtime, onResult, seq))
                PresetManager.deckStatus[slot.index].set(PresetIOStatus(PresetIOState.IDLE))
            } catch (e: Exception) {
                logger.error(e) { "Failed to load deck preset from ${file.absolutePath}" }
                PresetManager.deckStatus[slot.index].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
                onResult?.invoke(false)
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, PresetManager.presetIoExecutor)
    }

    /** Test hook: queues an already-decoded preset as if it had just been read from disk. */
    internal fun postLoaded(slot: DeckSlot, dto: DeckPresetDto, origin: LoadOrigin = LoadOrigin.MANUAL, mtime: Long? = null, seq: Long = nextSeq(slot)) {
        pending.offer(Op(slot, DeckChange.Preset(File("${dto.name}.lsd")), origin, dto, mtime, seq = seq))
    }

    /** Test hook: the sequence number of the most recent accepted request for [slot]. */
    internal fun lastRequestedSeq(slot: DeckSlot): Long = requestSeq[slot.index].get()

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
            if (op.seq < lastApplied[op.slot.index]) {
                logger.info { "Dropping stale ${op.change::class.simpleName} for ${op.slot.label}: a newer change was already applied" }
                op.onResult?.invoke(false)
                continue
            }
            lastApplied[op.slot.index] = op.seq
            appliedAny = true
            var ok = false
            try {
                ok = apply(op, mixer)
            } catch (e: Exception) {
                logger.error(e) { "Error applying ${op.change::class.simpleName} to ${op.slot.label}" }
            }
            op.onResult?.invoke(ok)
        }
        if (appliedAny) {
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
            // Notify broadcast engine if connected so full state is pushed immediately
            llm.slop.liquidlsd.broadcast.BroadcastEngine.notifyStateChanged()
        }
    }

    /** @return false if nothing was applied. */
    private fun apply(op: Op, mixer: Mixer): Boolean {
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
                val dto = op.dto ?: return false
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
        return true
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
