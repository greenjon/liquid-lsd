# Presets & MIDI Mapping

Liquid LSD provides preset serialization, preset clipboard management, and external MIDI hardware integration.

---

## Presets & Serialization

Presets store the complete state of a deck, including all visual parameters, feedback settings, active CV modulators, and user notes.

### File Format (`.lsd`)
- Presets are stored in `presets/` (and subfolders) and managed via the **Library** panel.

### Preset Clipboard (Copy & Paste)
- **Deck Parameter Copying**: You can copy base parameter settings from one deck and paste them onto another deck using right-click context menus in the `FINAL` column or Deck Controls.
- **Modulator Copying**: Copy individual `CvModulator` configurations or full cell setups between parameters.

---

## MIDI Controller Mapping Architecture

Liquid LSD cleanly separates physical hardware controller maps from visual performance presets.

### Hardware MIDI Profiles vs. Per-Preset MIDI Modulators

| Feature Scope | Storage Location | Purpose | Portability |
|---------------|------------------|---------|-------------|
| **Base Parameter Sliders** (e.g. Master Crossfader, Deck Gain) | Hardware Profile (`library/midi/default.json`) | Maps physical knobs to global base parameter controls | Swap physical MIDI controllers without editing visual presets |
| **Grid Cell Modulators** (MIDI Column in Preset Grid) | Visual Preset File (`.lsd`) | Binds specific MIDI CC numbers as dynamic modulation sources (`midi_cc_<ch>_<cc>`) | Preserved inside preset files across performances |

### MIDI Learn Mode
1. **Activate MIDI Learn**: Click the **MIDI Learn** button in the Patch Grid header or next to a parameter slider.
2. **Send CC Signal**: Move a knob, slider, or fader on your connected hardware MIDI controller.
3. **Automatic Binding**: Liquid LSD intercepts the incoming MIDI CC event, identifies channel and CC ID, and confirms binding automatically.
4. **Unbind**: Right-click any mapped slider or cell to clear the MIDI mapping.
