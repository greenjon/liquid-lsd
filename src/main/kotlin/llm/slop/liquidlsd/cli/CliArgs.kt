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
    val smokeTestRequested: Boolean = false
) {
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
                smokeTestRequested = smokeTestRequested
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
  --ui-lab                      Launch isolated UI component gallery sandbox
""".trimIndent())
        }
    }
}
