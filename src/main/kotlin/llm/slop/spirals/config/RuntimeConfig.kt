package llm.slop.spirals.config

object RuntimeConfig {
    object Window {
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        const val MIN_WIDTH = 320
        const val MIN_HEIGHT = 240
        const val OUTPUT_PREVIEW_WIDTH = 1280
        const val OUTPUT_PREVIEW_HEIGHT = 720
    }

    object Rendering {
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
    }

    object Screenshot {
        const val DEFAULT_AFTER_FRAMES = 6
    }

    object AssetScan {
        const val CACHE_TTL_MS = 1_000L
    }
}
