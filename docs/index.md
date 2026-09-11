# Liquid LSD

Liquid LSD is open-source VJ software for real-time, audio-reactive visuals. It drives generative shaders and procedural geometry from a live audio feed, runs everything through an analog-style modulation matrix, and gives you a fast keyboard-driven performance interface for playing it live.

It's in active beta — the core workflow is solid and usable, and the docs here aim to help you get comfortable with it as quickly as possible.

---

## Where to start

**Never used Liquid LSD before?** Start with [Getting Started](getting_started.md) to get the app running and your audio routed in.

**Getting your bearings?** [Your Workspace](user_guide/your_workspace.md) explains the layout, the four decks, and how everything connects.

---

## User Guide

- **[Your Workspace](user_guide/your_workspace.md)** — The layout explained: four decks, the mixer, signal flow, and the two main modes.
- **[Visual Sources](user_guide/visual_sources.md)** — The built-in generators (Mandala, Icosa-Dodeca, Gyroid, and more), the FX chain, feedback loops, and how to load your own ISF shaders.
- **[Modulation](user_guide/modulation.md)** — How to wire audio, LFOs, sequencers, and MIDI to your visual parameters using the CV grid.
- **[Presets & Library](user_guide/presets_and_library.md)** — Saving and loading presets, building setlists, the Auto-VJ queue, and MIDI mapping.
- **[Output & Recording](user_guide/output_and_recording.md)** — Sending video to projectors and other apps, recording your set, and exporting high-quality renders.
- **[Performance Controls](user_guide/performance_controls.md)** — The trackpad performance surface, custom notes, and tooltips.
- **[Web Broadcast](user_guide/web_broadcast.md)** — Live-streaming visual parameters to a browser-based visualizer. *(Experimental — requires a relay server.)*

---

## Developer Reference

If you're building on or contributing to Liquid LSD, the developer docs cover the engine internals:

- [Architecture Overview](developer/architecture.md)
- [Real-Time Audio & DSP](developer/audio_dsp.md)
- [Beat Sync Engine](developer/beat_sync.md)
- [Modulation Pipeline](developer/modulation.md)
- [OpenGL & Shaders](developer/rendering.md)
- [Media Export Pipeline](developer/export_pipeline.md)
- [Web Subsystem](developer/web_subsystem.md)
- [UI Architecture](developer/ui.md)
- [Preset Storage & Queues](developer/preset_management.md)
- [Operations & Tuning](developer/ops_tuning.md)

---

## Release Notes

[Full changelog](release_notes.md)
