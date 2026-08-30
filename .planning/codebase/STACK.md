# Technology Stack

**Analysis Date:** 2026-07-07

## Languages

**Primary:**
- Kotlin 2.0.21 - Application, rendering, UI, audio, MIDI, persistence, and tests under `src/main/kotlin/llm/slop/liquidlsd/` and `src/test/kotlin/llm/slop/liquidlsd/`.
- GLSL 330 core - OpenGL shader programs under `src/main/resources/shaders/` and dynamic visual presets under `presets/sources/*/shader.frag`.

**Secondary:**
- Kotlin Gradle DSL - Build, packaging, and distribution automation in `build.gradle.kts` and `settings.gradle.kt`.
- XML - Logback runtime logging configuration in `src/main/resources/logback.xml`.
- YAML - GitHub Actions release workflow in `.github/workflows/release.yml` and MkDocs configuration in `mkdocs.yml`.
- Markdown - User/developer documentation under `docs/`, plus project notes in `README.md`, `ARCHITECTURE.md`, and `TODO.md`.
- JSON / Java properties - Runtime data and settings via `src/main/resources/patches/default.json`, `presets/sources/*/meta.json`, generated `presets/*.json`, and generated `lsd-settings.properties`.

## Runtime

**Environment:**
- JVM 17 - Required by `kotlin { jvmToolchain(17) }` in `build.gradle.kts`; release CI installs Temurin 17 in `.github/workflows/release.yml`.
- Desktop OpenGL 3.3 core profile - GLFW hints in `src/main/kotlin/llm/slop/liquidlsd/Main.kt` request OpenGL 3.3 core with a debug context.
- JACK/PipeWire-JACK audio server - Used by `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`; the app uses `JackOptions.JackNoStartServer`, so the audio server must already be available.
- Java Sound MIDI subsystem - Used by `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt` to enumerate and open MIDI input devices.

**Package Manager:**
- Gradle Wrapper 8.5 - Configured in `gradle/wrapper/gradle-wrapper.properties`; run with `gradlew` or `gradlew.bat`.
- Maven Central - Sole dependency repository in `build.gradle.kts`.
- Lockfile: missing - No `gradle.lockfile` or dependency locking files detected.

## Frameworks

**Core:**
- LWJGL 3.3.3 - Native desktop/runtime layer for GLFW, OpenGL, OpenAL, and STB in `build.gradle.kts`; imports appear in `src/main/kotlin/llm/slop/liquidlsd/Main.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/`, and `src/main/kotlin/llm/slop/liquidlsd/ui/`.
- imgui-java 1.86.11 - Immediate-mode UI framework and LWJGL3 backend in `build.gradle.kts`; used throughout `src/main/kotlin/llm/slop/liquidlsd/ui/`.
- JNAJack 1.4.0 - JACK Audio Connection Kit binding in `build.gradle.kts`; wrapped by `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`.
- kotlinx.serialization-json 1.6.3 - JSON serialization for patches, playlists, sessions, MIDI mappings, and dynamic source metadata in `src/main/kotlin/llm/slop/liquidlsd/models/`, `src/main/kotlin/llm/slop/liquidlsd/patches/`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`, and `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`.
- kotlinx-coroutines-core 1.8.0 - Declared in `build.gradle.kts`; no source import was detected in the current pass.

**Testing:**
- kotlin.test - Test API declared by `testImplementation(kotlin("test"))` in `build.gradle.kts`; used in `src/test/kotlin/llm/slop/liquidlsd/`.
- MockK 1.13.8 - Test mocking library declared in `build.gradle.kts`; used by `src/test/kotlin/llm/slop/liquidlsd/rendering/DeckUtilityTest.kt`, `src/test/kotlin/llm/slop/liquidlsd/patches/DirtyStateTest.kt`, and `src/test/kotlin/llm/slop/liquidlsd/patches/PlayQueueManagerTest.kt`.

**Build/Dev:**
- Gradle `application` plugin - Runs `llm.slop.liquidlsd.MainKt` as configured in `build.gradle.kts`.
- Shadow plugin 8.1.1 - Builds the fat JAR used by distribution tasks in `build.gradle.kts`.
- MkDocs + Material theme - Documentation generator invoked by the `generateDocs` Gradle task in `build.gradle.kts` and installed by `.github/workflows/release.yml`; site navigation is in `mkdocs.yml`.
- Logback 1.4.14 with kotlin-logging 3.0.5 - Runtime logging stack configured by `src/main/resources/logback.xml`.
- GitHub Actions - Release workflow builds ZIP artifacts, generates release notes, and publishes releases on push to `main` and `v*` tags via `.github/workflows/release.yml`.

## Key Dependencies

**Critical:**
- `org.lwjgl:lwjgl-bom:3.3.3` - Pins native platform modules used for windowing, OpenGL rendering, OpenAL availability, and font inspection.
- `org.lwjgl:lwjgl-glfw` - Window, monitor, event polling, and OpenGL context creation in `src/main/kotlin/llm/slop/liquidlsd/Main.kt`.
- `org.lwjgl:lwjgl-opengl` - GL calls in `src/main/kotlin/llm/slop/liquidlsd/Main.kt` and rendering classes under `src/main/kotlin/llm/slop/liquidlsd/rendering/`.
- `org.lwjgl:lwjgl-stb` - Font inspection/test support in `src/test/kotlin/llm/slop/liquidlsd/ui/FontInspectorTest.kt`.
- `io.github.spair:imgui-java-binding:1.86.11` - Core ImGui binding used by UI components under `src/main/kotlin/llm/slop/liquidlsd/ui/`.
- `io.github.spair:imgui-java-lwjgl3:1.86.11` - ImGui GLFW/GL3 backend used by `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt`.
- `org.jaudiolibs:jnajack:1.4.0` - Real-time JACK client used by `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`.
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3` - Stable persistence format for `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlaylistManager.kt`, and `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`.

