# External Integrations

**Analysis Date:** 2026-07-07

## APIs & External Services

**Audio Host Integration:**
- JACK Audio Connection Kit / PipeWire-JACK - Captures mono audio input for audio-reactive CV and beat analysis.
  - SDK/Client: `org.jaudiolibs:jnajack:1.4.0` in `build.gradle.kts`; implementation in `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`.
  - Auth: Not applicable.
  - Runtime behavior: `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt` opens a client named `liquid-lsd-desktop`, registers one input port, uses `JackNoStartServer`, activates the client, and auto-connects to the first physical capture output port when available.
  - Consumer: `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt` runs filtering, RMS extraction, onset detection, beat estimation, and CV publication from JACK buffers.

**MIDI Devices:**
- Java Sound MIDI - Receives MIDI CC input from connected controller devices.
  - SDK/Client: JDK `javax.sound.midi.*`; implementation in `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt`.
  - Auth: Not applicable.
  - Runtime behavior: `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt` scans `MidiSystem.getMidiDeviceInfo()`, opens devices with transmitters, stores CC values in an `AtomicIntegerArray`, and queues received CC events for render-thread handling.
  - Mapping config: `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt` reads and writes mapping profiles under `presets/midi/`.

**Graphics / Window System:**
- GLFW + OpenGL via LWJGL - Creates the primary UI/render window and optional secondary output window.
  - SDK/Client: `org.lwjgl:lwjgl-glfw`, `org.lwjgl:lwjgl-opengl`, and LWJGL native classifiers in `build.gradle.kts`.
  - Auth: Not applicable.
  - Runtime behavior: `src/main/kotlin/llm/slop/liquidlsd/Main.kt` initializes GLFW, creates a 1920x1080 primary OpenGL 3.3 core window, sets vsync, creates OpenGL capabilities, and detects secondary monitors for full-screen output.
  - Constraint: Keep GLFW polling and OpenGL context manipulation on the primary OS thread per `.agents/skills/lwjgl_thread_restriction/SKILL.md`.

**Operating System Commands:**
- System input volume controls - Reads and writes default input volume where supported.
  - SDK/Client: `ProcessBuilder` in `src/main/kotlin/llm/slop/liquidlsd/audio/SystemAudioVolume.kt`.
  - Auth: Not applicable.
  - Linux command: `wpctl get-volume @DEFAULT_AUDIO_SOURCE@` and `wpctl set-volume @DEFAULT_AUDIO_SOURCE@`.
  - macOS command: `osascript -e` for input volume queries and updates.
  - Windows behavior: unsupported and disabled by `src/main/kotlin/llm/slop/liquidlsd/audio/SystemAudioVolume.kt`.
- Browser opening - Opens bundled docs or repository fallback in the system browser.
  - SDK/Client: `java.awt.Desktop` and Linux `xdg-open` fallback in `src/main/kotlin/llm/slop/liquidlsd/ui/DocManager.kt`.
  - Auth: Not applicable.
  - Fallback URL: `https://github.com/greenjon/liquid-lsd-desktop` in `src/main/kotlin/llm/slop/liquidlsd/ui/DocManager.kt`.

**Build-Time Network Services:**
- Maven Central - Resolves Gradle dependencies declared in `build.gradle.kts`.
  - SDK/Client: Gradle repository `mavenCentral()` in `build.gradle.kts`.
  - Auth: Not detected.
- Gradle distribution service - Downloads Gradle 8.5 through the wrapper.
  - SDK/Client: `gradle/wrapper/gradle-wrapper.properties`.
  - Auth: Not detected.
- Adoptium API - Downloads Temurin JRE 17 archives during `packageThumbDrive`.
  - SDK/Client: `java.net.URL(...).openStream()` in `build.gradle.kts`.
  - Auth: Not detected.
  - Endpoints: platform-specific `https://api.adoptium.net/v3/binary/latest/17/ga/...` URLs in `build.gradle.kts`.
- GitHub Actions and GitHub Releases - CI builds distribution ZIPs, generates commit-history release notes, and publishes releases automatically on push to `main` or `v*` tags.
  - SDK/Client: `.github/workflows/release.yml` uses `actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`, and `softprops/action-gh-release@v2`.
  - Auth: GitHub Actions-provided repository token through workflow permissions; no repository secret names are declared in `.github/workflows/release.yml`.
- Python package tools for docs - Release workflow installs MkDocs tooling.
  - SDK/Client: `.github/workflows/release.yml` runs `pipx install mkdocs` and `pipx inject mkdocs mkdocs-material`.
  - Auth: Not detected.

## Data Storage

**Databases:**
- Not detected.
  - Connection: Not applicable.
  - Client: No JDBC, ORM, SQLite, PostgreSQL, MySQL, Supabase, Firebase, or database SDK usage was detected.

