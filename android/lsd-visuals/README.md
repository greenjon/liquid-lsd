# lsd-visuals

The Liquid LSD visuals for Android apps: the web TV's engine (`web/`, in its `?embed` mode) in a WebView,
signed content updates from the web TV's server, and the audio feed. Used by the Spaz Radio app and the
VJ app.

**Edit it here, in liquid-lsd.** Apps carry a copy of this folder as a Gradle module, made by
`scripts/sync_lsd_visuals.sh <app-dir>`, which also writes the web engine into the copy's
`src/main/assets/visuals/` and its liquid-lsd commit into `VERSION`. The app commits the copy, so its builds
never need a liquid-lsd checkout. Re-run the script and commit whenever `web/` or this module changes.

In the app:

```kotlin
// settings.gradle.kts
include(":lsd-visuals")
// app/build.gradle.kts
implementation(project(":lsd-visuals"))
```

The host build must have the Android, Kotlin Android and Compose compiler plugins on its classpath (any
Compose app does).

## API (package `org.liquidlsd.visuals`)

- `LsdVisuals(settings, active, onFailed, modifier)`: the picture as a Compose element.
- `LsdPage`: the plain WebView (`create`, `applySettings`, `pause`, `resume`, `feed`), for hosts outside
  Compose such as a `Presentation` on an external display.
- `PcmTap`: push 16-bit PCM in (`configure(rate, channels)`, `onBuffer(buffer)`) from any audio source; it
  reaches the page while `LsdVisuals` is active.
- `VisualsSettings`: quality, reactivity, reduced motion, live, sync, content updates.
- `ContentUpdates`: `schedule(context, enabled)` (daily check), `runCheck(context)`, `store(context).version()`.
  The manifest is made by `scripts/make_content_manifest.py`; `ContentUpdater.ENGINE` must match
  `CONTENT_ENGINE` there.

Unit tests (`ContentUpdaterTest`, `PcmMonoChunkerTest`) run in the host app: `./gradlew :lsd-visuals:testDebugUnitTest`.
