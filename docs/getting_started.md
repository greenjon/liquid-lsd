# Getting Started

Liquid LSD comes with everything it needs bundled in — no separate Java installation required.

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
|     PRESET GRID     |      CELL CONFIG      |     MIXER / OUT     |
| (CV Mod Matrix)     | (Parameters & LFOs)   | (Decks & Monitor)   |
+---------------------+-----------------------+---------------------+
```

Here's what to try first:

1. **Check the audio feed.** Look at the **Preset Grid** on the left — you should see the `AMP`, `BASS`, `MID`, and `HIGH` meters pulsing with your music. If nothing moves, check your audio routing (Step 2).

2. **Click anything.** Click a row in the Preset Grid to select it. The **Cell Config** panel in the middle will show you the controls for that parameter. Hover over any label to see a tooltip explaining what it does.

3. **Switch modes.** Press **`F3`** to toggle between **Performance Mode** (the live view you're in now) and **Asset Management Mode**, which opens the library, playlist editor, and shader browser.

That's it — you're in. Dig into [Your Workspace](user_guide/your_workspace.md) next to understand what you're looking at, or jump straight to [Modulation](user_guide/modulation.md) if you want to start wiring audio to visuals.
