# Getting Started

Liquid LSD comes pre-packaged with its own embedded runtime and shader libraries. No external Java installation or system dependencies are required.

> **Building from source?** If you are compiling or hacking on the Kotlin/OpenGL codebase directly, see the repository README for Gradle build instructions.

---

## 1. Installation & Launch

Download the archive for your operating system and CPU architecture, unzip it, and launch:

* **macOS (Apple Silicon / M-Series)**: 
  Double-click `run-mac-arm.command`.
* **macOS (Intel)**: 
  Double-click `run-mac-intel.command`.
  *(Note: If macOS displays a Gatekeeper warning on first launch, right-click the script and select **Open**).*
* **Windows (x64)**: 
  Double-click `run-windows.bat`.
* **Linux (x64 / ARM64)**: 
  Open a terminal inside the folder and run:
  ```bash
  ./run-linux.sh

Seeing the actual directory structures clarifies the whole distribution model: you bundle a private `jre/`, which means **users don't even need to install Java (JDK 17)**. The launcher scripts handle the ZGC and platform-specific flags (`-XstartOnFirstThread`, etc.) automatically.

Here is the revised, accurate **`getting_started.md`** matching the release archives:

Markdown

```
# Getting Started

Liquid LSD comes pre-packaged with its own embedded runtime and shader libraries. No external Java installation or system dependencies are required.

> **Building from source?** If you are compiling or hacking on the Kotlin/OpenGL codebase directly, see the repository README for Gradle build instructions.

---

## 1. Installation & Launch

Download the archive for your operating system and CPU architecture, unzip it, and launch:

* **macOS (Apple Silicon / M-Series)**: 
  Double-click `run-mac-arm.command`.
* **macOS (Intel)**: 
  Double-click `run-mac-intel.command`.
  *(Note: If macOS displays a Gatekeeper warning on first launch, right-click the script and select **Open**).*
* **Windows (x64)**: 
  Double-click `run-windows.bat`.
* **Linux (x64 / ARM64)**: 
  Open a terminal inside the folder and run:
  ```bash  ./run-linux.sh
```

*(Optional: Run `./install-desktop.sh` to add Liquid LSD directly to your desktop environment's application menu).*

## 2. Audio Routing

Liquid LSD drives visual parameters and generative shaders via real-time audio analysis. Ensure audio is routed into the app before you perform:

- **macOS & Windows**: Out-of-the-box system capture. The app automatically hooks into your operating system's default input device. Start playback or select your interface/virtual loopback device in OS sound settings.

- **Linux (PipeWire / JACK)**: Sub-millisecond direct routing. Connect your hardware capture card or media player to `lsd:input_1` and `lsd:input_2` using Helvum, qjackctl, or `jack_connect`.

- **Linux (Fallback)**: If PipeWire/JACK is not running, Liquid LSD captures audio directly from the default ALSA/Pulse device.

## 3. First Launch Walkthrough

Once launched, the workstation opens into three primary panels:

```
+---------------------+-----------------------+---------------------+
|     PRESET GRID     |      CELL CONFIG      |     MIXER / OUT     |
| (CV Mod Matrix)     | (Parameters & LFOs)   | (Master Deck & Mon) |
+---------------------+-----------------------+---------------------+
```

1. **Verify the Audio Feed**: Look at the **Preset Grid** (left). The `AUDIO` bands (`AMP`, `BASS`, `MID`, `HIGH`) and `TRIGGER` meters will pulse in real time with your audio stream.

2. **Inspect & Tweak**: Click a cell in the grid to reveal its controls in the **Cell Config** panel (middle). Hover over parameter labels to view live engine readouts and tooltips.

3. **Toggle Layout**: Press **`F3`** to switch between **Performance Mode** (focused live show view) and **Asset Management Mode** (playlists, library browsers, and shader source tools).
