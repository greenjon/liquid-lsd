# Spirals Desktop

Spirals Desktop is open-source VJ software for real-time, audio-reactive visual performance. It renders dual-deck parametric mandala and shader visuals, maps audio and generated CV sources into visual parameters, and exposes the performance surface through an ImGui desktop interface.

The project is in active beta: the core workflow is usable, the UI is close to its intended shape, and bug reports are welcome.

## What It Does

- Dual-deck visual mixer with per-deck feedback, blend, and monitor controls.
- Audio-reactive modulation from JACK/PipeWire-JACK input analysis.
- CV modulation matrix for amplitude bands, onset/accent triggers, beat phase, LFOs, and random/sample-and-hold sources.
- Patch, playlist, play queue, clipboard, and MIDI mapping support.
- Dynamic GLSL visual sources loaded from `presets/sources/`.
- Bundled shader and font resources under `src/main/resources/`.

## Requirements

- JDK 17.
- OpenGL 3.3 capable GPU/driver.
- JACK or PipeWire-JACK for audio-reactive features.
- Gradle Wrapper from this repository.
- Optional: `mkdocs` and `mkdocs-material` if you want to regenerate bundled documentation.

The app can launch without JACK, but audio-derived CV values will stay inactive until a JACK-compatible server is running.

## Tech Stack

- Kotlin/JVM 2.0.21.
- LWJGL 3 for GLFW, OpenGL, and native desktop integration.
- imgui-java for the immediate-mode UI.
- JNAJack for JACK audio input.
- kotlinx.serialization for patch/session data.
- Gradle Shadow for fat JAR packaging.

## Build

On Linux/macOS:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

If the Gradle daemon has local socket trouble, run the same command with `--no-daemon`.

## Run

Start JACK or PipeWire-JACK first if you want live audio analysis.

On Linux/macOS:

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
```

For UI design iteration without audio, MIDI, or session state:

```powershell
.\gradlew.bat runUiLab
.\gradlew.bat captureUiLab
```

Useful JACK/PipeWire commands:

```bash
jack_lsp
jack_connect <source> <destination>
pw-link
```

## Package

Create a fat JAR:

```bash
./gradlew shadowJar
```

The output is written to:

```text
build/libs/spirals-desktop-1.0-SNAPSHOT-all.jar
```

Platform ZIP distribution tasks are also defined in `build.gradle.kts`.

## Project Map

```text
src/main/kotlin/llm/slop/spirals/
  Main.kt                GLFW window, OpenGL context, render loop
  audio/                 JACK client, DSP, beat/audio analysis
  cv/                    CV registry, beat clock, evaluators, history buffers
  midi/                  MIDI input and mapping
  models/                Serializable patch/session DTOs
  parameters/            Modulatable parameters and CV operators
  patches/               Patch, playlist, queue, and clipboard managers
  rendering/             Decks, mixer, shaders, FBOs, visual sources
  ui/                    ImGui panels and application UI state
  utils/                 Timing utilities

src/main/resources/
  shaders/               Built-in GLSL shaders
  patches/               Default bundled patch data
  fonts/                 Bundled UI/icon fonts
  logback.xml            Logging configuration

presets/sources/         User-loadable dynamic visual sources
docs/                    MkDocs source documentation
```

For deeper implementation notes, see `ARCHITECTURE.md` and the docs site source under `docs/`.

## Development Notes

- Keep JACK callbacks real-time safe: no allocation, blocking calls, logging, or UI work in the callback path.
- Keep GLFW polling and OpenGL context usage on the primary thread.
- Manage ImGui native resources explicitly when adding UI code.
- Prefer `.\gradlew.bat --no-daemon test` on Windows if daemon startup reports a file-lock listener bind error.

## License

GPL-3.0. See `LICENSE`.
