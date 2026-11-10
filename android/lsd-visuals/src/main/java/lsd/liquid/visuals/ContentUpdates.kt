package lsd.liquid.visuals

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit

private const val TAG = "ContentUpdates"
private const val WORK_NAME = "lsd-visuals-content-update"
/** The name Spaz Radio 2.0 scheduled the check under, when this code lived in the app; cancelled so it never runs a class that is gone. */
private const val OLD_WORK_NAME = "visuals-content-update"
private const val BASE_URL = "https://spaz.org/tv/"
private const val MAX_FETCH_BYTES = 6L * 1024 * 1024

/** Public half of the key that signs the content manifest (liquid-lsd scripts/content_signing_pub.pem). */
private const val PUBLIC_KEY =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAErmxnLpUXX3Gl7akMm6qQL7D2wVsSntTKsVNknMXW3DHLmaS6V060eZWHg23Tmxfz4uQ3MWdaGV7xFYEb6tc4rQ=="

/**
 * Wires [ContentUpdater] to the app: the store folder (`files/visuals-content`), the server (the web TV's
 * `/tv/update/`), a daily background check. Call [schedule] at startup and whenever the user's setting changes.
 */
object ContentUpdates {
    fun store(context: Context): ContentStore {
        val appVersion = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
        return ContentStore(File(context.filesDir, "visuals-content"), appVersion)
    }

    /** Runs one check now, on the calling (background) thread. Never throws. */
    fun runCheck(context: Context): ContentUpdater.Result? = try {
        val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)))
        val assets = context.assets
        ContentUpdater(
            store(context), key, ContentUpdater.ENGINE,
            readBundled = { path -> try { assets.open("visuals/$path").use { it.readBytes() } } catch (_: Exception) { null } },
            fetch = { path -> fetch(client, BASE_URL + path) },
        ).check().also { Log.d(TAG, "check: $it") }
    } catch (e: Exception) {
        Log.d(TAG, "check failed: $e")
        null
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(OLD_WORK_NAME)
        if (!enabled) { wm.cancelUniqueWork(WORK_NAME); return }
        val request = PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            runCheck(applicationContext)
            return Result.success() // a failed check just waits for the next day
        }
    }

    private fun fetch(client: OkHttpClient, url: String): ByteArray {
        client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} for $url")
            val body = r.body ?: error("no body for $url")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            body.byteStream().use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_FETCH_BYTES) error("too large: $url")
                }
            }
            return out.toByteArray()
        }
    }
}
