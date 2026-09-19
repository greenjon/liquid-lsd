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
- **FX Playlists & Live FX Queues**: Unified FX browser (ISF stock filters, saved `.lsdfx` singles, saved `.lsdfxchain` 4-slot chains) with deterministic apply, curated FX playlists (`.lsdfxplay`), and volatile live FX queues for A/B and BG with shuffle, repeat, and history back-stepping.
- **Performance Mode & Macro Controls**: `F4` toggles a 4×4 macro knob matrix spanning per-deck banks (Deck A/B/BG/PV, Transitions, Master) with color-coded rows and four context tabs (Live Quad, Dual Decks, Prep & BG, Master & FX), alongside `MacroBank` (8 knobs / 4 switches) binding and response curves edited in Classic mode.

---

## Current Status

Liquid LSD is in active beta with a stable, production-ready core video and audio pipeline.

| Subsystem | Status | Details |
| :--- | :---: | :--- |
| **Video Pipeline & FX** | **Operational** | 4 decks (A, B, BG, PV), 100% ISF 2.0 pipeline, 4-slot deck FX chains, 4-slot Master FX chain, feedback loops, ping-pong FBOs. |
| **Audio & Beat Sync** | **Operational** | Sub-millisecond JACK/PipeWire audio capture, Adam Stark beat tracking DSP, continuous phase generator, Ableton Link network sync. |
| **Transitions & Setlists** | **Operational** | ISF transition shaders, `.lsdtrans` presets, `.lsdtransplay` setlists, auto-advance transition queue, 2x2 Library panel layout. |
| **Presets & Library** | **Operational** | Hierarchical preset system, `.lsdfx` slot presets, `.lsdfxchain` 4-slot chains, FX playlists (`.lsdfxplay`), live FX queues (A/B & BG), preset tags, instant tag search, drag-and-drop preset loading. |
| **MIDI & Shortcuts** | **Operational** | Multi-type MIDI engine, soft takeover, relative encoders, customizable keyboard shortcuts, real-time packet sniffer. |
| **Video Export & Sharing** | **Operational** | Asynchronous PBO GPU video export, zero-copy Spout2/Syphon/PipeWire streaming, camera ingest, WebGL2 broadcast engine. |
| **Performance Mode & Macros** | **Operational** | `F4` 4×4 macro knob matrix (6 canonical banks: Deck A/B/BG/PV, Transitions, Master), binding, curve editing. Macro-learn/randomization coverage across control types is still being generalized — see [`ROADMAP.md`](ROADMAP.md). The 19" Modular Video Rack chassis UI this replaced has been fully removed. |

---

## Roadmap & Path to v1.0

The core v1.0 feature set has shipped: the 100% ISF pipeline, MIDI, TouchOSC/OSC control, and the 5-platform build (Linux x64/ARM64, macOS x64/ARM64, Windows x64). Performance Mode and the Macro system have shipped and are under continued refinement. What's left before v1.0 locks is finishing macro-learn/randomization coverage, some UI beautification, and general battle hardening.

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

## System Requirements

Liquid LSD is a real-time procedural visual synthesizer and low-latency audio DSP performance workstation. Rendering 4 simultaneous visual decks (A, B, BG, PV) with multi-pass ISF FX chains, feedback buffers, and audio analysis requires modern 64-bit hardware with hardware-accelerated **OpenGL 3.3 Core Profile** support.

| Component | Minimum Specification | Recommended Specification |
| :--- | :--- | :--- |
| **Operating System** | **64-bit only**:<br>• Linux (Ubuntu 20.04+, Debian 11+, Fedora 36+, Arch Linux)<br>• macOS 11.0 Big Sur or newer<br>• Windows 10 / 11 | **64-bit only**:<br>• Linux x64 / ARM64 (Wayland or X11)<br>• macOS 13+ (Apple Silicon M-Series)<br>• Windows 11 (x64) |
| **GPU / Graphics** | **Hardware OpenGL 3.3 Core Profile support**:<br>• **Intel**: HD Graphics 3000 / 4000+ (Mesa 20+ on Linux; HD 4000+ on Windows), Iris, UHD, Xe, Arc<br>• **AMD**: Radeon HD 5000+ (TeraScale 2), HD 7000+ (GCN), RX series, RDNA<br>• **NVIDIA**: GeForce 8000/9000/GT 200+ (Tesla 2.0), GeForce GTX 400+ (Fermi, Kepler, Maxwell, Pascal, Turing, Ampere, Ada)<br>• **Apple**: Apple Silicon M1+ or Metal/OpenGL 3.3+ capable Intel Macs<br>• **VRAM**: 512 MB | Dedicated GPU with **2 GB+ VRAM**:<br>• NVIDIA GeForce GTX 1060 / RTX series<br>• AMD Radeon RX 580 / RX 6000+ series<br>• Apple Silicon M-Series (Unified Memory) |
| **Processor (CPU)** | **64-bit dual-core** with SSE4.1/AVX:<br>• Intel Core i3 / i5 / i7 (2nd-Gen Sandy Bridge, 2011 or newer)<br>• AMD FX / Zen (Ryzen) series<br>• Apple Silicon M1+<br>• ARMv8 64-bit (Raspberry Pi 4/5 or equivalent) | **Quad-core or 6+ core** processor:<br>• Intel Core i5/i7 (8th-Gen or newer)<br>• AMD Ryzen 5 / 7 (3000 series or newer)<br>• Apple Silicon (M1 Pro / M2 / M3 / M4) |
| **Memory (RAM)** | **4 GB RAM** | **8 GB – 16 GB RAM** (supports ZGC sub-millisecond GC and high-res multi-deck FBOs) |
| **Display Resolution** | **1280 × 720** (minimum window size limit enforced by window manager) | **1920 × 1080 (Full HD)** or higher |
| **Audio Input** | • **Linux**: ALSA / PulseAudio via Java Sound fallback<br>• **macOS / Windows**: Standard system audio input (built-in mic, interface, or loopback) | • **Linux**: PipeWire (`pipewire-jack`) or JACK daemon for sub-millisecond latency and inter-app patchbay routing<br>• **macOS / Windows**: Low-latency USB/Thunderbolt audio interface |
| **Java / Runtime** | • **Bundled Releases**: Adoptium JRE 17+ bundled (no external Java install required)<br>• **Building from Source**: JDK 17 or higher (tested with JDK 17, 21, 25) | Bundled Adoptium JRE 17+ or JDK 21+ with ZGC |

> [!WARNING]
> **Legacy Hardware Incompatibility (Intel Core 2 Duo / Core 2 Quad / Intel GMA Graphics)**:
> Legacy processors such as **Intel Core 2 Duo / Core 2 Quad** (Conroe, Merom, Penryn ~2006–2008) and systems with **Intel GMA integrated graphics** (e.g. GMA 950, 3100, X3100, 4500MHD) or 1st-Gen Intel HD Graphics (Arrandale/Clarkdale) **do not support OpenGL 3.3 Core Profile** (hardware and drivers cap out at OpenGL 1.4 – 2.1). Attempting to launch Liquid LSD on these machines will fail immediately with `Failed to create GLFW window`. 32-bit operating systems and processors are also unsupported.

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
