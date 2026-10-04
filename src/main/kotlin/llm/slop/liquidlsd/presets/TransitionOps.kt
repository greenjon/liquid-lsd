package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.models.TransitionPresetDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.ToastOverlay
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The single entry point for changing the mixer's transition, the counterpart of [FxOps].
 *
 * Replacing a transition disposes the old ISF filter and creates a new one, which deletes and
 * allocates GL resources -- that must happen on the GL/main thread, never on
 * [PresetManager.presetIoExecutor] where async file loads complete. So every change is queued and
 * applied in [drainOnGlThread], once per frame before rendering. Safe to call from any thread.
 */
object TransitionOps {
    private val logger = KotlinLogging.logger {}

    private const val DEFAULT_ID = "linear_crossfade"

    private val pending = ConcurrentLinkedQueue<(Mixer) -> Unit>()

    /**
     * Receives a restore action just before an `undoable` change is applied (wired in `UIManager` to Ctrl+Z). Only picks made in
     * the UI ask for it; queue steps and session restore do not.
     */
    var undoSink: (((() -> Unit)) -> Unit)? = null

    /** Hands [undoSink] a restore for the transition the mixer runs now: its id, enabled flag, dry/wet and parameters. */
    private fun pushUndo(mixer: Mixer) {
        val sink = undoSink ?: return
        val filter = mixer.transitionFilter
        if (filter == null) {
            sink { setStock(null) }
            return
        }
        val dto = TransitionPresetDto(
            name = filter.id,
            slot = llm.slop.liquidlsd.models.FXSlotDto(
                filterId = filter.id,
                enabled = filter.enabled,
                dryWet = filter.dryWet.toDto(),
                parameters = filter.parameters.mapValues { it.value.toDto() }
            )
        )
        sink { applyPreset(dto) }
    }

    /** Switches to the stock transition [id] (null or blank = the default crossfade). */
    fun setStock(id: String?, undoable: Boolean = false) {
        pending.offer { mixer ->
            if (undoable) pushUndo(mixer)
            mixer.setTransition(id)
        }
    }

    /** Applies an already-decoded transition preset. */
    fun applyPreset(dto: TransitionPresetDto, undoable: Boolean = false) {
        pending.offer { mixer ->
            if (undoable) pushUndo(mixer)
            mixer.applyTransitionPreset(dto)
        }
    }

    /**
     * Reads the `.lsdtrans` [file] off-thread and queues it. A missing or unreadable file shows a
     * toast and falls back to the stock transition named after the file. The returned future
     * completes once the change is queued (not applied); it exists for tests.
     */
    fun loadPreset(file: File, undoable: Boolean = false): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            try {
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                applyPreset(PresetManager.json.decodeFromString<TransitionPresetDto>(file.readText()), undoable)
                logger.info { "Queued transition preset from ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load transition preset ${file.absolutePath}, falling back to stock transition" }
                ToastOverlay.show("Could not load transition ${file.name}; using ${stockIdFor(file)}")
                setStock(stockIdFor(file))
            }
        }, PresetManager.presetIoExecutor)

    /**
     * Applies a transition file picked from a queue, drop or browser: a `.lsdtrans` preset is
     * loaded, anything else (a `.fs` / `.isf` shader or a bare id) selects the stock transition.
     */
    fun applyItem(file: File) {
        if (file.extension.equals("lsdtrans", ignoreCase = true) && file.exists()) {
            loadPreset(file)
        } else {
            setStock(stockIdFor(file))
        }
    }

    internal fun stockIdFor(file: File): String =
        file.nameWithoutExtension.ifBlank { file.name.ifBlank { DEFAULT_ID } }

    /** Applies every queued change. Main/GL thread only -- called once per frame from the render loop. */
    fun drainOnGlThread(mixer: Mixer) {
        while (true) {
            val op = pending.poll() ?: break
            try {
                op(mixer)
            } catch (e: Exception) {
                logger.error(e) { "Failed to apply queued transition change" }
                ToastOverlay.show("A transition change could not be applied (see log)")
            }
        }
    }

    /** Test hook: number of changes waiting for [drainOnGlThread]. */
    internal val pendingCount: Int get() = pending.size
}
