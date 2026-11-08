package llm.slop.liquidlsd.utils

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Saving a user's file without ever leaving it half written. */
object AtomicFile {
    /**
     * Writes [text] to a temp file beside [target] then moves it into place, so a crash or a full disk never leaves a
     * truncated file: the old one stays until the new one is complete. A [target] made read-only is refused (a move would
     * replace it), as a plain write would.
     */
    fun write(target: File, text: String) {
        if (target.exists() && !target.canWrite()) throw AccessDeniedException(target.path, null, "file is read-only")
        val dir = target.absoluteFile.parentFile
        dir.mkdirs()
        val tmp = File.createTempFile(".${target.name}.", ".tmp", dir)
        try {
            tmp.writeText(text)
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tmp.delete() // no-op after a successful move
        }
    }
}
