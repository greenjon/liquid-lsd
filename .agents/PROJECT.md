# Spirals Desktop — Agent Context

**Read this file first.** It gives you enough orientation to work on any task.
For deeper context, consult the subsystem notes listed at the bottom — only read them if your task touches that area.

---

## What This App Is

Spirals Desktop is a **Linux VJ (video jockey) application** written in **Kotlin/JVM**.
It renders real-time audio-reactive visuals — primarily parametric mandala geometry and
user-defined GLSL shaders — and exposes a live performance surface through an ImGui desktop UI.

**The primary user is a live performer.** They load patches, map audio/CV signals to visual
parameters, and mix between two decks in real time.

---

## Tech Stack (quick ref)

| Layer | Library | Notes |
|-------|---------|-------|
| Window/GL | LWJGL 3 (GLFW + OpenGL 3.3) | Single primary thread only |
| UI | imgui-java | Immediate-mode; native memory managed explicitly |
| Audio | JNAJack (JACK/PipeWire) | **Linux only** — fallbacks for other platforms TBD |
| Serialization | kotlinx.serialization | Patches, playlists, MIDI maps |
| Build | Gradle + Shadow | `./gradlew run` to launch |

**Build targets:** Linux x64, Linux ARM64, macOS x64, macOS ARM64, Windows x64.
Distribution: `./gradlew packageThumbDrive` bundles the fat JAR + bundled JREs for all 5 platforms.
Audio-reactive CV features are only active on Linux (JACK/PipeWire). On other platforms,
audio CVs stay at 0 but all visuals and MIDI continue to work.

---

## The Three Hard Constraints

These apply to every change. Violating them causes xruns, segfaults, or crashes.

1. **Audio callback is zero-alloc and non-blocking.** No `new`, no lambdas capturing state,
   no I/O, no logging inside `JackClient` / `AudioEngine` / `DSP`. See skill `jack_callback_safety`.

2. **GLFW and OpenGL must run on Thread 0.** No GL calls from background threads.
   Data flows audio→render via lock-free ring buffers (`CvHistoryBuffer`, volatile fields).
   See skill `lwjgl_thread_restriction`.

3. **ImGui native memory must be explicitly freed.** Stack allocators and native ImGui objects
   (e.g. `ImString`, `ImGuiIO` buffers) are not GC'd. See skill `imgui_memory_management`.

---

## Source Layout (one-liner per package)

```
src/main/kotlin/llm/slop/spirals/
├── Main.kt              — GLFW init, render loop, top-level lifecycle
├── audio/               — JACK client, DSP (FFT/RMS/onset), beat analysis
├── cv/                  — CV registry, beat clock, evaluators, history ring buffers
├── midi/                — MIDI input polling and CC→parameter mapping
├── models/              — @Serializable DTOs (patch, playlist, MIDI map)
├── parameters/          — ModulatableParameter, CvModulator, waveforms, operators
├── patches/             — Save/load patches, play queue, playlists, clipboard
├── rendering/           — Decks, Mixer, Mandala, shaders, FBOs, VisualSource abstraction
├── ui/                  — UIManager, PatchGridPanel, CellConfigPanel, PatchGridState
└── utils/               — TimeUtils
```

User-loadable GLSL visual sources live in `presets/sources/` (not in `src/`).
Built-in shaders, fonts, and bundled patches are in `src/main/resources/`.

---

## Data Flow (bird's-eye)

```
JACK audio input
    └─► AudioEngine / DSP  (RT thread)
            └─► CVRegistry  (volatile writes)
                    └─► ModulatableParameter.evaluate()  (render thread, per-frame)
                            ├─► Deck A / Deck B  →  FBO ping-pong  →  Mixer  →  screen
                            └─► Deck C  →  FBO ping-pong  →  preview only (not in final output)
```

ImGui reads the same `CVRegistry` values for the oscilloscope and meter displays.
MIDI CCs are polled each frame from a queue and applied to parameters or UI state.

---

## Key Classes to Know

| Class | Role |
|-------|------|
| `CVRegistry` | Singleton; all CV source values live here |
| `ModulatableParameter` | A float param with up to N `CvModulator`s stacked on it |
| `CvModulator` | One modulation slot: source id + weight + operator (ADD/MUL) + waveform |
| `Deck` | One visual channel: holds a `VisualSource` + ping-pong FBOs + feedback params |
| `Mixer` | Blends Deck A + Deck B → `masterFBO` → screen (Deck C is excluded from output) |
| `Renderer` | Orchestrates per-frame: source→feedback→mix→blit |
| `UIManager` | Top-level ImGui layout (PatchGrid 40% | CellConfig 30% | Mixer 30%) |
| `PatchManager` | Save/load patches; owns current patch state |

---

## Build & Run

```bash
./gradlew run           # launch (JACK/PipeWire should be running for audio CVs)
./gradlew compileKotlin # fast type-check without running
```

Launch flags (in `build.gradle.kts` JVM args): `-XX:+UseZGC -XX:MaxGCPauseMillis=2`

---

## Deeper Context — Read When Relevant

Only read these if your task touches the area. Don't load all of them by default.

| Task area | Where to look |
|-----------|--------------|
| Audio thread / JACK callback | Skill: `jack_callback_safety` · `docs/developer/audio_dsp.md` |
| Beat sync / BPM / BeatClock | `docs/developer/beat_sync.md` |
| OpenGL / FBOs / shaders / Mandala | Skill: `lwjgl_thread_restriction` · `docs/developer/rendering.md` |
| ImGui / UI panels / native memory | Skill: `imgui_memory_management` · `docs/developer/ui.md` |
| CvModulator fields / LFO2 / evaluate() | `docs/developer/modulation.md` |
| Full architecture diagram & CV source table | `ARCHITECTURE.md` |
| Performance / GC tuning / xrun diagnosis | `docs/developer/ops_tuning.md` |
| Patch serialization / save-load | `models/PatchModels.kt` · `patches/PatchManager.kt` |
| Dynamic GLSL visual sources | `rendering/VisualSourceRegistry.kt` · `rendering/DynamicVisualSource.kt` |
| MIDI mapping | `midi/MidiEngine.kt` · `midi/MidiMappingManager.kt` |

---

## Current State & Known Gaps

See `TODO.md` for the active task list.
After completing significant work, update `TODO.md` and add a dated entry to `DECISIONS.md`
(create it if it doesn't exist yet) explaining any non-obvious choices made.
