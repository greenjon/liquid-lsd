# Recording, Preset Saving & Video Export

Liquid LSD provides powerful tools for persisting performance presets, recording real-time master output with zero frame drops, rendering frame-perfect 4K/60fps offline videos with motion blur, and preserving hardware settings.

---

## 1. Preset & Bank Management

Liquid LSD saves and organizes visual synthesizer presets using human-readable JSON files. Presets encapsulate the entire parameter state, modulation matrix routings, LFO configurations, and geometric settings for both decks and the master mixer.

### File Hierarchy & Locations

```
presets/
├── factory/                 — Built-in read-only factory banks
│   ├── bank_a.json
│   └── bank_b.json
└── user/                    — User-created banks & custom presets
    ├── bank_user1.json
    └── bank_ambient.json
```

- **Active Directory**: Presets are loaded from and saved to the `presets/` directory relative to the application working directory.
- **Preset Banks**: Banks contain a grid of preset slots mapped to quick-selection buttons in the **Preset Grid** and can be triggered via MIDI Program Change or CC messages.
- **Preset Format**: Clean JSON serialization via Kotlinx Serialization. All floating-point parameter values, envelope curves, LFO frequencies/waveforms, and CV modulation amounts are preserved verbatim.

### Saving & Recalling Presets

1. **Saving from the UI**:
   - In the **Preset Grid**, select an empty slot or right-click an existing slot.
   - Choose **Save Preset** or click the preset disk icon.
   - Enter a descriptive name. The preset state is written asynchronously to disk via the `PresetIO` background thread without stalling the render engine.
2. **Exporting & Sharing**:
   - Presets can be copied to the system clipboard as JSON text (**Copy Preset JSON**) and pasted into chat, text editors, or another instance of Liquid LSD (**Paste Preset JSON**).
3. **Dirty Deck Indicators**:
   - When a parameter is modified on live Deck A or B, the deck title displays a dirty indicator (`*`). If a new preset is triggered while a deck has unsaved changes, the configured Auto-VJ queue policy (`SKIP`, `AUTO_SAVE`, or `AUTO_DISCARD`) determines how changes are handled.

---

## 2. Real-Time Video Recording (`REC`)

Liquid LSD features an integrated real-time video recorder capable of capturing live visual performances directly to high-bitrate MP4 video files with synchronized audio.

```
┌─────────────────┐       ┌──────────────────────┐       ┌─────────────────────┐
│  OpenGL Master  │ PBO   │ Bounded Frame Queue  │ Worker│   FFmpeg Subprocess │
│    Framebuffer  ├──────►│  (10 pre-allocated)  ├──────►│ (H.264/NVENC + AAC) │
└─────────────────┘ Async └──────────────────────┘ Thread└──────────┬──────────┘
                                                                    ▼
                                                             output_YYYYMMDD.mp4
```

### Key Features
- **Zero Render Stalls**: Uses asynchronous OpenGL Pixel Buffer Objects (PBO) ring buffers. Pixel readbacks occur concurrently on the GPU, avoiding blocking `glReadPixels` stalls on the primary render thread.
- **Synchronized Audio Capture**: Records raw floating-point audio directly from the audio input callback using a lock-free, zero-allocation SPSC (Single-Producer Single-Consumer) queue.
- **Background Transcoding**: Frames are streamed into an FFmpeg subprocess running asynchronously, outputting H.264 (or hardware-accelerated NVENC/QuickSync/VAAPI when available) at constant frame rates (e.g., 60 FPS).

### Starting a Live Recording

1. Open the top menu bar and select **Output → Record Master Output (REC)** (or press `Ctrl+R` / `Cmd+R`).
2. Alternatively, configure a MIDI controller button mapped to the `REC` toggle.
3. Once active, a prominent red **`REC mm:ss`** badge appears on the unified top title bar alongside a dropped-frame telemetry counter.
4. Click **Stop Recording** or toggle the shortcut again. The video and audio streams are finalized and muxed into the `recordings/` folder (e.g., `recordings/liquid_lsd_2026-09-01_20-15-00.mp4`).

---

## 3. Offline Render Studio (4K / 60 FPS)

For studio music videos, promotional visuals, and high-fidelity rendering, Liquid LSD includes a dedicated **Offline Render Studio**. Unlike live recording, offline rendering is **deterministic and non-realtime**: it decouples rendering from wall-clock time, allowing arbitrarily complex shaders to render at massive resolutions with sub-frame motion blur.

```
Offline Render Studio Configuration
─────────────────────────────────────────────────────────────────
Preset Source:      Active Performance Deck / Stored Preset
Audio File:         /path/to/track.wav (WAV / FLAC / MP3 / OGG)
Target Resolution:  3840 × 2160 (4K UHD)
Frame Rate:         60.0 FPS
Motion Blur:        4× Sub-Frame Accumulation (1/120s shutter)
Encoding Quality:   H.264 CRF 17 (Visually Lossless) + 320kbps AAC
─────────────────────────────────────────────────────────────────
[ Cancel ]                                       [ Start Export ]
```

### Capabilities

- **Deterministic DSP Simulation**: Decodes the entire audio file upfront into memory. The audio DSP engine (Biquad filters, RMS energy followers, beat clock flywheels) is stepped in precise, mathematical sample blocks per visual frame.
- **High-Resolution Support**: Render native 1080p, 1440p, 4K UHD (3840×2160), or custom ultrawide aspect ratios.
- **Sub-Frame Accumulation Motion Blur**: Generates intermediate sub-frame slices (e.g., 2×, 4×, 8× temporal oversampling) and blends them inside an internal HDR accumulation buffer. This produces cinematic, fluid motion trails on fast-rotating mandalas and geometric feedback loops.
- **Live Preview & ETA**: Displays real-time progress percentage, elapsed time, calculated ETA, output file size estimate, and a live rendering thumbnail texture.

### How to Render Offline

1. Open top menu: **Output → Export Video (Offline Studio)...**.
2. Select the audio soundtrack file (`.wav`, `.flac`, `.mp3`, or `.ogg`).
3. Choose the target preset or capture current active deck settings.
4. Select resolution (e.g. `1080p 60fps`, `4K 60fps`), oversampling multiplier, and output destination.
5. Click **Start Export**. The window will display live rendering progress while processing without freezing the OS window.

---

## 4. Hardware Settings & Configuration Persistence

Liquid LSD automatically saves your hardware configurations, audio routing, MIDI controller bindings, and broadcast preferences to disk.

- **Storage File**: `lsd-settings.properties` in the project root directory.
- **Persisted Settings**:
  - **Audio Settings**: Driver backend (`JACK` vs `JavaSound`), buffer sizes, selected input device.
  - **Broadcast Settings**: WebSocket relay URL, authentication token, target delta streaming FPS, and auto-connect flag.
  - **MIDI Mappings**: Hardware controller profiles, CC and Note bindings, channel configurations.
  - **UI & Display**: Theme colors, font scaling, secondary projector display coordinates, and fullscreen preferences.
