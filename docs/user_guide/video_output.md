# Zero-Copy Video Sharing & Inter-App Video Output

Liquid LSD supports live zero-copy video frame sharing to external VJ software, media servers, and live streaming tools (OBS Studio, Resolume Arena, TouchDesigner, MadMapper, qpwgraph) across Windows, macOS, and Linux.

---

## 1. Supported Platform Backends

- **Windows**: Spout2 (`SpoutLibrary.dll`)
- **macOS**: Syphon (`Syphon.framework`)
- **Linux**: PipeWire 0.3 (`libpipewire-0.3.so`) with DMA-BUF GPU export & `SPA_DATA_MemFd` fallback

---

## 2. Configuring Output Streams

Navigate to **Settings → Video Sharing Matrix**:

1. **Enable Endpoints**: Toggle output sharing independently for `Deck A`, `Deck B`, `Deck BG`, `Deck PV` (Preview), and `Master Output`.
2. **Custom Stream Names**: Assign custom server names (e.g. `LiquidLSD-Master`, `MainStage-Feed`).
3. **Resolution Rescaling**: Choose output target resolutions (`Sync to Master`, `1080p`, `720p`, `540p`).
4. **Scaling Mode**: Select aspect ratio conform modes (`Fit`, `Fill`, `Stretch`).

---

## 3. Linux PipeWire Setup & Routing

On Linux distributions (Ubuntu 22.04+, Debian 12+, Fedora 34+, Arch):

1. **OBS Studio**:
   - Add a **PipeWire Screen Capture** or **Window Capture** source.
   - Select the Liquid LSD video node (`Liquid LSD - LiquidLSD-Master`).
2. **Graph Routing (`qpwgraph` / `Helvum` / `pw-link`)**:
   - Run `qpwgraph` to inspect live video nodes.
   - Liquid LSD exposes video output ports under `Liquid LSD - <StreamName>`.
   - Wire video ports directly to downstream video sinks.
3. **Driver Telemetry & Video Ingest**:
   - Check **Settings → Video Sharing** status column for live driver telemetry (`PipeWire 0.3 (DMA-BUF Active)` or `PipeWire 0.3 (MemFd Fallback)`).
   - **Video Ingest**: Liquid LSD discovers live PipeWire video feeds (webcams, OBS virtual cameras, external video streams) via `ExternalVideoDiscovery`. Select **External Video** as a deck visual source to ingest live video natively on Linux.
