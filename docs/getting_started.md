# Getting Started

Liquid LSD comes with everything it needs bundled in — no separate Java installation required.

---

## System Requirements

Before downloading or running Liquid LSD, ensure your machine meets the minimum hardware and software requirements. Real-time procedural rendering across 4 visual decks and low-latency audio analysis requires modern 64-bit hardware and hardware-accelerated **OpenGL 3.3 Core Profile** support.

| Component | Minimum Specification | Recommended Specification |
| :--- | :--- | :--- |
| **Operating System** | **64-bit OS**:<br>• Linux (Ubuntu 20.04+, Debian 11+, Fedora 36+, Arch)<br>• macOS 11.0+ (Big Sur or newer)<br>• Windows 10 / 11 | **64-bit OS**:<br>• Linux x64 / ARM64 (Wayland or X11)<br>• macOS 13+ (Apple Silicon M-Series)<br>• Windows 11 (x64) |
| **GPU / Graphics** | **OpenGL 3.3 Core Profile hardware support**:<br>• Intel HD Graphics 3000 / 4000+ (Mesa 20+ on Linux; HD 4000+ on Windows), Iris, UHD, Xe, Arc<br>• AMD Radeon HD 5000+ (TeraScale 2), HD 7000+ (GCN), RX series, RDNA<br>• NVIDIA GeForce 8000/9000/GT 200+ (Tesla 2.0), GeForce GTX 400+ (Fermi/Kepler/Maxwell/Pascal/etc.)<br>• Apple Silicon M1+ or Metal/OpenGL 3.3+ Intel Macs<br>• 512 MB VRAM | Dedicated GPU with **2 GB+ VRAM**:<br>• NVIDIA GeForce GTX 1060 / RTX series<br>• AMD Radeon RX 580 / RX 6000+ series<br>• Apple Silicon M-Series |
| **Processor (CPU)** | **64-bit dual-core** with SSE4.1/AVX (Intel 2nd-Gen Core 2011+, AMD FX/Ryzen, Apple Silicon, modern 64-bit ARMv8) | **Quad-core or 6+ core** (Intel 8th-Gen+, AMD Ryzen 3000+, Apple Silicon M-Series) |
| **Memory (RAM)** | **4 GB RAM** | **8 GB – 16 GB RAM** |
| **Display** | **1280 × 720** (minimum enforced window limit) | **1920 × 1080 (Full HD)** or higher |
| **Audio** | • Linux: ALSA / PulseAudio (Java Sound)<br>• macOS / Windows: Default system audio input | • Linux: PipeWire (`pipewire-jack`) or JACK daemon<br>• macOS / Windows: Low-latency audio interface |

> [!WARNING]
> **Legacy Hardware Incompatibility (Intel Core 2 Duo / Intel GMA Graphics)**:
> Legacy systems such as **Intel Core 2 Duo / Core 2 Quad** laptops and desktops equipped with **Intel GMA integrated graphics** (GMA 950, 3100, X3100, 4500MHD) or 1st-Gen Intel HD Graphics (Arrandale/Clarkdale) **do not support OpenGL 3.3 Core Profile** (their hardware caps out at OpenGL 1.4 – 2.1). Running Liquid LSD on these systems will fail at launch with `Failed to create GLFW window`. 32-bit operating systems are also not supported.

---

## 1. Download & Launch

Download the archive for your platform, unzip it, and launch:

- **macOS (Apple Silicon / M-Series):** Double-click `run-mac-arm.command`.
- **macOS (Intel):** Double-click `run-mac-intel.command`.
  *(If macOS blocks it on first launch, right-click the file and select **Open** instead.)*
- **Windows (x64):** Double-click `run-windows.bat`.
- **Linux (x64 / ARM64):** Open a terminal in the folder and run:
  
  ```bash
  ./run-linux.sh
  ```
  
  Optional: run `./install-desktop.sh` to add Liquid LSD to your desktop app menu.

> **Building from source?** See the [README](https://github.com/greenjon/liquid-lsd) for Gradle build instructions.

---

## 2. Route Your Audio

Liquid LSD needs to hear your music. How you connect it depends on your platform:

- **macOS & Windows:** Works straight away. The app picks up whatever your OS default input device is — a mic, an audio interface, or a virtual loopback driver. Just start playing music and check that the app responds.

- **Linux (PipeWire / JACK):** Connect your audio source to `lsd:input_1` and `lsd:input_2` using Helvum, qjackctl, or `jack_connect`. This gives you sub-millisecond, sample-accurate routing and is the recommended setup on Linux.

- **Linux (fallback):** If PipeWire or JACK isn't running, Liquid LSD will capture from your default ALSA/PulseAudio input automatically.

---

## 3. Your First 60 Seconds

When the app opens, you'll see three main panels side by side:

```
+---------------------+-----------------------+---------------------+
|     PARAMETERS      |      PROPERTIES       |        MIXER        |
| (CV Mod Matrix)     | (Parameters & LFOs)   | (Decks & Output)    |
+---------------------+-----------------------+---------------------+
```

Here's what to try first:

1. **Check the audio feed.** Open **Preferences → Audio Hardware** (`Ctrl+P`) — you should see the `AMP`, `BASS`, `MID`, and `HIGH` meters pulsing with your music. If nothing moves, check your audio routing (Step 2).

2. **Click anything.** Click any cell in the Parameters panel to select it. The **Properties** panel in the middle will show you the controls for that parameter. Hover over any label to see a tooltip explaining what it does.

3. **Open the Library.** Press **`Space`** (when not in a text field) to raise the Library panel into view. This is where you browse presets, build playlists, and manage your play queues. Cycle through Full Height, Half Height, and Docked to find the layout that suits what you're doing.

That's it — you're in. Dig into [Your Workspace](user_guide/your_workspace.md) next to understand what you're looking at, or jump straight to [Modulation](user_guide/modulation.md) if you want to start wiring audio to visuals.
