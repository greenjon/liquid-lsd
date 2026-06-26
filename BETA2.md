# Spirals Desktop - Beta 2 Release Notes

This document highlights all major features, improvements, refactorings, and bug fixes implemented since **Beta 1** (commit `6c0b71d`). Over 4,400 lines of code have been added, representing a massive leap in usability, stability, customization, and visual feedback.

---

## 1. Modulation System Refactoring (Amplitude & DC Offset)
* **Zero-Centered Waveforms**: Core CV signals have been refactored to be zero-centered where applicable, ensuring consistent, predictable modulation math.
* **Refactored Modulation Parameters**: Replaced the ambiguous `weight` parameter in `CvModulator` with two explicit, well-defined properties:
  * `amplitude` (controls the scale/depth of the modulation, range `0.0` to `1.0`).
  * `dcOffset` (sets the offset/center point of the modulation, range `-1.0` to `1.0`).
* **Cleaned DSP Logic**: Decoupled the parameter math and unified core DSP logic to support independent scaling and offset per modulator.

## 2. Advanced MIDI Profiles & Mapping Manager
* **Custom MIDI Mapping Profiles**: Introduced `MidiMappingManager`, allowing users to define, save, and switch between named MIDI mapping profiles (e.g., matching different physical hardware controllers).
* **Key-Based MIDI Learn**: Refactored the MIDI Learn target identification system to bind CC mappings by parameter key strings (`paramKey`) instead of direct JVM object references, preventing mapping breakage during patch loading or state changes.
* **MIDI Column**: Retained the dedicated MIDI column layout in the patch grid for direct learn interactions.

## 3. Setlist Advance & Live Mode Features
* **Setlist Navigation Triggers**: Added the ability to trigger setlist changes (Next/Prev) using physical MIDI CC messages, keyboard shortcuts, or user interface buttons.
* **Threshold-Based CV Triggers**: Integrated `setlistPrev` and `setlistNext` as standard `ModulatableParameter`s in the `Mixer`. This allows setlists to be advanced dynamically using CV signals—such as an LFO, beat clock, or audio amplitude envelope—when crossing a threshold (e.g., `0.5f`).
* **Setlist Transition Behaviors**: Introduced three selectable behaviors under a new **Setlist & Live Mode** configuration:
  * `PROMPT` (Default): Confirms before switching patches if there are unsaved changes.
  * `AUTO_DISCARD`: Discards unsaved changes instantly and advances.
  * `AUTO_SAVE`: Automatically saves changes to the current patch before loading the next. In autosave mode, newly created patches are silently named with a timestamp when moving to a new patch, so users can create and save patches live with no break in flow.

## 4. Preset Browser & Custom File Browser UI
* **Custom File Browser**: Built `ImGuiFileBrowser` from scratch, enabling native directory navigation, file selection, and custom file filters in ImGui without external desktop OS dependency.
* **Deck Preset Browser**: Added `DeckPresetBrowser` to support browsing, loading, saving, and deleting deck-level presets directly from the sidebar.
* **Setlist Panel**: Added a dedicated `SetlistPanel` for loading, listing, and playing through structured visual sets (setlists) in sequence.

## 5. Patch Grid UI Visual Polish
* **Hover Crosshairs**: Added a crosshair effect. Hovering over any cell or label highlights the entire row and column, making the intersection crystal clear.
* **Zebra Striping**: Alternating row backgrounds improve readability on long vertical lists.
* **Extended Grid Lines**: Extended vertical column lines all the way down the grid area to keep the layout aligned.
* **Color-Coded Subgroup Margins**: Left border colored lines (margin lines) distinguish subgroups visually (e.g., Geometry vs. Color vs. Feedback).
* **Categorized Colors**: Color-coded CV headers, active knobs, and cell backgrounds by their category (Audio-reactive = warm tints, Internal CVs = cool tints, etc.). Added matching background tints to Deck A and Deck B headers.
* **Startup Autocollapse Sync**: Subgroup autocollapse states (e.g., collapsing all geometry subgroups) are synchronized across decks, and top-level headers remain open by default. Now when you open "Geometry" (for example) on either deck, the same opens on the other deck.

## 6. Layout & Controls Polish
* **Read-Only Deck Controls**: Replaced direct parameter knobs with readouts in key areas of the Deck controls to prevent accidental manual adjustments during a show. It turns out that having CVs and humans control the same slider is a shit show.
* **Mixer Alignment**: Fixed alignment and spacing bugs, wrapping Deck A and Deck B Mixer parameters inside clean child windows to ensure zero Y-coordinate desynchronization.
* **Audio Engine Toggle**: Added a settings toggle to turn off the audio engine dynamically, which automatically hides audio-reactive columns in the patch grid to simplify the interface.
* **Clean Mode Toggle**: Pressing "f" toggles hiding UI overlays, useful for dialing in patches or when performing live.

## 7. Modulation Editor & Cell Oscilloscopes
* **Interactive Waveform Oscilloscopes**: Added real-time oscilloscopes inside a new panel, `CellConfigPanel`, that displays both the raw CV signal and the final scaled modulation output.
* **Custom Theme Colors**: Modulator range sliders and oscilloscopes now inherit the active theme color scheme.
* **Text Inputs for Range Sliders**: Added text fields next to range sliders to allow for precise numerical entry of base values and limits.

## 8. General UI Polish & Quality of Life
* **Title Dirty Indicator**: The window title now shows a dirty marker (`*`) whenever the current patch has unsaved changes.
* **Load Confirmation Modal**: Shows a warning modal preventing accidental loss of unsaved changes.
* **Keyboard Font Scaling**: Quick text size scaling at runtime via `CTRL-` (shrink) and `CTRL=` (grow).
* **Deck-to-Deck Copying**: Added direct "Deck A -> Deck B" and "Deck B -> Deck A" copy/paste buttons.
* **Clipboard Manager**: Improved row and cell copy-paste buffer reliability.

## 9. Built-in Documentation & Developer Resources
* **In-App Documentation Viewer**: Added a `DocManager` UI component to browse and read markdown docs directly inside the application.
* **MkDocs Architecture**: Configured a complete static site documentation framework (`mkdocs.yml` and `/docs`) detailing:
  * Build/install prerequisites and JVM packaging setup.
  * Audio DSP zero-allocation real-time rules.
  * Dual-deck OpenGL rendering and FBO ping-pong feedback loops.
  * JVM tuning flags for zero-pause garbage collection (ZGC).
