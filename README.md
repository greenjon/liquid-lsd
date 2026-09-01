# Liquid LSD - Libre Shader Decks

Liquid LSD is open-source VJ software for real-time, audio-reactive visual performance. It renders dual-deck parametric mandala and shader visuals, maps audio and generated CV sources into visual parameters, and exposes the performance surface through an ImGui desktop interface.

The project is in active beta: the core workflow is usable, the UI is close to its intended shape, and bug reports are welcome.

## What It Does

- Dual-deck visual mixer with per-deck feedback, blend, and monitor controls.
- Audio-reactive modulation from JACK/PipeWire-JACK (Linux) or cross-platform Java Sound fallback (macOS/Windows/Linux fallback).
- CV modulation matrix for amplitude bands, onset/accent triggers, beat phase, LFOs, and random/sample-and-hold sources.
- Preset, playlist, play queue, clipboard, and MIDI mapping support.
- Dynamic GLSL visual sources loaded from `library/sources/`.
- Bundled shader and font resources under `src/main/resources/`.

## Requirements

- JDK 17.
- OpenGL 3.3 capable GPU/driver.
- Audio input device: JACK or PipeWire-JACK (recommended on Linux for superior low latency and routing) or any standard system audio input (works out-of-the-box via Java Sound on macOS, Windows, and JACK-less Linux).
- Gradle Wrapper from this repository.
- Optional: `mkdocs` and `mkdocs-material` if you want to regenerate bundled documentation.

## Tech Stack

- Kotlin/JVM 2.0.21.
- LWJGL 3 for GLFW, OpenGL, and native desktop integration.
- imgui-java for the immediate-mode UI.
- JNAJack (JACK) and Java Sound (fallback/cross-platform) for audio input.
- kotlinx.serialization for preset/session data.
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

On Linux, starting JACK or PipeWire first is recommended for superior low-latency analysis and inter-app audio routing. Otherwise, the app automatically captures from the system's default audio input device using Java Sound.

On Linux/macOS:

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
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
build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar
```

Platform ZIP distribution tasks are also defined in `build.gradle.kts`.

## Website & Documentation Export (greenjon.com)

Generate the complete static website, responsive HTML documentation, and offline documentation ZIP bundle into `./greenjon/` ready for FTP upload:

```bash
./gradlew buildWebsite
```

This compiles all guides from `docs/` and `RELEASE_NOTES.md` into `./greenjon/docs/`, creates `greenjon/docs.zip`, renders `greenjon/index.html` with current version and release links, and bundles static assets. Upload the contents of `./greenjon/` directly to your web server.

## Project Map

```text
src/main/kotlin/llm/slop/liquidlsd/
  Main.kt                GLFW window, OpenGL context, render loop
  SessionContext.kt      Application state & context
  audio/                 JACK client, Java Sound fallback, DSP, beat/audio analysis
  cv/                    CV registry, beat clock, evaluators, history buffers
  export/                Video & audio render export, OfflineRenderStudio
  midi/                  MIDI input, profiles, and mapping
  models/                Serializable preset/session DTOs
  notes/                 NotesManager and 3-tier notes persistence
  parameters/            Modulatable parameters and CV operators
  presets/               Preset, playlist, queue, and clipboard managers
  rendering/             Decks, mixer, shaders, FBOs, VisualSources, SourceDocRegistry
  ui/                    ImGui panels, browser subpackage, NoteEditorModal, UI state
  utils/                 Timing utilities

src/main/resources/
  shaders/               Built-in GLSL shaders
  presets/               Default bundled preset data
  fonts/                 Bundled UI/icon fonts
  logback.xml            Logging configuration

library/sources/         User-loadable dynamic visual sources
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
