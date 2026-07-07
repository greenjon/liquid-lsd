package llm.slop.spirals.config

object UiConfig {
    object ThemeDefaults {
        const val BASE_SIZE = 18f
        const val MAX_FPS = 30
        const val AUDIO_ENGINE_ENABLED = true
        const val BACKGROUND_VIDEO_ENABLED = false
        const val TOOLTIPS_ENABLED = true
        const val AUTO_VJ_DIRTY_BEHAVIOR = "AUTO_DISCARD"
        const val QUEUE_KEY_TRIGGER = "NONE"
        const val STARTUP_BEHAVIOR = "PREVIOUS_SESSION"
        const val ASSET_BROWSER_MODE = "HALF"
    }

    object MainLayout {
        const val NARROW_WIDTH = 1_000f
        const val RIGHT_COMPACT_BREAKPOINT = 1_100f
        const val RIGHT_MEDIUM_BREAKPOINT = 1_500f
        const val RIGHT_COMPACT_MIN = 320f
        const val RIGHT_MEDIUM_MIN = 360f
        const val RIGHT_WIDE_MIN = 420f
        const val RIGHT_TARGET_RATIO = 0.30f
        const val RIGHT_MAX_RATIO = 0.38f
        const val LIBRARY_MIN_WIDTH = 360f
        const val RIGHT_RESERVED_MIN_WIDTH = 360f
        const val RIGHT_MAX_RESERVED_WIDTH = 560f
        const val ASSET_BROWSER_HALF_RATIO = 0.5f
        const val ASSET_BROWSER_HIDDEN_HEIGHT = 38f
        const val PATCH_GRID_COMPACT_MIN = 360f
        const val PATCH_GRID_WIDE_MIN = 420f
        const val CELL_CONFIG_COMPACT_MIN = 220f
        const val CELL_CONFIG_WIDE_MIN = 320f
        const val PATCH_GRID_COMPACT_BREAKPOINT = 1_300f
        const val PATCH_GRID_COMPACT_RATIO = 0.34f
        const val PATCH_GRID_WIDE_RATIO = 0.30f
    }

    object AssetBrowserLayout {
        const val COMPACT_BREAKPOINT = 700f
        const val MEDIUM_BREAKPOINT = 1_000f
        const val COMPACT_SIDEBAR_WIDTH = 130f
        const val MEDIUM_SIDEBAR_RATIO = 0.24f
        const val WIDE_SIDEBAR_RATIO = 0.26f
        const val MEDIUM_SIDEBAR_MIN = 150f
        const val MEDIUM_SIDEBAR_MAX = 240f
        const val WIDE_SIDEBAR_MAX = 320f
        const val NO_SIDEBAR_QUEUE_RATIO = 0.42f
        const val NO_SIDEBAR_QUEUE_MIN = 220f
        const val NO_SIDEBAR_QUEUE_MAX = 360f
        const val COMPACT_QUEUE_WIDTH = 220f
        const val MEDIUM_QUEUE_RATIO = 0.28f
        const val WIDE_QUEUE_RATIO = 0.30f
        const val MEDIUM_QUEUE_MIN = 240f
        const val MEDIUM_QUEUE_MAX = 300f
        const val WIDE_QUEUE_MIN = 300f
        const val WIDE_QUEUE_MAX = 420f
        const val CENTER_MIN_WIDTH = 220f
    }
}
