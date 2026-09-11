# Output & Recording

This page covers everything about where your visuals go: sending them to projectors and other apps, recording your live set, and exporting a polished high-res render afterwards.

---

## Fullscreen & Projector Output

The simplest way to output: press **`F`** to go fullscreen. The UI disappears and the master output fills the screen. Press **`Esc`** to come back.

For a second display or projector, use **Settings → Video & Display** to configure a secondary output window you can drag to the projector screen and go fullscreen there independently.

**Output scaling:**
- If your shaders are demanding and your GPU is struggling (especially on a laptop), lower the internal render resolution in **Settings → Video & Display**. Dropping from 1080p to 720p or 540p cuts GPU load significantly while the output still fills the screen.
- Use **Fit / Fill / Stretch** to handle non-standard aspect ratios — useful for LED walls or vintage 4:3 projectors.

---

## Sending Video to Other Apps (Spout / Syphon / PipeWire)

Liquid LSD can share its video feeds live to other VJ apps, media servers, and streaming tools — without any encoding overhead or latency.

Go to **Settings → Video Sharing Matrix** and toggle which feeds you want to share:

- **Deck A, Deck B, Deck BG, Deck PV (Preview), Master Output** — enable any combination.
- Set a custom stream name for each (e.g. `LiquidLSD-Master`, `MainStage-Feed`).
- Choose a resolution for the shared stream, or keep it synced to the master.
- Pick how the image is scaled if the resolutions don't match (Fit, Fill, or Stretch).

The sharing method depends on your platform:

- **Windows:** Spout2
- **macOS:** Syphon
- **Linux:** PipeWire (DMA-BUF GPU sharing, with a memory fallback if the GPU doesn't support it)

**On Linux with OBS:** Add a **PipeWire Screen Capture** source and select the Liquid LSD video node. Use `qpwgraph` or `Helvum` to inspect and route video nodes.

**Status:** Check the **Settings → Video Sharing** panel for a live status indicator showing which sharing method is active and whether it's working.

---

## Recording Your Set (REC)

Press **`Ctrl+R`** (or go to **Output → Record Master Output**) to start recording. A red `REC mm:ss` badge appears on the title bar while recording is active, along with a dropped-frame counter.

Press the same shortcut again, or click **Stop Recording**, to finish. The output file lands in the `recordings/` folder as an MP4, named with the date and time (e.g. `liquid_lsd_2026-09-01_20-15-00.mp4`).

**What you get:** H.264 video at your current render resolution and frame rate, with synchronized audio from your input. On capable hardware, NVENC, QuickSync, or VAAPI hardware encoding is used automatically.

You can also map the `REC` toggle to a MIDI controller button so you never have to touch the keyboard during a performance.

---

## Offline Render Studio

The Offline Render Studio is for making video content — music videos, promotional visuals, or anything where quality matters more than real-time speed. It renders frame by frame at whatever resolution and frame rate you like, fully decoupled from the clock.

Open it via **Output → Export Video (Offline Studio)...**.

### What you can set

- **Audio file** — A `.wav`, `.flac`, `.mp3`, or `.ogg` file that drives the visual modulation.
- **Preset source** — The current active deck, or any saved preset.
- **Resolution** — 1080p, 1440p, 4K UHD (3840×2160), or custom.
- **Frame rate** — Typically 60fps, but configurable.
- **Motion blur** — 2×, 4×, or 8× temporal oversampling. Each frame is blended from multiple sub-frame renders, producing smooth cinematic motion on fast-rotating mandalas and feedback loops. More samples = more render time.
- **Output destination** — Where to save the file.

### What happens

The audio file is decoded up front and the audio analysis runs mathematically per frame — so the beat tracking, frequency bands, and envelope followers all behave exactly as they would in real time, just calculated offline. You get a frame-accurate render even on complex shaders.

A progress bar shows render percentage, elapsed time, estimated time remaining, and output file size estimate. There's also a live thumbnail so you can confirm it looks right before committing an hour to a 4K render.

---

## Hardware Settings & Persistence

Your audio routing, MIDI bindings, display preferences, and broadcast settings all save automatically to `lsd-settings.properties` in the app folder. You don't need to reconfigure anything between sessions.
