package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.rendering.FlameImporter
import java.io.File

/** Turns a user-supplied `.flame` file into saved Fractal Flame presets (one per flame in the file). */
object FlameImportOps {
    const val FOLDER = "library/presets/imported-flames"
    private const val MAX_BYTES = 8L * 1024 * 1024

    class Outcome(val files: List<File>, val notes: List<String>)

    /** Reads [source], writes `<folder>/<name>.lsd` for each flame (never overwriting: `-2`, `-3`, ... is appended). Throws on unreadable or oversized input. */
    fun importFile(source: File, folder: File = File(FOLDER)): Outcome {
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
