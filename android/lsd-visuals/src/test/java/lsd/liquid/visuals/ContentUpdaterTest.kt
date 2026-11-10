package lsd.liquid.visuals

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class ContentUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private val server = HashMap<String, ByteArray>()
    private val bundled = HashMap<String, ByteArray>()
    private val fetched = ArrayList<String>()

    private fun publish(version: Long, files: Map<String, String>, minEngine: Int = 1, signer: java.security.PrivateKey = pair.private) {
        val fo = JsonObject()
        for ((p, c) in files) {
            server[p] = c.toByteArray()
            fo.add(p, JsonObject().apply { addProperty("sha256", ContentUpdater.sha256(c.toByteArray())); addProperty("size", c.length) })
        }
        val m = JsonObject().apply {
            addProperty("format", 1); addProperty("version", version); addProperty("minEngine", minEngine); add("files", fo)
        }.toString().toByteArray()
        server["update/manifest.json"] = m
        server["update/manifest.json.sig"] = Signature.getInstance("SHA256withECDSA").run { initSign(signer); update(m); sign() }
    }

    private fun store(app: Int = 1) = ContentStore(tmp.root.resolve("c"), app)
    private fun updater(s: ContentStore = store(), engine: Int = 1) =
        ContentUpdater(s, pair.public, engine, { bundled[it] }, { p -> fetched += p; server[p] ?: error("404 $p") })

    private val base = mapOf("catalog.json" to "{}", "settings.json" to "{}", "presets/a.lsd" to "A")

    @Test fun installsAndServesDownloadedFiles() {
        publish(10, base)
        val s = store()
        val r = updater(s).check()
        assertTrue(r.toString(), r is ContentUpdater.Result.Updated)
        assertEquals(10L, s.version())
        assertEquals("A", ContentStore.resolve(s.activeDir()!!, "presets/a.lsd")!!.readText())
    }

    @Test fun reusesBundledFilesWithSameHash() {
        bundled["catalog.json"] = "{}".toByteArray(); bundled["settings.json"] = "{}".toByteArray()
        publish(10, base)
        updater().check()
        assertEquals(listOf("update/manifest.json", "update/manifest.json.sig", "presets/a.lsd"), fetched)
    }

    @Test fun rejectsWrongSignature() {
        publish(10, base, signer = other.private)
        val s = store()
        assertTrue(updater(s).check() is ContentUpdater.Result.Rejected)
        assertNull(s.version())
    }

    @Test fun rejectsTamperedFileAndKeepsPreviousContent() {
        publish(10, base)
        val s = store()
        updater(s).check()
        publish(11, base + ("presets/b.lsd" to "B"))
        server["presets/b.lsd"] = "EVIL".toByteArray()
        val r = updater(s).check()
        assertTrue(r.toString(), r is ContentUpdater.Result.Rejected)
        assertEquals(10L, s.version())
        assertNull(ContentStore.resolve(s.activeDir()!!, "presets/b.lsd"))
    }

    @Test fun skipsContentThatNeedsNewerEngine() {
        publish(10, base, minEngine = 2)
        val r = updater().check()
        assertEquals(ContentUpdater.Result.Rejected("needs a newer app"), r)
    }

    @Test fun ignoresOlderOrSameVersion() {
        publish(10, base)
        val s = store()
        updater(s).check()
        assertTrue(updater(s).check() is ContentUpdater.Result.UpToDate)
        publish(9, base)
        assertTrue(updater(s).check() is ContentUpdater.Result.UpToDate)
    }

    @Test fun downloadedContentFromAnotherAppVersionIsIgnored() {
        publish(10, base)
        updater(store(1)).check()
        assertNotNull(store(1).version())
        assertNull(store(2).version())
    }

    @Test fun refusesCodeAndPathTricks() {
        for (p in listOf("../x.json", "/etc/x.json", "presets/../../x.lsd", "app.js", "index.html", "presets/a.js", "other/a.lsd", "presets//a.lsd")) {
            assertFalse(p, ContentUpdater.allowedPath(p))
        }
        for (p in listOf("catalog.json", "presets/a.lsd", "shaders/fx/x.fs", "content/fxchains/q.lsdfxchain")) assertTrue(p, ContentUpdater.allowedPath(p))
        publish(10, base + ("app.js" to "evil()"))
        assertTrue(updater().check() is ContentUpdater.Result.Rejected)
    }

    @Test fun incompleteManifestIsRejected() {
        publish(10, mapOf("presets/a.lsd" to "A"))
        assertTrue(updater().check() is ContentUpdater.Result.Rejected)
    }

    @Test fun resolveCannotEscapeTheDirectory() {
        val d = tmp.newFolder("d"); tmp.root.resolve("secret").writeText("x")
        assertNull(ContentStore.resolve(d, "../secret"))
    }
}
