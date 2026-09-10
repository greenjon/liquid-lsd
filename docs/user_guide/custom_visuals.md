# Custom Shaders (ISF) & External Video

Liquid LSD uses the open-standard **Interactive Shader Format (ISF v2.0)** for custom visual generators, filter effects, and crossfade transitions[cite: 3, 4]. Any standard ISF shader from platforms like [editor.isf.video](https://editor.isf.video) will load directly into the application with automatic UI controls and CV modulation[cite: 3, 4].

---

## 1. Adding ISF Shaders

To add shaders, drop `.fs` (fragment) or `.vs` (vertex) files into the appropriate library directory[cite: 4]:

* **Visual Generators**: Drop into `library/sources/` (or your OS user ISF directory)[cite: 4]. These appear in the Deck **Source** picker[cite: 3, 4].
* **Post-Processing Filters**: Drop into `library/filters/`[cite: 4]. These appear in the Deck **FX Slot 1** and **FX Slot 2** menus[cite: 3].
* **Mixer Transitions**: Drop into `library/transitions/`[cite: 3, 4]. These register in the **Master Mixer** crossfader menu[cite: 3].

Liquid LSD automatically parses the ISF JSON header inside the shader file, sets up your parameter sliders in the Cell Config panel, and exposes them to the CV modulation matrix[cite: 3, 4].

---

## 2. Live Coding & Hot-Reloading

You can write or tweak shaders live during a performance without restarting the app[cite: 4]:

* **Instant Hot-Reload**: Saving changes to any `.fs` or `.vs` file in an active directory triggers an immediate background compile[cite: 4].
* **Crash-Proof Sandbox**: Shaders are validated in an isolated compilation check before swapping into the live engine[cite: 4]. If your GLSL has syntax errors, Liquid LSD keeps the existing frame running smoothly on stage—no dropped frames, black screens, or UI lockups[cite: 4].
* **Save Debouncing**: An automatic 250ms debounce prevents mid-save write collisions from external code editors[cite: 4].

---

## 3. External Video Ingest (Spout, Syphon & PipeWire)

Liquid LSD can receive live video streams from external VJ suites, media servers, capture cards, or creative coding rigs (TouchDesigner, Resolume, OBS, notch)[cite: 4]:

1. In the target Deck's **SRC** tab, set the Visual Source to **External Video**[cite: 4].
2. Open the **Server** dropdown and pick your stream[cite: 4]:
   
   * **macOS**: Syphon servers[cite: 4].
   
   * **Windows**: Spout2 senders[cite: 4].
   
   * **Linux**: PipeWire video nodes[cite: 4].
3. The incoming feed routes directly through the deck pipeline—giving you full access to 3D transformations, dual ISF post-effects, and audio-reactive feedback loops[cite: 4].

---

## 4. Managing Shader Directories

Liquid LSD scans standard system directories on startup, but you can also mount external drives or custom git repos[cite: 4].

### Standard Search Paths

* **macOS**: `/Library/Graphics/ISF/` and `~/Library/Graphics/ISF/`[cite: 4]
* **Windows**: `C:\ProgramData\ISF\` and `%LOCALAPPDATA%\ISF\`[cite: 4]
* **Linux**: `/usr/share/isf/`, `/usr/local/share/isf/`, and `~/.local/share/isf/`[cite: 4]
* **Bundled Assets**: `library/sources/`, `library/filters/`, and `library/transitions/`[cite: 4]

### Custom Paths & Drive Health

Open **Settings -> Shader Locations** to manage folders at runtime[cite: 4]:

* **Origin Badges**: Easily distinguish between `Built-in`, `System`, `User`, and `Custom` directories[cite: 4].
* **Drive Health Indicators**: Displays folder status in real time (**Active** [green], **Missing** [orange warning for unmounted USB/SSDs], or **Unreadable** [red])[cite: 4].
* **Rescan Now**: Instantly re-indexes all enabled folders without restarting the workstation[cite: 4].
