package lsd.liquid.visuals

import java.io.File

/**
 * The downloaded copy of the visuals' data files (presets, playlists, shaders, ...), laid out as
 * `<root>/v<version>/<path>` plus `<root>/current` naming the live version. It overlays the bundled
 * assets/visuals (this module's assets): a path found here wins, anything else comes from the APK, so the app always works offline
 * and with no download at all. Content downloaded by an older or newer app build is ignored ([appVersion]
 * is recorded) because the bundled files of a new build can be newer than what was downloaded.
 */
class ContentStore(private val root: File, private val appVersion: Int) {
    /** Version of the live download (the manifest's `version`), or null when the bundled files are in use. */
    fun version(): Long? {
        val v = try { File(root, "current").readText().trim().toLong() } catch (_: Exception) { return null }
        val meta = try { File(dir(v), "meta").readText().trim() } catch (_: Exception) { return null }
        return if (meta == "app=$appVersion") v else null
    }

    /** The directory to read from, or null for "bundled only". Take it once per page load so one session never mixes versions. */
    fun activeDir(): File? = version()?.let { dir(it) }

    fun dir(version: Long) = File(root, "v$version")

    /** Highest manifest version ever accepted as genuine (signed), even when it matched the bundled files; null if none. */
    fun highestSeen(): Long? {
        val seen = try { File(root, "seen").readText().trim().toLong() } catch (_: Exception) { null }
        val cur = version()
        return if (seen == null) cur else if (cur == null) seen else maxOf(seen, cur)
    }

    /** Remembers [version] as seen; never lowers the stored value. */
    fun recordSeen(version: Long) {
        if (version <= (highestSeen() ?: Long.MIN_VALUE)) return
        root.mkdirs()
        val tmp = File(root, "seen.tmp")
        tmp.writeText("$version\n")
        tmp.renameTo(File(root, "seen"))
    }

    /** Starts an empty, uniquely named staging directory for one download run; leftovers of dead runs are removed. */
    fun newStaging(): File {
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("staging") }?.forEach { it.deleteRecursively() }
        return File(root, "staging-${System.nanoTime()}").apply { mkdirs() }
    }

    /**
     * Makes [staging] the live version [version]. The previously live version stays on disk, because a running
     * picture reads from it until it restarts; every older version is removed. Returns false if it could not.
     */
    fun activate(staging: File, version: Long): Boolean {
        File(staging, "meta").writeText("app=$appVersion\n")
        val target = dir(version)
        val previous = try { File(root, "current").readText().trim().toLong() } catch (_: Exception) { null }?.let { dir(it) }
        target.deleteRecursively()
        if (!staging.renameTo(target)) return false
        val tmp = File(root, "current.tmp")
        tmp.writeText("$version\n")
        if (!tmp.renameTo(File(root, "current"))) return false
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("v") && it != target && it != previous }?.forEach { it.deleteRecursively() }
        return true
    }

    companion object {
        /** A file under [dir] for the relative [path], or null if the path escapes the directory or is absent. */
        fun resolve(dir: File, path: String): File? {
            val f = File(dir, path)
            val inside = f.canonicalPath.startsWith(dir.canonicalPath + File.separator)
            return if (inside && f.isFile) f else null
        }
    }
}
