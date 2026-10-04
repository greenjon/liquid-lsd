package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.models.TransitionPresetDto
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

    /** Switches to the stock transition [id] (null or blank = the default crossfade). */
    fun setStock(id: String?) {
        pending.offer { mixer -> mixer.setTransition(id) }
    }

    /** Applies an already-decoded transition preset. */
    fun applyPreset(dto: TransitionPresetDto) {
        pending.offer { mixer -> mixer.applyTransitionPreset(dto) }
    }

    /**
     * Reads the `.lsdtrans` [file] off-thread and queues it. A missing or unreadable file shows a
     * toast and falls back to the stock transition named after the file. The returned future
     * completes once the change is queued (not applied); it exists for tests.
     */
    fun loadPreset(file: File): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            try {
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                applyPreset(PresetManager.json.decodeFromString<TransitionPresetDto>(file.readText()))
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
