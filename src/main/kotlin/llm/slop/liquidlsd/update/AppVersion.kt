package llm.slop.liquidlsd.update

/**
 * Authoritative runtime access to the application version.
 *
 * Resolves version in order of preference:
 * 1. JAR Manifest `Implementation-Version`
 * 2. Classpath resource `/version.txt` (packaged during build)
 * 3. Default fallback for development/test runs ("1.0.0-beta.41")
 */
object AppVersion {

    const val FALLBACK_VERSION = "1.0.0-beta.59"

    val CURRENT: String by lazy {
        // 1. Check implementationVersion from package manifest
        val manifestVer = AppVersion::class.java.`package`?.implementationVersion
        if (!manifestVer.isNullOrBlank() && manifestVer != "unspecified") {
            return@lazy manifestVer.trim()
        }

        // 2. Check bundled /version.txt resource
        try {
            val stream = AppVersion::class.java.getResourceAsStream("/version.txt")
            if (stream != null) {
                val fileVer = stream.bufferedReader().use { it.readText() }.trim()
                if (fileVer.isNotBlank() && fileVer != "unspecified") {
                    return@lazy fileVer
                }
            }
        } catch (_: Exception) {
            // Ignore classpath read errors
        }

        // 3. Fallback
        FALLBACK_VERSION
    }

    val CURRENT_SEMVER: SemVer by lazy {
        SemVer.parse(CURRENT)
    }
}
