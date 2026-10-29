package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.rendering.FlameImporter
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.ui.ToastOverlay
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CompletableFuture

/** Turns a user-supplied `.flame` file into saved Fractal Flame presets (one per flame in the file). */
object FlameImportOps {
    private val logger = KotlinLogging.logger {}
    const val FOLDER = "library/presets/imported-flames"
    private const val MAX_BYTES = 8L * 1024 * 1024

    /** What an import wrote. [completion] is set on the value [importFile] returns: that one is empty until the import is done. */
    class Outcome(val files: List<File>, val notes: List<String>, val completion: CompletableFuture<Outcome>? = null)

    /**
     * Imports [source] on [PresetManager.presetIoExecutor] (it reads, parses and writes files, so never on thread 0) and
     * returns at once. When it finishes, a toast says how many flames were imported (or why it failed) and the notes on
     * dropped variations go to the log. The returned [Outcome] has no files yet; wait on its [Outcome.completion] for them.
     */
    fun importFile(source: File, folder: File = File(FOLDER)): Outcome {
        val done = CompletableFuture.supplyAsync({ importNow(source, folder) }, PresetManager.presetIoExecutor)
        done.whenComplete { outcome, error ->
            val text = if (outcome != null) {
                outcome.notes.forEach { logger.info { "Flame import: $it" } }
                "Imported ${outcome.files.size} flame(s) to ${folder.path}"
            } else {
                val cause = error.cause ?: error
                logger.warn(cause) { "Flame import failed for ${source.name}" }
                "Could not import ${source.name}: ${cause.message}"
            }
            // the toast is UI state: shown from the GL thread
            VisualSourceRegistry.runOnGlThread { ToastOverlay.show(text) }
        }
        return Outcome(emptyList(), emptyList(), done)
    }

    /** Reads [source], writes `<folder>/<name>.lsd` for each flame (never overwriting: `-2`, `-3`, ... is appended). Throws on unreadable or oversized input. Blocking: the preset IO thread (or a test). */
    internal fun importNow(source: File, folder: File): Outcome {
        require(source.isFile && source.length() <= MAX_BYTES) { "Not a readable .flame file under ${MAX_BYTES / 1024 / 1024} MB: ${source.name}" }
        val imported = FlameImporter.parse(source.readText(), source.nameWithoutExtension)
        folder.mkdirs()
        val files = ArrayList<File>()
        val notes = ArrayList<String>()
        for (flame in imported) {
            val stem = Garden.slug(flame.name)
            var n = 1
            var target = File(folder, "$stem.lsd")
            while (target.exists()) target = File(folder, "$stem-${++n}.lsd")
            val dto = FlameImporter.toPresetDto(flame, target.nameWithoutExtension)
            target.writeText(PresetManager.json.encodeToString(dto))
            files += target
            notes += flame.notes.map { "${flame.name}: $it" }
        }
        return Outcome(files, notes)
    }
}