**Infrastructure:**
- `io.github.microutils:kotlin-logging-jvm:3.0.5` - Logging facade used across app modules including `src/main/kotlin/llm/slop/liquidlsd/Main.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/`, `src/main/kotlin/llm/slop/liquidlsd/rendering/`, and `src/main/kotlin/llm/slop/liquidlsd/ui/`.
- `ch.qos.logback:logback-classic:1.4.14` - Console logging backend configured by `src/main/resources/logback.xml`.
- `io.mockk:mockk:1.13.8` - Mocking support for unit tests in `src/test/kotlin/llm/slop/liquidlsd/`.
- LWJGL native classifiers - `natives-linux`, `natives-windows`, `natives-macos`, `natives-macos-arm64`, and `natives-linux-arm64` runtime dependencies in `build.gradle.kts`.
- imgui-java native classifiers - Linux, Windows, and macOS native artifacts declared in `build.gradle.kts`.

## Configuration

**Environment:**
- Runtime settings are stored in `lsd-settings.properties`, loaded and saved by `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`.
- Local user data is stored under `presets/`, including `presets/last_session.json`, `presets/patches/`, `presets/playlists/`, and `presets/midi/`, managed by `src/main/kotlin/llm/slop/liquidlsd/Main.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlaylistManager.kt`, and `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`.
- Dynamic visual sources are loaded from `presets/sources/*/meta.json`, `presets/sources/*/shader.frag`, and optional `presets/sources/*/shader.vert` by `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`.
- Bundled resources live in `src/main/resources/`, including `src/main/resources/shaders/`, `src/main/resources/fonts/`, `src/main/resources/patches/default.json`, and `src/main/resources/logback.xml`.
- `.env` files: not detected. Do not introduce `.env` dependency unless a future integration actually needs secrets.
- `local.properties` exists for local machine configuration and must remain local-only; do not rely on it for app runtime behavior.

**Build:**
- Primary build file: `build.gradle.kts`.
- Project name file: `settings.gradle.kt`.
- Gradle wrapper: `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, and `gradlew.bat`.
- Documentation config: `mkdocs.yml`; generated docs are emitted to `src/main/resources/docs` by the `generateDocs` task when `mkdocs` is installed.
- Logging config: `src/main/resources/logback.xml`.
- CI/release config: `.github/workflows/release.yml`.
- Distribution tasks: `shadowJar`, `packageThumbDrive`, `zipWindows`, `zipLinux`, `zipLinuxArm`, `zipMacArm`, `zipMacIntel`, and `packageZips` in `build.gradle.kts`.

## Platform Requirements

**Development:**
- Use JDK 17; Gradle enforces a JVM 17 toolchain in `build.gradle.kts`.
- Use `./gradlew build` to compile and test; `README.md` documents the command.
- Use `./gradlew run` to launch `llm.slop.liquidlsd.MainKt`; runtime opens a GLFW/OpenGL desktop window from `src/main/kotlin/llm/slop/liquidlsd/Main.kt`.
- Install `mkdocs` and `mkdocs-material` if generating bundled HTML docs locally; otherwise `build.gradle.kts` skips doc generation and uses existing resources.
- For audio-reactive features on Linux, run JACK or a PipeWire-JACK backend before starting the app; `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt` does not auto-start the server.
- On Linux, `wpctl` is used for system input volume control; on macOS, `osascript` is used by `src/main/kotlin/llm/slop/liquidlsd/audio/SystemAudioVolume.kt`.
- Project skills define hard runtime constraints: keep ImGui native memory managed using explicit resource scopes from `.agents/skills/imgui_memory_management/SKILL.md`; keep JACK callbacks non-blocking and zero-allocation from `.agents/skills/jack_callback_safety/SKILL.md`; keep GLFW/OpenGL on the primary OS thread and pass background data through thread-safe channels from `.agents/skills/lwjgl_thread_restriction/SKILL.md`.

**Production:**
- Distribution target is platform-specific ZIP archives assembled by `build.gradle.kts` into `build/distributions/`.
- Fat JAR output is `build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar` from the Shadow plugin, documented in `README.md`.
- `packageThumbDrive` downloads Temurin JRE 17 binaries from Adoptium endpoints in `build.gradle.kts` and writes launchers for Windows, Linux x64, Linux ARM64, macOS x64, and macOS ARM64.
- GitHub releases are built on `ubuntu-latest` and published automatically on push to `main` or for `v*` tags by `.github/workflows/release.yml`.
- Production runtime remains a local desktop app; no hosted server, database, auth provider, or cloud runtime was detected.

---

*Stack analysis: 2026-07-07*
