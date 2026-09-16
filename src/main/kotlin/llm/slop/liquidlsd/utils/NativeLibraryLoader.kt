package llm.slop.liquidlsd.utils

import mu.KotlinLogging
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for detecting host platform and dynamically extracting/loading native shared libraries
 * (.so, .dll, .dylib) from application resources.
 */
object NativeLibraryLoader {
    private val logger = KotlinLogging.logger {}
    private val loadedLibraries = HashSet<String>()

    enum class OS { LINUX, WINDOWS, MACOS, UNKNOWN }
    enum class Arch { X64, ARM64, UNKNOWN }

    val currentOs: OS by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("linux") -> OS.LINUX
            osName.contains("win") -> OS.WINDOWS
            osName.contains("mac") || osName.contains("darwin") -> OS.MACOS
            else -> OS.UNKNOWN
        }
    }

    val currentArch: Arch by lazy {
        val archName = System.getProperty("os.arch").lowercase()
        when {
            archName.contains("aarch64") || archName.contains("arm64") -> Arch.ARM64
            archName.contains("amd64") || archName.contains("x86_64") || archName.contains("x64") -> Arch.X64
            else -> Arch.UNKNOWN
        }
    }

    fun getPlatformDirectoryName(): String {
        val osStr = when (currentOs) {
            OS.LINUX -> "linux"
            OS.WINDOWS -> "windows"
            OS.MACOS -> "macos"
            OS.UNKNOWN -> "unknown"
        }
        val archStr = when (currentArch) {
            Arch.X64 -> "x64"
            Arch.ARM64 -> "arm64"
            Arch.UNKNOWN -> "unknown"
        }
        return "$osStr-$archStr"
    }

    fun getNativeLibraryName(baseName: String): String {
        return when (currentOs) {
            OS.LINUX -> "lib$baseName.so"
            OS.WINDOWS -> "$baseName.dll"
            OS.MACOS -> "lib$baseName.dylib"
            OS.UNKNOWN -> baseName
        }
    }

    /**
     * Loads a native library by base name (e.g. "link_jni").
     * First attempts standard System.loadLibrary(), then tries extracting from classpath resource /natives/{platform}/.
     */
    @Synchronized
    fun loadLibrary(baseName: String): Boolean {
        if (loadedLibraries.contains(baseName)) return true

        // 1. Try standard System.loadLibrary
        try {
            System.loadLibrary(baseName)
            loadedLibraries.add(baseName)
            logger.info { "Successfully loaded native library '$baseName' via System.loadLibrary()" }
            return true
        } catch (e: UnsatisfiedLinkError) {
            logger.debug { "System.loadLibrary('$baseName') failed, attempting resource extraction: ${e.message}" }
        }

        // 2. Try resource extraction from /natives/{platform}/
        val libFileName = getNativeLibraryName(baseName)
        val platformDir = getPlatformDirectoryName()
        val resourcePaths = listOf(
            "/natives/$platformDir/$libFileName",
            "/natives/$libFileName",
            "/$libFileName"
        )

        for (resourcePath in resourcePaths) {
            val resourceStream = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
            if (resourceStream != null) {
                try {
                    val tempFile = File.createTempFile("liquid_lsd_native_", "_$libFileName")
                    tempFile.deleteOnExit()

                    resourceStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    System.load(tempFile.absolutePath)
                    loadedLibraries.add(baseName)
                    logger.info { "Successfully loaded native library '$baseName' from resource '$resourcePath' -> ${tempFile.absolutePath}" }
                    return true
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to load extracted native library from resource '$resourcePath'" }
                }
            }
        }

        logger.warn { "Could not load native library '$baseName' for platform $platformDir" }
        return false
    }

    /**
     * Prepares ImGui native library binaries for platforms (e.g. Linux ARM64) where upstream
     * imgui-java does not bundle native JNI .so artifacts in its standard Maven packages.
     * Extracts bundled libimgui-java64.so to temp directory and sets 'imgui.library.path'.
     */
    fun prepareImGuiNatives() {
        if (currentOs == OS.LINUX && currentArch == Arch.ARM64) {
            val libFileName = "libimgui-java64.so"
            val resourcePath = "/natives/linux-arm64/$libFileName"
            val stream = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
            if (stream != null) {
                try {
                    val tempDir = File(System.getProperty("java.io.tmpdir"), "liquid_lsd_imgui_arm64")
                    tempDir.mkdirs()
                    val destFile = File(tempDir, libFileName)
                    if (!destFile.exists() || destFile.length() == 0L) {
                        stream.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        destFile.setExecutable(true)
                    }
                    System.setProperty("imgui.library.path", tempDir.absolutePath)
                    logger.info { "Configured ImGui ARM64 library path: ${tempDir.absolutePath}" }
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to extract ImGui ARM64 native library from resource $resourcePath" }
                }
            } else {
                logger.debug { "ImGui ARM64 native library resource not present at $resourcePath" }
            }
        }
    }
}
