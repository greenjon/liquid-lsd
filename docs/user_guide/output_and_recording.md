# Output & Recording

This page covers everything about where your visuals go: sending them to projectors and other apps, recording your live set, and exporting a polished high-res render afterwards.

---

## Fullscreen & Projector Output

The simplest way to output: press **`F`** for **Output View**. The UI disappears and the master output fills the screen. Press **`Esc`** to come back.

To make the whole window fullscreen with the normal UI still showing, press **`F11`** (again to restore the previous size and position). The two combine: `F11` for a fullscreen window, then `F` for Output View on top of it.

For a second display or projector, use **Preferences → Video & Display** to configure a secondary output window you can drag to the projector screen and go fullscreen there independently.

**Choosing the output screen:** the **Output → Secondary Output Window** item fills one monitor. By default (**Output → Output Monitor → Automatic**) that is the first monitor that is not the system's primary one. If the video lands on the wrong screen, which can happen on Wayland where there is no true primary monitor, pick your projector under **Output → Output Monitor**. The choice is remembered by monitor name.

**Output scaling:**
- If your shaders are demanding and your GPU is struggling (especially on a laptop), lower the internal render resolution in **Preferences → Video & Display**. Dropping from 1080p to 720p or 540p cuts GPU load significantly while the output still fills the screen.
- Use **Fit / Fill / Stretch** to handle non-standard aspect ratios — useful for LED walls or vintage 4:3 projectors.

---

## Sending Video to Other Apps (Spout / Syphon / PipeWire)

Liquid LSD can share its video feeds live to other VJ apps, media servers, and streaming tools — without any encoding overhead or latency.

Go to **Preferences → Video & Display → Live Video Sharing** and toggle which feeds you want to share:

- **Deck A, Deck B, Deck BG, Deck PV (Preview), Master Output** — enable any combination.
- Set a custom stream name for each (e.g. `LiquidLSD-Master`, `MainStage-Feed`).
- Choose a resolution for the shared stream, or keep it synced to the master.
- Pick how the image is scaled if the resolutions don't match (Fit, Fill, or Stretch).

The sharing method depends on your platform:

- **Windows:** Spout2
- **macOS:** Syphon
- **Linux:** PipeWire. Each shared feed is a video source node named after the stream (`LiquidLSD-MASTER` by default), sending RGBA frames through shared memory at the stream's size.

**On Linux:** any app that reads PipeWire video nodes can take the feed. With GStreamer, for example: `gst-launch-1.0 pipewiresrc target-object=LiquidLSD-MASTER ! videoconvert ! autovideosink`. Use `qpwgraph` or `Helvum` to see and route video nodes. OBS's PipeWire sources go through the desktop's screen and camera portals, which may not list an app's video node; this hasn't been tested yet. Changing the output size reconnects the stream, so readers may need to reconnect too.

**Status:** Check the **Preferences → Video & Display** panel for a live status indicator next to each shared feed showing which sharing method is active and whether it's working.

---

## Recording Your Set (REC)

Press **`Ctrl+R`** (or go to **Output → Record Output**) to start recording. A red `REC mm:ss` badge appears on the title bar while recording is active, along with a dropped-frame counter.

Press the same shortcut again, or choose **Record Output** again, to finish. The file is an MP4 named with the date and time (e.g. `liquid_lsd_20260901_201500.mp4`), saved in the recording folder set in Preferences, or else your Videos folder (`~/Videos/liquid-lsd` on Linux and Windows, `~/Movies/liquid-lsd` on macOS).

After you stop, the sound is added to the file in the background while you keep playing: the output monitor shows `SAVING...` and Record Output waits until it's done. For an hour-long set that takes a few minutes. If you quit meanwhile, the app finishes the file before it closes. Should adding the sound ever fail, the video is saved as usual and the sound stays next to it as a `.wav` file.

**What you get:** H.264 video at your render resolution and the recording frame rate from Preferences, with synchronized audio from your input. If the app renders slower than that rate, frames are repeated so the video still runs in real time with the sound. On capable hardware, NVENC, QuickSync, or VAAPI hardware encoding is used automatically.

You can also map the `REC` toggle to a MIDI controller button so you never have to touch the keyboard during a performance.

---

## Offline Render Studio

The Offline Render Studio is for making video content — music videos, promotional visuals, or anything where quality matters more than real-time speed. It renders frame by frame at whatever resolution and frame rate you like, fully decoupled from the clock.

Open it via **Output → Render Video (Offline)...**.

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

## Hardware Preferences & Persistence
 
Your audio routing, MIDI mappings, display preferences, and broadcast preferences all save automatically to `lsd-preferences.properties` in the app folder (with automatic backward compatibility for existing `lsd-settings.properties` files). You don't need to reconfigure anything between sessions.
