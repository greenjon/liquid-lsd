package llm.slop.liquidlsd.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val name: String,
    val body: String,
    val publishedAt: String
)

sealed class UpdateCheckResult {
    object Idle : UpdateCheckResult()
    object Checking : UpdateCheckResult()
    data class UpdateAvailable(val currentVersion: String, val latestRelease: ReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestRelease: ReleaseInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Thread-safe, non-blocking GitHub release update checker for Liquid LSD.
 *
 * Runs exclusively on background daemon threads with strict network timeouts
 * to guarantee that audio callbacks and OpenGL rendering loops are never blocked.
 */
object UpdateChecker {

    const val GITHUB_REPO = "greenjon/liquid-lsd"
    const val GITHUB_RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val GITHUB_WEB_LATEST_REDIRECT = "https://github.com/$GITHUB_REPO/releases/latest"

    private const val TIMEOUT_SECONDS = 5L

    @Volatile
    var lastResult: UpdateCheckResult = UpdateCheckResult.Idle
        private set

    @Volatile
    var isChecking: Boolean = false
        private set

    @Volatile
    var hasCheckedOnStartup: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Executes the update check asynchronously on a daemon thread.
     *
     * @param isManualCheck Whether this check was requested directly by the user (e.g. from the menu).
     * @param onComplete Optional callback executed on the background thread when checking completes.
     */
    fun checkForUpdatesAsync(
        isManualCheck: Boolean = false,
        onComplete: ((UpdateCheckResult) -> Unit)? = null
    ) {
        if (isChecking) {
            logger.debug { "Update check already in progress; skipping duplicate request." }
            return
        }

        isChecking = true
        lastResult = UpdateCheckResult.Checking

        thread(isDaemon = true, name = "LiquidLSD-UpdateChecker") {
            try {
                val result = checkForUpdatesSync()
                lastResult = result
                onComplete?.invoke(result)
            } catch (e: Exception) {
                val err = UpdateCheckResult.Error(e.message ?: "Failed to connect to update server")
                lastResult = err
                onComplete?.invoke(err)
            } finally {
                isChecking = false
            }
        }
    }

    /**
     * Synchronously checks GitHub for the latest release.
     * Guaranteed not to throw uncaught exceptions; errors are encapsulated in [UpdateCheckResult.Error].
     */
    fun checkForUpdatesSync(): UpdateCheckResult {
        return try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()

            val release = queryGitHubApi(client) ?: queryGitHubRedirectFallback(client)
            if (release == null) {
                return UpdateCheckResult.Error("Could not retrieve release information from GitHub.")
            }

            val currentVer = AppVersion.CURRENT
            val currentSemVer = AppVersion.CURRENT_SEMVER
            val latestSemVer = SemVer.parse(release.tagName)

            logger.info { "Liquid LSD Version Check: current=$currentVer ($currentSemVer), latest=${release.tagName} ($latestSemVer)" }

            if (latestSemVer > currentSemVer) {
                UpdateCheckResult.UpdateAvailable(currentVer, release)
            } else {
                UpdateCheckResult.UpToDate(currentVer, release)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check for updates: ${e.message}" }
            UpdateCheckResult.Error(e.message ?: "Network error while checking for updates")
        }
    }

    private fun queryGitHubApi(client: HttpClient): ReleaseInfo? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_LATEST_RELEASE))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Liquid-LSD-Desktop/${AppVersion.CURRENT}")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                parseReleaseJson(response.body())
            } else {
                logger.debug { "GitHub releases API returned HTTP ${response.statusCode()}: ${response.body()}" }
                null
            }
        } catch (e: Exception) {
            logger.debug(e) { "GitHub API request failed: ${e.message}" }
            null
        }
    }

    private fun queryGitHubRedirectFallback(client: HttpClient): ReleaseInfo? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_WEB_LATEST_REDIRECT))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", "Liquid-LSD-Desktop/${AppVersion.CURRENT}")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            val location = response.headers().firstValue("location").orElse(null)
            if (!location.isNullOrBlank() && location.contains("/releases/tag/")) {
                val tag = location.substringAfterLast("/").trim()
                if (tag.isNotBlank()) {
                    return ReleaseInfo(
                        tagName = tag,
                        htmlUrl = location,
                        name = tag,
                        body = "",
                        publishedAt = ""
                    )
                }
            }
            null
        } catch (e: Exception) {
            logger.debug(e) { "GitHub redirect fallback failed: ${e.message}" }
            null
        }
    }

    fun parseReleaseJson(body: String): ReleaseInfo? {
        return try {
            val element = json.parseToJsonElement(body).jsonObject
            val tagName = element["tag_name"]?.jsonPrimitive?.content ?: return null
            val htmlUrl = element["html_url"]?.jsonPrimitive?.content ?: "$GITHUB_RELEASES_URL/tag/$tagName"
            val name = element["name"]?.jsonPrimitive?.content ?: tagName
            val releaseNotes = element["body"]?.jsonPrimitive?.content ?: ""
            val publishedAt = element["published_at"]?.jsonPrimitive?.content ?: ""

            ReleaseInfo(
                tagName = tagName,
                htmlUrl = htmlUrl,
                name = name,
                body = releaseNotes,
                publishedAt = publishedAt
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse GitHub release JSON: ${e.message}" }
            null
        }
    }
}
