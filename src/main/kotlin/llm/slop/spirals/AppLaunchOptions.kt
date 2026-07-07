package llm.slop.spirals

import java.io.File
import llm.slop.spirals.config.RuntimeConfig

data class AppLaunchOptions(
    val uiLab: Boolean = false,
    val screenshotPath: File? = null,
    val screenshotAfterFrames: Int = RuntimeConfig.Screenshot.DEFAULT_AFTER_FRAMES,
    val windowWidth: Int = RuntimeConfig.Window.DEFAULT_WIDTH,
    val windowHeight: Int = RuntimeConfig.Window.DEFAULT_HEIGHT,
    val startMaximized: Boolean = false,
    val audioEnabled: Boolean? = null,
    val watchdogEnabled: Boolean = true
) {
    companion object {
        fun parse(args: Array<String>): AppLaunchOptions {
            var options = AppLaunchOptions()

            args.forEach { arg ->
                when {
                    arg == "--ui-lab" -> options = options.copy(uiLab = true)
                    arg == "--maximized" -> options = options.copy(startMaximized = true)
                    arg == "--no-audio" -> options = options.copy(audioEnabled = false)
                    arg == "--audio" -> options = options.copy(audioEnabled = true)
                    arg == "--no-watchdog" -> options = options.copy(watchdogEnabled = false)
                    arg.startsWith("--screenshot-ui=") -> {
                        options = options.copy(screenshotPath = File(arg.substringAfter("=")))
                    }
                    arg.startsWith("--screenshot-after-frames=") -> {
                        val frames = arg.substringAfter("=").toIntOrNull()?.coerceAtLeast(1)
                        if (frames != null) {
                            options = options.copy(screenshotAfterFrames = frames)
                        }
                    }
                    arg.startsWith("--window=") -> {
                        val windowValue = arg.substringAfter("=")
                        if (windowValue.equals("maximized", ignoreCase = true)) {
                            options = options.copy(startMaximized = true)
                        } else {
                            parseWindowSize(windowValue)?.let { (width, height) ->
                                options = options.copy(windowWidth = width, windowHeight = height)
                            }
                        }
                    }
                }
            }

            return options
        }

        private fun parseWindowSize(value: String): Pair<Int, Int>? {
            val normalized = value.lowercase().replace(" ", "")
            val parts = normalized.split("x")
            if (parts.size != 2) return null

            val width = parts[0].toIntOrNull()?.coerceAtLeast(RuntimeConfig.Window.MIN_WIDTH) ?: return null
            val height = parts[1].toIntOrNull()?.coerceAtLeast(RuntimeConfig.Window.MIN_HEIGHT) ?: return null
            return width to height
        }
    }
}
