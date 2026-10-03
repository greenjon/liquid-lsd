package llm.slop.liquidlsd.control

import mu.KotlinLogging
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** File-IO shared by the user-file stores (controller profiles, Perform pages): safe scan and atomic write. */
internal object UserJsonFiles {
    private val logger = KotlinLogging.logger {}

    /** A user file that was accepted: its parsed [value], the [id] it declares and the [file] it came from. */
    class Loaded<T>(val id: String, val value: T, val file: File)

    class Scan<T>(val loaded: List<Loaded<T>>, val rejected: List<Pair<File, List<String>>>)

    /**
     * Reads every `*.json` file in [dir], sorted by name. Never throws: an unreadable file or a parse failure
     * becomes a rejected entry. When two files declare the same id the first (by name) wins and the later one is rejected.
     * [parse] returns the value or the problems.
     */
    fun <T> scan(dir: File, idOf: (T) -> String, parse: (text: String, source: String) -> Pair<T?, List<String>>): Scan<T> {
        val loaded = ArrayList<Loaded<T>>()
        val rejected = ArrayList<Pair<File, List<String>>>()
        val files = try {
            dir.listFiles { _, n -> n.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Could not list ${dir.path}" }
            emptyList()
        }
        val firstById = HashMap<String, File>()
        for (file in files) {
            try {
                val (value, problems) = parse(file.readText(), file.path)
                if (value == null) {
                    rejected += file to problems
                    continue
                }
                val id = idOf(value)
                val first = firstById[id]
                if (first != null) {
                    logger.error { "Skipping ${file.path}: duplicate id '$id' (already used by ${first.name})" }
                    rejected += file to listOf("duplicate id '$id' (already defined by ${first.name})")
                } else {
                    firstById[id] = file
                    loaded += Loaded(id, value, file)
                }
            } catch (e: Exception) {
                logger.error(e) { "Could not read ${file.path}" }
                rejected += file to listOf("Could not read file: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return Scan(loaded, rejected)
    }

    /** Writes [text] to a temp file beside [target] then moves it into place, so a crash never leaves a truncated file. */
    fun writeAtomic(target: File, text: String) {
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
