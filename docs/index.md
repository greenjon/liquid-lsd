# Liquid LSD Documentation

Welcome to the documentation for **Liquid LSD**, a cross-platform real-time graphics and audio workstation designed for VJs and live visual performances.

Liquid LSD combines real-time low-latency audio analysis (via JACK/PipeWire on Linux or Java Sound on macOS, Windows, and Linux), a Control Voltage (CV) modulation matrix, and high-performance generative GLSL rendering in a single ImGui desktop application.

---

## Documentation Map

### 🚀 [Getting Started](getting_started.md)
System prerequisites, compilation instructions (Linux, macOS, Windows), fat JAR packaging, and first-launch walkthrough.

### 🎨 [User Guide](user_guide/concepts.md)
Comprehensive guides for visual synthesis, modulation matrix routing, setlist management, custom shaders, and rich tooltips.
- **[Core Concepts & Visual Sources](user_guide/concepts.md)**: Mandalas, Dynamic Visual Sources, 3D projections, Deck A/B live mixer, and Deck PV preview deck.
- **[CV Modulation & Preset Grid](user_guide/modulation.md)**: Modulation routing matrix, operator math (ADD, MUL, SCALE), LFO 1/2 generators, audio/trigger CVs, and power-user mouse shortcuts.
- **[Documentation & Notes System](user_guide/notes_and_tooltips.md)**: 3-tier note system (Global Source Notes, Preset Notes, Parameter Notes), rich UI hover tooltips, Deck Monitor preset name labels, and modal note editor.
- **[Presets & MIDI Mapping](user_guide/midi_presets.md)**: Preset saving/loading, hardware MIDI profiles vs. preset grid cell modulators, MIDI Learn, and deck parameter clipboard.
- **[Library & Playlists](user_guide/library.md)**: Preset library navigation, playlist editor, Auto-VJ play queue, and drag-and-drop setlist staging.
- **[Custom Shaders & Sources](user_guide/custom_visuals.md)**: Adding dynamic visual sources in `library/sources/`, `meta.json` manifests, parameter grouping, GLSL uniform injection, and custom shader creation.

### 🛠️ [Developer Reference](developer/architecture.md)
In-depth technical architecture, threading boundaries, DSP algorithms, rendering math, and UI design patterns.
- **[Architecture Overview](developer/architecture.md)**: Main loop lifecycle, threading boundaries (Thread 0 vs Audio Thread), concurrency primitives, and lock-free queues.
- **[Real-Time Audio & DSP](developer/audio_dsp.md)**: Zero-allocation JACK & Java Sound callback loops, Biquad IIR filter banks, RMS band splitting, and spectral flux onset detection.
- **[Beat Sync & Stability](developer/beat_sync.md)**: Sub-millisecond beat clock flywheel interpolation, manual BPM lock, PLL tracking, STFT comb filters, autocorrelation, and background thread safety.
- **[Modulation Engine](developer/modulation.md)**: `ModulatableParameter` evaluation pipeline, `CvModulator` serialization & DTO migration (`gen1`/`gen2` $\rightarrow$ `lfo`), log-cosh waveform math, and `ParameterResolver`.
- **[OpenGL Rendering](developer/rendering.md)**: Framebuffer Object (FBO) ping-pong feedback architecture, `SourceDocRegistry`, dynamic shader ownership, and pipeline resolution scaling.
- **[UI Architecture & ImGui](developer/ui.md)**: `UIManager` lifecycle, `PresetGridState`, `UITheme` font atlas deferred rebuilds, `NoteEditorModal` pre-allocated buffer safety, and native ImGui memory management rules.
- **[Preset & Queue Management](developer/preset_management.md)**: Async preset IO executor, `NotesManager` persistence, `PlayQueueManager` AutoVJ dirty deck handling (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`), and `PlaylistParser`.
- **[Mandala UX Roadmap](developer/mandala_future_roadmap.md)**: Future design roadmap for visual recipe vault popover, geometric tags, global recipe index, and performance quick-slots.

### ⚡ [Operations & Tuning](developer/ops_tuning.md)
Low-latency JVM Garbage Collector tuning (ZGC), cross-platform setup, PipeWire/JACK CLI diagnostics, and troubleshooting guide.

### 📜 [Release Notes](release_notes.md)
Version changelogs and history across releases.
