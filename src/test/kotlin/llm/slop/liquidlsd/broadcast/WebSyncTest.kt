package llm.slop.liquidlsd.broadcast

import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSyncTest {

    private val projectRoot: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) {
            dir = dir.parentFile
        }
        dir
    }

    private val manifestFile: File by lazy {
        File(projectRoot, "web/sync_manifest.json")
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun transpileToWebGlsl(source: String): String {
        var text = source.replace("\r\n", "\n")
        val versionRegex = Regex("^#version\\s+330(\\s+core)?", RegexOption.MULTILINE)
        val precisionRegex = Regex("precision\\s+(highp|mediump|lowp)\\s+float;")

        if (versionRegex.containsMatchIn(text)) {
            text = text.replace(versionRegex, "#version 300 es\nprecision highp float;")
        } else if (!text.startsWith("#version 300 es")) {
            text = "#version 300 es\nprecision highp float;\n$text"
        } else if (!precisionRegex.containsMatchIn(text)) {
            text = text.replace("#version 300 es\n", "#version 300 es\nprecision highp float;\n")
        }

        return text.trim() + "\n"
    }

    @Test
    fun testSyncManifestExists() {
        assertTrue(manifestFile.exists(), "web/sync_manifest.json must exist at ${manifestFile.absolutePath}")
    }

    @Test
    fun testAllWebShadersMatchDesktopTranspiled() {
        val json = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        val shaders = json["shaders"]?.jsonArray ?: error("No 'shaders' array found in manifest")

        val mismatches = mutableListOf<String>()

        for (elem in shaders) {
            val obj = elem.jsonObject
            val desktopRel = obj["desktop"]?.jsonPrimitive?.content ?: continue
            val webRel = obj["web"]?.jsonPrimitive?.content ?: continue

            val desktopFile = File(projectRoot, desktopRel)
            val webFile = File(projectRoot, webRel)

            if (!desktopFile.exists()) {
                mismatches.add("[MISSING DESKTOP SOURCE] $desktopRel does not exist")
                continue
            }
            if (!webFile.exists()) {
                mismatches.add("[MISSING WEB TARGET] $webRel does not exist")
                continue
            }

            val desktopTranspiled = transpileToWebGlsl(desktopFile.readText())
            val webContent = webFile.readText().replace("\r\n", "\n").trim() + "\n"

            if (desktopTranspiled != webContent) {
                mismatches.add("[OUT OF SYNC] $webRel does not match transpiled $desktopRel. Run './gradlew syncWeb' or './scripts/sync_web.py --apply'")
            }
        }

        assertTrue(
            mismatches.isEmpty(),
            "Web shader drift detected:\n" + mismatches.joinToString("\n")
        )
    }

    @Test
    fun testMonitoredSourceFilesMatchRecordedHashes() {
        val json = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        val monitored = json["monitored_sources"]?.jsonArray ?: error("No 'monitored_sources' array in manifest")

        val driftList = mutableListOf<String>()

        for (elem in monitored) {
            val obj = elem.jsonObject
            val desktopRel = obj["desktop"]?.jsonPrimitive?.content ?: continue
            val webRel = obj["web"]?.jsonPrimitive?.content ?: continue
            val lastHash = obj["last_synced_hash"]?.jsonPrimitive?.content ?: ""

            val desktopFile = File(projectRoot, desktopRel)
            if (!desktopFile.exists()) {
                driftList.add("[MISSING DESKTOP SOURCE] $desktopRel does not exist")
                continue
            }

            val currentHash = computeSha256(desktopFile)
            if (currentHash != lastHash) {
                driftList.add(
                    "[LOGIC DRIFT DETECTED] Desktop file '$desktopRel' was modified (current hash: ${currentHash.take(8)}, manifest: ${lastHash.take(8)}).\n" +
                    "  -> Please verify JS parity in '$webRel', then run: ./scripts/sync_web.py --mark-synced $desktopRel"
                )
            }
        }

        assertTrue(
            driftList.isEmpty(),
            "Desktop-to-Web algorithmic drift detected:\n" + driftList.joinToString("\n\n")
        )
    }
}
