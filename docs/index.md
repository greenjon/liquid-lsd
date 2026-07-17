# Liquid LSD Documentation

Welcome to the documentation for **Liquid LSD**, a real-time graphics and audio workstation designed for VJs and live visual performances.

Liquid LSD combines real-time low-latency audio analysis, a CV modulation matrix, and high-performance generative visual rendering in a single desktop application.

## Documentation Map

### [Getting Started](getting_started.md)
Prerequisites, cross-platform build instructions (Linux, macOS, Windows), and first-launch walkthrough.

### [User Guide](user_guide/concepts.md)
Detailed walkthroughs of the visual synth engine, CV routing matrix, and MIDI/Preset controls.
- **[Core Concepts](user_guide/concepts.md)**: Mandalas, decks, and mixing.
- **[CV Modulation](user_guide/modulation.md)**: Modulation routing, operators, and generators (LFOs, Clocks, Random).
- **[Presets & MIDI Mapping](user_guide/midi_presets.md)**: Preset saving/loading, clipboard, and MIDI Learn.
- **[Custom Visuals](user_guide/custom_visuals.md)**: Add or create your own custom shaders and dynamic visuals.

### [Developer Reference](developer/architecture.md)
Under-the-hood details of the application architecture, DSP engine, and rendering pipelines.
- **[Architecture Overview](developer/architecture.md)**: Main loop, threading boundaries, and concurrency safety.
- **[Real-Time Audio & DSP](developer/audio_dsp.md)**: Zero-allocation JACK callbacks, biquad filters, and FFT.
- **[Beat Sync & Stability](developer/beat_sync.md)**: Transient detection, median-filtered BPM estimation, and phase accumulation.
- **[OpenGL Rendering](developer/rendering.md)**: Ping-pong framebuffers, shader compiles, and visual rendering math.
- **[Operations & Tuning](developer/ops_tuning.md)**: JVM optimization flags (ZGC), PipeWire/JACK diagnostics, and troubleshooting.