**File Storage:**
- Local filesystem only.
  - Runtime settings: `lsd-settings.properties`, read and written by `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`.
  - Session state: `presets/last_session.json`, read and written by `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`.
  - Deck/global patches: files in `presets/patches/`, read and written by `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt` and selected through UI components under `src/main/kotlin/llm/slop/liquidlsd/ui/`.
  - Playlists: files in `presets/playlists/`, managed by `src/main/kotlin/llm/slop/liquidlsd/patches/PlaylistManager.kt` and `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`.
  - MIDI profiles: JSON files in `presets/midi/`, managed by `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`.
  - Dynamic visual sources: `presets/sources/*/meta.json`, `presets/sources/*/shader.frag`, and optional `presets/sources/*/shader.vert`, loaded by `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`.
  - Bundled docs extraction: files are copied from classpath resources to the user app-data folder `.liquid-lsd-desktop/docs` by `src/main/kotlin/llm/slop/liquidlsd/ui/DocManager.kt`.
  - Build artifacts: `build/libs/` and `build/distributions/`, produced by Gradle tasks in `build.gradle.kts`.
  - JRE cache: `build/jre-cache/`, populated by `packageThumbDrive` in `build.gradle.kts`.

**Caching:**
- Local build cache only.
  - Gradle uses normal Gradle caches through `gradlew` and `.github/workflows/release.yml` enables `cache: gradle`.
  - Downloaded JRE archives are cached in `build/jre-cache/` by `build.gradle.kts`.
  - No Redis, Memcached, CDN cache integration, or application-level remote cache was detected.

## Authentication & Identity

**Auth Provider:**
- Not detected.
  - Implementation: The app is a local desktop tool with no login, users table, OAuth flow, API key auth, or session auth code detected.
  - GitHub release publishing uses workflow permissions in `.github/workflows/release.yml`; no application authentication provider is present.

## Monitoring & Observability

**Error Tracking:**
- None.
  - No Sentry, Rollbar, OpenTelemetry exporter, hosted logging client, or crash reporting SDK was detected.

**Logs:**
- Console logging through kotlin-logging and Logback.
  - Configuration: `src/main/resources/logback.xml`.
  - Runtime usage: `mu.KotlinLogging` appears in `src/main/kotlin/llm/slop/liquidlsd/Main.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/`, `src/main/kotlin/llm/slop/liquidlsd/rendering/`, `src/main/kotlin/llm/slop/liquidlsd/ui/`, and `src/main/kotlin/llm/slop/liquidlsd/patches/`.
  - JACK callback pattern: `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt` captures callback errors into atomics and logs them on a scheduled non-real-time thread.
  - Real-time rule: Do not log, allocate, block, or perform I/O inside JACK processing callbacks per `.agents/skills/jack_callback_safety/SKILL.md` and `docs/developer/audio_dsp.md`.

## CI/CD & Deployment

**Hosting:**
- Not applicable for runtime; Liquid LSD Desktop is distributed as local desktop ZIP/JAR artifacts.
- Release artifacts are attached to GitHub Releases by `.github/workflows/release.yml`.

**CI Pipeline:**
- GitHub Actions.
  - Workflow: `.github/workflows/release.yml`.
  - Triggers: `push` tags matching `v*` and manual `workflow_dispatch`.
  - Build environment: `ubuntu-latest`, Temurin JDK 17, Gradle wrapper.
  - Docs tooling: workflow installs `mkdocs` and `mkdocs-material`.
  - Build command: `./gradlew packageZips`.
  - Outputs: platform ZIPs under `build/distributions/*.zip`, uploaded as artifacts and attached to tagged releases.

## Environment Configuration

**Required env vars:**
- None detected for application runtime.
- No `System.getenv(...)` usage was detected in source files.
- No `.env` files were present in the repo root during analysis.

**Secrets location:**
- Not detected.
- Do not store secrets in `lsd-settings.properties`, `presets/`, `local.properties`, or source-controlled Gradle files.
- `local.properties` exists and is local-machine configuration only; keep it out of source-controlled runtime assumptions.
- GitHub Actions release auth relies on repository workflow permissions in `.github/workflows/release.yml`, not explicit secret variables.

## Webhooks & Callbacks

**Incoming:**
- JACK process callback - Registered by `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`; receives audio buffers from the local JACK server.
- MIDI receiver callback - `MidiInputReceiver.send(...)` in `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt`; receives MIDI `ShortMessage` control-change events from local devices.
- GLFW callbacks - Window close and key callbacks are registered in `src/main/kotlin/llm/slop/liquidlsd/Main.kt`.
- ImGui input callbacks - UI controls use imgui-java types and callbacks in files such as `src/main/kotlin/llm/slop/liquidlsd/ui/CustomRangeSlider.kt`.
- Webhook endpoints: none detected.

**Outgoing:**
- JACK port connection - `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt` attempts to connect the app input port to the first physical capture output port.
- OS process invocations - `src/main/kotlin/llm/slop/liquidlsd/audio/SystemAudioVolume.kt` runs `wpctl` or `osascript`; `src/main/kotlin/llm/slop/liquidlsd/ui/DocManager.kt` may run `xdg-open`.
- Browser navigation - `src/main/kotlin/llm/slop/liquidlsd/ui/DocManager.kt` opens local documentation or the GitHub repository fallback in the default browser.
- Build-time downloads - `build.gradle.kts` downloads JRE archives from Adoptium during `packageThumbDrive`; Gradle downloads dependencies from Maven Central.
- Outbound webhooks: none detected.

---

*Integration audit: 2026-07-07*
