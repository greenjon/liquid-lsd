package llm.slop.spirals.config

object ProjectConfig {
    object Names {
        const val PRODUCT = "Spirals"
        const val SLUG = "spirals"
        const val DESKTOP_SUFFIX = "Desktop"
        const val APP_NAME = "$PRODUCT $DESKTOP_SUFFIX"
        const val APP_ARTIFACT_ID = "$SLUG-desktop"
    }

    object App {
        const val NAME = Names.APP_NAME
        const val MAIN_WINDOW_TITLE = "$NAME - VJ Software"
        const val UI_LAB_WINDOW_TITLE = "$NAME - UI Lab"
        const val OUTPUT_WINDOW_TITLE = "${Names.PRODUCT} Output"
        const val OUTPUT_PREVIEW_WINDOW_TITLE = "${Names.PRODUCT} Output Preview"
        const val JACK_CLIENT_NAME = Names.APP_ARTIFACT_ID
        const val SETTINGS_FILE_COMMENT = "${Names.PRODUCT} Settings"
        const val PLAYLIST_FILE_COMMENT_PREFIX = "${Names.PRODUCT} Playlist"
        const val EXIT_CONFIRM_TITLE = "Exit ${Names.PRODUCT}?"
        const val UI_LAB_TITLE = "${Names.PRODUCT} UI Lab"
        const val GITHUB_REPOSITORY_URL = "https://github.com/greenjon/${Names.APP_ARTIFACT_ID}"
    }

    object Paths {
        const val PRESETS_DIR = "presets"
        const val PATCHES_DIR = "$PRESETS_DIR/patches"
        const val PLAYLISTS_DIR = "$PRESETS_DIR/playlists"
        const val MIDI_DIR = "$PRESETS_DIR/midi"
        const val SOURCES_DIR = "$PRESETS_DIR/sources"
        const val LAST_SESSION_FILE = "$PRESETS_DIR/last_session.json"
        const val SETTINGS_FILE = "${Names.SLUG}-settings.properties"
        const val LOCAL_APP_DATA_DIR = ".${Names.APP_ARTIFACT_ID}"

        val requiredPresetDirectories = listOf(PATCHES_DIR, PLAYLISTS_DIR, MIDI_DIR)
        val managedAssetDirectories = listOf(PATCHES_DIR, PLAYLISTS_DIR)
    }

    object Files {
        const val DEFAULT_MIDI_PROFILE = "default"
        val patchExtensions = listOf("lsd", "json", "patch")
        val patchFileSuffixes = patchExtensions.map { ".$it" }
    }
}
