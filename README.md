# Liquid LSD — Libre Shader Decks

Liquid LSD is an open-source, real-time procedural visual synthesizer and VJ performance workstation. It renders multi-deck parametric mandala and shader visuals, processes audio feeds and generated CV modulators into visual parameters, and provides a low-latency, tactile performance interface built with Kotlin/JVM, OpenGL 3.3, and Dear ImGui.

---

## What It Does

- **4-Deck Visual Engine**: Independent visual decks for live output (**Deck A** and **Deck B**), a background compositor layer (**Deck BG**), and an offline audition deck (**Deck PV**) for previewing and tweaking presets live without affecting stage output.
- **100% ISF 2.0 Shader Pipeline**: Native Interactive Shader Format (ISF 2.0) engine for visual generators, filters, and transitions. Includes an automated compatibility bridge for importing Shadertoy (`mainImage`) and GLSLSandbox shaders without code edits.
- **Multi-Slot Deck & Master FX Chains**: 4-slot serial ISF FX chains per deck plus a 4-slot serial Master FX post-processing stage (bloom, CRT glitch, color correction, spatial distortion) operating across the final composite output.
- **Modular Transitions & Setlist Queue**: ISF transition engine (Deck A $\leftrightarrow$ Deck B), custom transition presets (`.lsdtrans`), setlists (`.lsdtransplay`), auto-advance transition queue, and VJ crossfader controls.
- **Low-Latency Audio & Beat Sync**: Real-time audio analysis via JACK / PipeWire-JACK (Linux) or cross-platform Java Sound fallback (macOS/Windows/Linux). Features an Adam Stark-based beat tracking engine with a continuous phase generator and Ableton Link network clock sync.
- **Hierarchical CV Modulation Matrix**: Route real-time audio amplitude bands (Bass/Mid/High), onset triggers, beat clock phases, LFOs, and sample-and-hold generators to any visual parameter.
- **Tactile Performance Surface**: Modern frameless CSD windowing with live performance telemetry (FPS, DSP latency, CPU usage, frame time, beat phase), TouchConsole support (evdev/macOS), 2x2 grouped Library panel, drag-and-drop workflow, and clipboard management for presets, slots, and chains.
- **Hardware & MIDI Control**: Multi-type MIDI engine (Notes, CC, Pitch Bend, Soft Takeover, Relative Rotary Encoders), centralized `ShortcutManager` for keyboard shortcuts, preset tagging & search, and 3-tier hierarchical set notes (`NotesManager`).
- **Stage Interoperability & Recording**: Zero-copy GPU video streaming (Spout2 on Windows, Syphon on macOS, PipeWire DMA-BUF on Linux), video device/OBS ingest, high-performance asynchronous GPU PBO video export/recording pipeline (`PboReadbackPipeline`), and WebGL2 live web broadcast relay.
- **Modular Video Rack & Macro Controls**: 19" rack-style alternate workspace with curated per-unit performance faceplates, embedded confidence micro-monitors, per-unit `MacroBank`s (8 knobs / 4 switches), and a Reason-style `Tab`-flip rear chassis with virtual patch cables.

---

## Current Status

Liquid LSD is in active beta with a stable, production-ready core video and audio pipeline.

| Subsystem | Status | Details |
| :--- | :---: | :--- |
| **Video Pipeline & FX** | **Operational** | 4 decks (A, B, BG, PV), 100% ISF 2.0 pipeline, 4-slot deck FX chains, 4-slot Master FX chain, feedback loops, ping-pong FBOs. |
| **Audio & Beat Sync** | **Operational** | Sub-millisecond JACK/PipeWire audio capture, Adam Stark beat tracking DSP, continuous phase generator, Ableton Link network sync. |
| **Transitions & Setlists** | **Operational** | ISF transition shaders, `.lsdtrans` presets, `.lsdtransplay` setlists, auto-advance transition queue, 2x2 Library panel layout. |
| **Presets & Library** | **Operational** | Hierarchical preset system, `.lsdfx` slot presets, `.lsdfxchain` 4-slot chains, preset tags, instant tag search, drag-and-drop preset loading. |
| **MIDI & Shortcuts** | **Operational** | Multi-type MIDI engine, soft takeover, relative encoders, customizable keyboard shortcuts, real-time packet sniffer. |
| **Video Export & Sharing** | **Operational** | Asynchronous PBO GPU video export, zero-copy Spout2/Syphon/PipeWire streaming, camera ingest, WebGL2 broadcast engine. |
| **Modular Video Rack & Macros** | **Core Complete** | 19" rack workspace, curated faceplates, per-unit macro banks & confidence monitors, `Tab`-flip rear patch bay. Patch cables reroute pixels for custom Utility units only — see [`ROADMAP.md`](ROADMAP.md) for the built-in-unit limitation and remaining backlog. |

---

## Roadmap & Path to v1.0

The core v1.0 feature set has shipped: the 100% ISF pipeline, TouchOSC/OSC control, the 5-platform build (Linux x64/ARM64, macOS x64/ARM64, Windows x64), and the Modular Video Rack & Macro system (Phases 1-9, Core Complete) are all implemented. What's left before v1.0 locks is parked as the v1.1 backlog:

### v1.1 Backlog

1. **Unified Control Mapping & Hardware Profiles**:
   - Decouple all user actions into a universal `CommandRegistry` for hardware controllers, MIDI, keyboard shortcuts, and GUI.
   - Pre-packaged controller profiles (`library/mappings/`) for Launchpad, APC40, Pioneer DDJ, and Midi Fighter.
   - Universal right-click "Learn" overlay across all UI widgets.

