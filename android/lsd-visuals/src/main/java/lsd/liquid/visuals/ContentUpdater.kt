package lsd.liquid.visuals

import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

/**
 * Fetches new presets, playlists and shaders from the Liquid LSD server so the visuals can improve without an app
 * release. The server publishes `update/manifest.json` (every data file with sha256 and size) and
 * `update/manifest.json.sig`, an ECDSA P-256 / SHA-256 signature over the manifest bytes (scripts/make_content_manifest.py
 * in the liquid-lsd repo). Nothing is used unless the signature verifies against [key], every file matches its hash,
 * and the whole set is complete; then it is swapped in as one step, so a failure at any point leaves the previous
 * content (or the bundled files) untouched. Only data files are accepted, never code.
 *
 * @param fetch downloads a path relative to the server's /tv/ (throws on any failure)
 * @param readBundled bytes of a bundled asset under assets/visuals/, or null
 */
class ContentUpdater(
    private val store: ContentStore,
    private val key: PublicKey,
    private val engine: Int,
    private val readBundled: (String) -> ByteArray?,
    private val fetch: (String) -> ByteArray,
) {
    sealed interface Result {
        data class Updated(val version: Long, val downloaded: Int, val reused: Int) : Result
        data class UpToDate(val version: Long?) : Result
        /** Content that needs a newer app, or a manifest that failed a check. [reason] is for logs and the status line. */
        data class Rejected(val reason: String) : Result
    }

    fun check(): Result {
        val manifestBytes = fetch("update/manifest.json")
        val sig = fetch("update/manifest.json.sig")
        if (!verify(manifestBytes, sig)) return Result.Rejected("bad signature")

        val root = JsonParser.parseString(String(manifestBytes, Charsets.UTF_8)).asJsonObject
        if (root.get("format")?.asInt != 1) return Result.Rejected("unknown manifest format")
        val version = root.get("version").asLong
        if (root.get("minEngine").asInt > engine) return Result.Rejected("needs a newer app")
        val current = store.version()
        if (current != null && version <= current) return Result.UpToDate(current)

        val files = LinkedHashMap<String, Pair<String, Long>>()
        for ((path, v) in root.getAsJsonObject("files").entrySet()) {
            if (!allowedPath(path)) return Result.Rejected("unexpected path $path")
            val o = v.asJsonObject
            val size = o.get("size").asLong
            if (size < 0 || size > MAX_FILE_BYTES) return Result.Rejected("file too large: $path")
            files[path] = o.get("sha256").asString.lowercase() to size
        }
        if (files.size > MAX_FILES || REQUIRED.any { it !in files }) return Result.Rejected("incomplete manifest")

        val staging = store.newStaging()
        try {
            val active = store.activeDir()
            var downloaded = 0
            var reused = 0
            var total = 0L
            for ((path, want) in files) {
                val (sha, size) = want
                val local = active?.let { ContentStore.resolve(it, path)?.readBytes() } ?: readBundled(path)
                val bytes = if (local != null && local.size.toLong() == size && sha256(local) == sha) {
                    reused++
                    local
                } else {
                    total += size
                    if (total > MAX_TOTAL_BYTES) return Result.Rejected("download too large")
                    val got = fetch(path)
                    if (got.size.toLong() != size || sha256(got) != sha) return Result.Rejected("hash mismatch: $path")
                    downloaded++
                    got
                }
                val out = File(staging, path)
                out.parentFile!!.mkdirs()
                out.writeBytes(bytes)
            }
            if (downloaded == 0 && current == null) {
                // The server's files are exactly the bundled ones: nothing to install, but remember we checked.
                staging.deleteRecursively()
                return Result.UpToDate(null)
            }
            return if (store.activate(staging, version)) Result.Updated(version, downloaded, reused)
            else Result.Rejected("could not install")
        } finally {
            staging.deleteRecursively() // already moved on success
        }
    }

    private fun verify(data: ByteArray, sig: ByteArray): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(data); verify(sig) }
    } catch (_: Exception) {
        false
    }

    companion object {
        /** What the bundled web engine can render. Raise it together with CONTENT_ENGINE gates in scripts/make_content_manifest.py. */
        const val ENGINE = 3
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
        private const val MAX_FILES = 2000
        private val REQUIRED = listOf("catalog.json", "settings.json")
        private val DIRS = listOf("presets/", "playlists/", "shaders/", "fxchains/", "content/")
        private val ROOT_FILES = setOf("catalog.json", "settings.json", "mandala_recipes.json", "autopilot.json")
        private val EXT = setOf("lsd", "lsdplay", "lsdfxchain", "lsdtrans", "frag", "vert", "fs", "json")

        /** Data files only, in the known folders, with no way to climb out of the content directory. */
        fun allowedPath(path: String): Boolean {
            if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.split('/').any { it == ".." || it == "." || it.isEmpty() }) return false
            if (path.substringAfterLast('.', "") !in EXT) return false
            return path in ROOT_FILES || DIRS.any { path.startsWith(it) }
        }

        fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}
