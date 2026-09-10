# Liquid LSD

Liquid LSD is a real-time graphics workstation and visual instrument built for live VJing. It pairs low-latency audio reactivity with a Control Voltage (CV) modulation matrix and generative GLSL rendering, all running in a fast, keyboard-first ImGui desktop interface.

---

## Documentation Map

### 🚀 [Getting Started](getting_started.md)

Prerequisites, building from source, cross-platform audio setup, and your first 60 seconds on the decks.

### 🎨 [User Guide](user_guide/concepts.md)

Field guides for running a live show, building scenes, and routing modulation.

- **[Core Concepts & Decks](user_guide/concepts.md)**: Mandalas, dynamic visual sources, 3D projections, Deck A/B live mixer, and the Deck PV preview deck.
- **[CV Modulation & The Grid](user_guide/modulation.md)**: Routing modulators, math operators (ADD, MUL, SCALE), LFO generators, audio/trigger CVs, and mouse shortcuts.
- **[Notes & Rich Tooltips](user_guide/notes_and_tooltips.md)**: 3-tier note system (Global, Preset, Parameter), live hover tooltips, Deck Monitor labels, and the modal editor.
- **[Presets & MIDI Mapping](user_guide/midi_presets.md)**: Storing states, hardware MIDI profiles, MIDI Learn, and copying parameters between decks.
- **[Recording & Video Export](user_guide/recording_and_export.md)**: Real-time MP4 capture (REC), 4K/60fps offline rendering with motion blur, and disk persistence.
- **[Video Output & Multi-Display](user_guide/video_output.md)**: Projector routing, fullscreen output windowing, and multi-monitor display management.
- **[Web Broadcast & Retro TV](user_guide/web_broadcast.md)**: Live WebSocket streaming, CRT TV shell, rotary volume dial ($V^2$), live DSP, and 24/7 Autopilot.
- **[Library & Playlists](user_guide/library.md)**: Browsing presets, building playlists, cueing sets, and staging transitions with Auto-VJ.
- **[Custom Shaders & Sources](user_guide/custom_visuals.md)**: Adding GLSL sources to `library/sources/`, configuring `meta.json` manifests, uniform injection, and custom parameters.
- **[Trackpad Console](user_guide/trackpad_console.md)**: Turning your laptop trackpad into an SCS.3m-style 4-zone performance surface with cut stutters and video strobes.

### 🛠️ [Developer Reference](developer/architecture.md)

Engine internals, DSP pipelines, lock-free threading models, and rendering pipelines.

- **[Architecture Overview](developer/architecture.md)**: Main loop lifecycle, Thread 0 vs. Audio Thread isolation, and lock-free concurrency.
- **[Real-Time Audio & DSP](developer/audio_dsp.md)**: Zero-allocation JACK/Java Sound audio threads, Biquad IIR filter banks, RMS band splitting, and spectral flux onset detection.
- **[Beat Sync Engine](developer/beat_sync.md)**: Beat clock flywheel interpolation, manual BPM lock, PLL tracking, and STFT comb filter analysis.
- **[Modulation Pipeline](developer/modulation.md)**: `ModulatableParameter` evaluation, `CvModulator` serialization, log-cosh math, and `ParameterResolver`.
- **[OpenGL & Shaders](developer/rendering.md)**: FBO ping-pong feedback architecture, dynamic shader reloading, and resolution scaling.
- **[Media Export Pipeline](developer/export_pipeline.md)**: Async dual-PBO GPU readback, lock-free audio ring buffers, and FFmpeg pipe streaming.
- **[Web Subsystem](developer/web_subsystem.md)**: WebSocket relay architecture, state sync protocol (`state_full`/`state_delta`), and dead-reckoning sync.
- **[UI Architecture](developer/ui.md)**: ImGui lifecycle, font atlas rebuilds, modal buffers, and native memory management.
- **[Preset Storage & Queues](developer/preset_management.md)**: Non-blocking IO executor, deck dirty-state handling (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`), and playlist parsers.
- **[Roadmaps & Proposals](developer/mandala_future_roadmap.md)**: Architecture RFCs for unified control mapping, random morphing, custom titlebars, and inter-app video (Spout/Syphon).

### ⚡ [Operations & Tuning](developer/ops_tuning.md)

ZGC low-latency garbage collection tuning, PipeWire/JACK troubleshooting, and performance benchmarks.

### 📜 [Release Notes](release_notes.md)

Changelog and version history.
