package llm.slop.liquidlsd.utils

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Saving a user's file without ever leaving it half written. */
object AtomicFile {
    /**
     * Keeps a copy of a user file that couldn't be read as `<name>.unreadable` beside it (no loader picks that up), and
     * tells the user, before the app falls back to defaults and later saves over the original. Empty files aren't kept.
     * Returns the copy, or null.
     */
    fun keepUnreadable(file: File, what: String = file.name): File? = try {
        if (!file.isFile || file.length() == 0L) null
        else {
            var copy = File(file.absoluteFile.parentFile, file.name + ".unreadable")
            if (copy.exists()) {
                val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                copy = File(file.absoluteFile.parentFile, "${file.name}.unreadable-$stamp")
            }
            Files.copy(file.toPath(), copy.toPath())
            UserNotice.post("Couldn't read $what; kept it as ${copy.name} and started without it")
            copy
        }
    } catch (e: Exception) {
        logger.warn(e) { "Couldn't keep a copy of unreadable ${file.path}" }
        UserNotice.post("Couldn't read $what, and couldn't keep a copy of it (${e.message ?: e.javaClass.simpleName}); it will be replaced on the next save")
        null
    }

    /**
     * The swap. On Windows a virus scanner or the search indexer can hold the new file for a moment ("used by another
     * process"), so a failed move is retried a few times before giving up.
     */
    private fun moveIntoPlace(tmp: File, target: File) {
        var attempt = 0
        while (true) {
            try {
                try {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                return
            } catch (e: java.nio.file.FileSystemException) {
                if (e is AtomicMoveNotSupportedException || ++attempt > 4) throw e
                Thread.sleep(40)
            }
        }
    }

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
            // A temp file is private (rw-------). Keep the permissions the file had; a new file gets what the user's umask
            // gives, read from an empty probe file (creating a file applies the umask, which Java can't read directly).
            // The probe has its own name, so a crash never leaves an empty [target].
            try {
                if (target.exists()) {
                    Files.setPosixFilePermissions(tmp.toPath(), Files.getPosixFilePermissions(target.toPath()))
                } else {
                    val probe = File(dir, ".${target.name}.${System.nanoTime()}.probe").toPath()
                    try {
                        Files.createFile(probe)
                        Files.setPosixFilePermissions(tmp.toPath(), Files.getPosixFilePermissions(probe))
                    } finally {
                        Files.deleteIfExists(probe)
                    }
                }
            } catch (e: UnsupportedOperationException) {
                // Windows: no POSIX permissions.
            } catch (e: java.io.IOException) {
                // Not ours to change (another owner): the private default stays.
            }
            moveIntoPlace(tmp, target)
        } finally {
            tmp.delete() // no-op after a successful move
        }
    }
}
