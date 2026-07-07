package llm.slop.spirals.ui

import llm.slop.spirals.config.ProjectConfig
import mu.KotlinLogging
import java.io.File
import java.io.FileOutputStream
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

private val logger = KotlinLogging.logger {}

object DocManager {
    private const val RESOURCE_DIR = "docs"

    private val appDataDir: File by lazy {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ProjectConfig.Paths.LOCAL_APP_DATA_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val localDocsDir: File by lazy {
        File(appDataDir, "docs")
    }

    /**
     * Extracts documentation resources from the classpath/JAR to the local app data folder and opens index.html in the browser.
     */
    fun openDocumentation() {
        try {
            extractDocsIfNeeded()
            val indexFile = File(localDocsDir, "index.html")
            if (indexFile.exists()) {
                openInBrowser(indexFile.toURI())
            } else {
                logger.error { "index.html not found in extracted docs directory at ${indexFile.absolutePath}" }
                // Fallback online URL if offline fails
                openInBrowser(URI(ProjectConfig.App.GITHUB_REPOSITORY_URL))
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract and open documentation: ${e.message}" }
        }
    }

    private fun extractDocsIfNeeded() {
        val jarUrl = javaClass.classLoader.getResource(RESOURCE_DIR) ?: run {
            logger.warn { "No docs directory found in resources." }
            return
        }

        val isJar = jarUrl.protocol == "jar"

        if (!localDocsDir.exists()) {
            localDocsDir.mkdirs()
        }

        if (isJar) {
            extractFromJar(jarUrl)
        } else {
            // Running from IDE/development file system environment
            val sourceDir = File(jarUrl.toURI())
            copyDirectory(sourceDir, localDocsDir)
        }
    }

    private fun extractFromJar(jarUrl: URL) {
        val connection = jarUrl.openConnection() as JarURLConnection
        connection.useCaches = false
        val jarFile: JarFile = connection.jarFile
        try {
            val entries = jarFile.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("$RESOURCE_DIR/") && !entry.isDirectory) {
                    val relativePath = entry.name.substring(RESOURCE_DIR.length + 1)
                    val destFile = File(localDocsDir, relativePath)
                    destFile.parentFile.mkdirs()

                    jarFile.getInputStream(entry).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } finally {
            jarFile.close()
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            if (!destination.exists()) {
                destination.mkdirs()
            }
            source.list()?.forEach { child ->
                copyDirectory(File(source, child), File(destination, child))
            }
        } else {
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun openInBrowser(uri: URI) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(uri)
            } else {
                val rt = Runtime.getRuntime()
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("nix") || os.contains("nux")) {
                    rt.exec(arrayOf("xdg-open", uri.toString()))
                } else {
                    logger.error { "Desktop browser action not supported on this platform." }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error launching browser: ${e.message}" }
        }
    }
}