2. **Session Scratchpad & Live Notes**:
   - Floating or docked set scratchpad window (`~/.liquid-lsd/scratchpad.txt`) for persistent set notes during live performances.

3. **Mandala Visual Generator v2+ Recipe Vault**:
   - Recipe gallery popover featuring micro-previews of ~300 built-in recipes grouped by lobe counts.
   - Geometric style tagging, global recipe sweep LFO index, and quick-recall performance bookmark slots.

*For complete details and progress tracking — including known limitations of what's already shipped — see [`ROADMAP.md`](ROADMAP.md).*

---

## Requirements

- **Java JDK**: JDK 17 or higher.
- **GPU**: OpenGL 3.3 capable GPU and drivers.
- **Audio Input**:
  - **Linux**: JACK or PipeWire-JACK recommended for sub-millisecond latency and inter-app audio routing. Automatic Java Sound fallback available.
  - **macOS / Windows**: Standard system audio input device via Java Sound (works out-of-the-box).
- **Build Tool**: Gradle Wrapper (included).

---

## Tech Stack

- **Language & Runtime**: Kotlin 2.0.21 on JVM 17+.
- **Graphics & Windowing**: LWJGL 3 (GLFW, OpenGL 3.3 Core Profile).
- **UI Engine**: `imgui-java` 1.92.7.1 (Dear ImGui) with Inter & JetBrains Mono fonts and Lucide icons.
- **Shader Pipeline**: ISF 2.0 parser & preprocessor, GLSL 330 core shaders.
- **Audio Subsystem**: JNAJack (JACK/PipeWire) and Java Sound API fallback.
- **Tempo & Network Sync**: Ableton Link C++ JNI (`liblink_jni`) and Carabiner TCP client interface.
- **Serialization**: `kotlinx.serialization` (JSON) for presets, sessions, transitions, FX chains, and preferences.

---

## Build

On Linux/macOS:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

*(If the Gradle daemon encounters local socket issues, add `--no-daemon` to the command.)*

---

## Run

Launch the application:

On Linux/macOS:

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
```

**Linux Audio Tip**: For optimal performance and zero-latency inter-app routing on Linux, launch PipeWire/JACK prior to starting Liquid LSD:

```bash
jack_lsp
jack_connect <source> <destination>
pw-link
```

---

## Package & Distribute

Create a self-contained fat JAR:

```bash
./gradlew shadowJar
```

Output binary:

```text
build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar
```

Platform distribution ZIP tasks are available in `build.gradle.kts` (`zipLinux`, `zipLinuxArm`, `zipMacArm`, `zipMacIntel`, `zipWindows`).

---

## Website & Documentation Export

Generate the complete static website, responsive HTML documentation, and offline documentation ZIP bundle into `./greenjon/` ready for web deployment:

```bash
./gradlew buildWebsite
```

This compiles guides from `docs/` and `RELEASE_NOTES.md` into `./greenjon/docs/`, creates `greenjon/docs.zip`, and updates `greenjon/index.html`.

---

## Project Map

```text
src/main/kotlin/llm/slop/liquidlsd/
  Main.kt                GLFW window lifecycle, CSD titlebar, render loop
  SessionContext.kt      Global application context & state
  audio/                 JACK client, Java Sound fallback, DSP, Beat Tracker, volume control
  link/                  Ableton Link network sync (Native JNI & Carabiner TCP backends)
  broadcast/             WebSocket relay client & WebGL2 TV state serializer
  cv/                    CV registry, BeatClock, evaluator functions, history ring buffers
  export/                Asynchronous GPU PBO video renderer & export studio
  input/                 TouchConsole controller (evdev/macOS JNA drivers)
  midi/                  Multi-message MIDI receiver, soft takeover, relative encoders
  models/                Preset, session, transition, and FX chain DTO schemas
  notes/                 NotesManager & 3-tier set notes persistence
  parameters/            Modulatable parameters, CV operators, parameter state
  presets/               Preset, playlist, queue, transition queue, clipboard managers
  rendering/             Decks, mixer, ISF engine, shaders, FBOs, VisualSources
  ui/                    Dear ImGui panels, 2x2 Library, property editors, modals, CSD controls
  utils/                 Timing and mathematical utilities

src/main/resources/
  shaders/               Built-in GLSL shaders & ISF transitions
  presets/               Bundled default preset data
  fonts/                 Bundled Inter, JetBrains Mono, & Lucide fonts
  logback.xml            Logging configuration

library/                 User-loadable dynamic assets
  sources/               GLSL & ISF visual generator sources
  filters/               ISF effect filters (Slots 1-4)
  transitions/           ISF transition filters & .lsdtrans presets
  transition_playlists/  .lsdtransplay transition setlists
  fx/                    .lsdfx single-slot FX presets
  fx_chains/             .lsdfxchain 4-slot FX chain presets

docs/                    MkDocs documentation sources
```

---

## Developer Guidelines

- **Zero-Allocation Audio/Render Path**: Keep JACK audio callbacks and Thread 0 render loops 100% allocation-free (no lambda state capture, collection allocations, string formatting, or blocking I/O).
- **Single-Threaded Windowing/GL**: Maintain all GLFW event polling and OpenGL context operations strictly on the primary OS thread (Thread 0).
- **ImGui Native Memory**: Manage ImGui native objects and texture handles explicitly to prevent JVM native memory leaks.
- **Documentation Maintenance**: Keep `ARCHITECTURE.md`, `ROADMAP.md`, `RELEASE_NOTES.md`, and `docs/` synchronized with code changes.

---

## License

GNU General Public License v3.0 (GPL-3.0). See [`LICENSE`](LICENSE).
