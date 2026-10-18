package llm.slop.liquidlsd.cli

/**
 * Parsed command-line arguments for Liquid LSD startup configuration.
 */
data class CliArgs(
    val screenshotUi: String? = null,
    val screenshotAfterFrames: Int = 5,
    val windowSpec: String? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val isMaximized: Boolean = false,
    val noAudio: Boolean = false,
    val uiLab: Boolean = false,
    val helpRequested: Boolean = false,
    val versionRequested: Boolean = false,
    val smokeTestRequested: Boolean = false,
    /** Deterministic tour render: script JSON path (see tour/TourRunner). Implies the isolation set in [tourMode]. */
    val tourScript: String? = null,
    val tourOut: String? = null,
    val tourHidden: Boolean = false,
    /** Debug: "<dir>:70,81" dumps those frames as raw RGBA for diffing determinism failures. */
    val tourDump: String? = null,
    val tourOptions: llm.slop.liquidlsd.tour.TourOptions = llm.slop.liquidlsd.tour.TourOptions()
) {
    val tourMode: Boolean get() = tourScript != null

    companion object {
        fun parse(args: Array<String>): CliArgs {
            var screenshotUi: String? = null
            var screenshotAfterFrames = 5
            var windowSpec: String? = null
            var windowWidth: Int? = null
            var windowHeight: Int? = null
            var isMaximized = false
            var noAudio = false
            var uiLab = false
            var helpRequested = false
            var versionRequested = false
            var smokeTestRequested = false
            var tourScript: String? = null
            var tourOut: String? = null
            var tourHidden = false
            var tourDump: String? = null
            var tourOptions = llm.slop.liquidlsd.tour.TourOptions()
            var tourHashes = false

            var i = 0
            while (i < args.size) {
                val arg = args[i]
                when {
                    arg == "--help" || arg == "-h" -> {
                        helpRequested = true
                    }
                    arg == "--version" || arg == "-v" -> {
                        versionRequested = true
                    }
                    arg == "--smoke-test" -> {
                        smokeTestRequested = true
                    }
                    arg.startsWith("--tour=") -> tourScript = arg.substringAfter("=").trim().ifEmpty { null }
                    arg.startsWith("--tour-out=") -> tourOut = arg.substringAfter("=").trim().ifEmpty { null }
                    arg == "--tour-hidden" -> tourHidden = true
                    arg.startsWith("--tour-dump=") -> tourDump = arg.substringAfter("=").trim().ifEmpty { null }
                    arg == "--tour-draft" -> tourOptions = llm.slop.liquidlsd.tour.TourOptions.DRAFT
                    arg.startsWith("--tour-fps=") -> arg.substringAfter("=").trim().toIntOrNull()?.let { tourOptions = tourOptions.copy(fps = it.coerceIn(10, 120)) }
                    arg.startsWith("--tour-scale=") -> arg.substringAfter("=").trim().toFloatOrNull()?.let { tourOptions = tourOptions.copy(scale = it.coerceIn(0.1f, 1f)) }
                    arg.startsWith("--tour-encoder=") -> tourOptions = tourOptions.copy(encoder = arg.substringAfter("=").trim().lowercase())
                    arg == "--tour-hashes" -> tourHashes = true
                    arg == "--no-audio" -> {
                        noAudio = true
                    }
                    arg == "--ui-lab" -> {
                        uiLab = true
                    }
                    arg.startsWith("--screenshot-ui=") -> {
                        screenshotUi = arg.substringAfter("=").trim().ifEmpty { null }
                    }
                    arg == "--screenshot-ui" -> {
                        if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                            i++
                            screenshotUi = args[i].trim().ifEmpty { null }
                        }
                    }
                    arg.startsWith("--screenshot-after-frames=") -> {
                        arg.substringAfter("=").toIntOrNull()?.let {
                            screenshotAfterFrames = it.coerceAtLeast(1)
                        }
                    }
                    arg == "--screenshot-after-frames" -> {
                        if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                            i++
                            args[i].toIntOrNull()?.let {
                                screenshotAfterFrames = it.coerceAtLeast(1)
                            }
                        }
                    }
                    arg.startsWith("--window=") -> {
                        windowSpec = arg.substringAfter("=").trim().ifEmpty { null }
                    }
                    arg == "--window" -> {
                        if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                            i++
                            windowSpec = args[i].trim().ifEmpty { null }
                        }
                    }
                }
                i++
            }

            if (windowSpec != null) {
                if (windowSpec.equals("maximized", ignoreCase = true)) {
                    isMaximized = true
                } else if (windowSpec.contains("x", ignoreCase = true)) {
                    val parts = windowSpec.lowercase().split("x")
                    if (parts.size == 2) {
                        val w = parts[0].trim().toIntOrNull()
                        val h = parts[1].trim().toIntOrNull()
                        if (w != null && h != null && w > 0 && h > 0) {
                            windowWidth = w
                            windowHeight = h
                        }
                    }
                }
            }

            return CliArgs(
                screenshotUi = screenshotUi,
                screenshotAfterFrames = screenshotAfterFrames,
                windowSpec = windowSpec,
                windowWidth = windowWidth,
                windowHeight = windowHeight,
                isMaximized = isMaximized,
                noAudio = noAudio,
                uiLab = uiLab,
                helpRequested = helpRequested,
                versionRequested = versionRequested,
                smokeTestRequested = smokeTestRequested,
                tourScript = tourScript,
                tourOut = tourOut,
                tourHidden = tourHidden,
                tourDump = tourDump,
                tourOptions = if (tourHashes) tourOptions.copy(hashes = true) else tourOptions
            )
        }

        fun printHelp(version: String) {
            println("""
Liquid LSD - Libre Shader Decks v$version
Usage: liquid-lsd [OPTIONS]

Options:
  --version, -v                 Print version and runtime platform details, then exit
  --smoke-test                  Run headless multi-component smoke test and exit (0 on success)
  --help, -h                    Display this help message and exit
  --screenshot-ui=<file.png>    Capture UI framebuffer PNG and exit cleanly
  --screenshot-after-frames=<N> Number of frames to render before capture (default: 5)
  --window=<W>x<H>|maximized    Override startup window dimensions (e.g. 1920x1080 or maximized)
  --no-audio                    Bypass JACK and JavaSound audio engine initialization
  --tour=<script.json>          Deterministic scripted tour render (fixed simulated clock, no audio/MIDI/OSC, nothing saved)
  --tour-out=<file.mp4>         Output video for --tour (a .vtt caption file is written next to it)
  --tour-hidden                 Create the tour window hidden (experimental)
  --tour-draft                  Fast preview: 30 fps, half size, NVENC, no frame hashes (same as the four options below)
  --tour-fps=<n>                Simulated frame rate (default 60)
  --tour-scale=<f>              Output size as a fraction of the 1920x1080 window, downscaled on the GPU (default 1)
  --tour-encoder=x264|nvenc     Encoder (default x264: reproducible; nvenc: NVIDIA GPU, much faster)
  --tour-hashes                 Write per-frame SHA-256 hashes (<out>.hashes) for determinism checks
  --ui-lab                      Launch isolated UI component gallery sandbox
""".trimIndent())
        }
    }
}
