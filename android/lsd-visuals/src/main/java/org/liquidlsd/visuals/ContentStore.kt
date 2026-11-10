package org.liquidlsd.visuals

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

    /** Starts an empty staging directory for a new download. */
    fun newStaging(): File = File(root, "staging").apply { deleteRecursively(); mkdirs() }

    /** Makes [staging] the live version [version], then removes every other version. Returns false if it could not. */
    fun activate(staging: File, version: Long): Boolean {
        File(staging, "meta").writeText("app=$appVersion\n")
        val target = dir(version)
        target.deleteRecursively()
        if (!staging.renameTo(target)) return false
        val tmp = File(root, "current.tmp")
        tmp.writeText("$version\n")
        if (!tmp.renameTo(File(root, "current"))) return false
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("v") && it != target }?.forEach { it.deleteRecursively() }
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
