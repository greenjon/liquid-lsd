# Spirals Desktop Documentation Checklist (DOCS-TODO)

Use this checklist to draft the documentation pages in phases. The skeleton files are already created under `docs/` with all the necessary headings and subheadings.

## Phase 1: Basic setup & Getting Started (Low/Medium Complexity)
*Recommended Model: Gemini 3.5 Flash*

- [ ] **getting_started.md**
  - [ ] Write detailed prerequisites for Linux (x86_64, arm64), macOS (Intel, Apple Silicon), and Windows.
  - [ ] Detail build and packaging commands using `./gradlew build` and `./gradlew shadowJar`.
  - [ ] Describe the launch step, verifying the default audio system connects, and simple UI interaction.
- [ ] **index.md**
  - [ ] Refine the landing page text, adding visual highlights and links to key sections.

## Phase 2: User Guide & Performance Concepts (Medium Complexity)
*Recommended Model: Gemini 3.5 Flash*

- [ ] **user_guide/concepts.md**
  - [ ] Document the Mandala Synthesis engine: what lobes, color cycles, and feedback settings do.
  - [ ] Document the Dual-Deck Mixer: how Deck A and B blend together, crossfading, and blend modes.
- [ ] **user_guide/modulation.md**
  - [ ] Explain how CV sources work (both audio amplitude extraction and generated clocks/LFOs/random step, noting that waveforms are 0-centered).
  - [ ] Walk through mapping modulators: amplitude (0-1), DC Offset (-1 to 1), operators (ADD, MUL, SCALE), and bypassed states.
  - [ ] Describe how to read the cell oscilloscopes and check signals in the Sound Analysis Panel.
- [ ] **user_guide/midi_presets.md**
  - [ ] Describe how presets/patches are saved to JSON.
  - [ ] Document the base parameter copy/paste functionality.
  - [ ] Guide users on MIDI Learn targets (both base value sliders and cell modulation mappings) and explain that MIDI mappings are saved directly inside patch files.

## Phase 3: Developer Deep-Dives & Platform Operations (High Complexity)
*Recommended Model: Gemini 3.1 Pro (High)*

- [ ] **developer/architecture.md**
  - [ ] Outline the video pipeline with ASCII diagram or mermaid charts.
  - [ ] Explain threading boundaries: Thread 0 (GLFW, OpenGL context, ImGui) vs. JACK audio thread.
  - [ ] Detail concurrency safety (lock-free queues, CvHistoryBuffer, volatile variables).
- [ ] **developer/audio_dsp.md**
  - [ ] Explain zero-allocation rules in JACK callbacks (no GC triggers).
  - [ ] Document the biquad filter design, FFT extraction, and transient/onset detection algorithms.
- [ ] **developer/rendering.md**
  - [ ] Document the OpenGL framebuffer ping-pong buffer implementation.
  - [ ] Outline shader lifecycle compilation and shader uniform bindings.
  - [ ] Add notes on future support for custom user feedback/mix shaders.
- [ ] **developer/ops_tuning.md**
  - [ ] Provide JVM tuning flags for zero-pause garbage collection (ZGC) across target OSs:
    - Linux (x86_64 & arm64)
    - macOS (Intel & Apple Silicon)
    - Windows
  - [ ] Detail diagnostics for JACK/PipeWire (`jack_lsp`, `jack_connect`, `pw-link`).
  - [ ] Detail troubleshooting steps for JVM crashes, OpenGL debug context errors, and audio dropouts (xruns).
