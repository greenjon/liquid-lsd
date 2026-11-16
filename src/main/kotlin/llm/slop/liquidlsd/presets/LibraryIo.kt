package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import mu.KotlinLogging
import java.io.File

/**
 * Small library and settings writes (playlists, favorites, recents, key bindings, the session autosave) done on
 * [PresetManager.presetIoExecutor] instead of the render thread. The caller takes what to write on Thread 0 and hands it
 * over; the one IO thread writes in the order asked, so a later save of the same file always lands last.
 */
object LibraryIo {
    private val logger = KotlinLogging.logger {}

    /** Writes [text] to [file] atomically on the IO thread. A failure is logged as "Could not save [what]". */
    fun writeLater(file: File, text: String, what: String) {
        PresetManager.presetIoExecutor.execute {
            try {
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, text)
            } catch (e: Exception) {
                logger.error(e) { "Could not save $what (${file.path})" }
            }
        }
    }

    /** Runs [work] on the IO thread, then [done] with its result on the render thread at the start of a later frame. */
    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        PresetManager.presetIoExecutor.execute {
            val result = runCatching(work)
            VisualSourceRegistry.runOnGlThread { done(result) }
        }
    }
}
