# Release Notes

## Version 1.0.0-beta.19

> [!NOTE]
> **Release 1.0.0-beta.19** rolls up all major features, UI redesigns, performance optimizations, and under-the-hood architectural improvements completed since **v1.0.0-beta.17** (combining all developments across beta 18 and beta 19 into this major release).

---

### Key Highlights

- **`SessionContext` Dependency Injection Architecture**: Replaced global singletons across 28+ UI panels and core managers with explicit context injection for improved state isolation, testability, and modularity.
- **Dynamic UI Theming Engine**: Added support for customizable UI color themes and theme management in settings.
- **Resizable 3-Column Layout & Toggleable Mixer**: Upgraded main window layout engine with resizable column splits and toggleable Mixer Panel columns.
- **Redesigned PatchGrid & Seamless Cards**: Connected active side tabs to the parameter card container with seamless outlines, inline sub-tabs, and reordered parameter groups (Visual Source before FX).
- **Mandala Performance Surface**: Added quick lobe selection pills (2, 3, 4, 6, 8, 12) and a live recipe stepper in `FinalParamSection`.
- **CV-Triggered Parameter Randomization**: Added CV-modulatable trigger parameters (`Deck A/B/C Param Rand` and `All Decks Param Rand`) to trigger parameter randomization dynamically via audio envelopes, LFOs, or beat clocks.
- **Middle-Click & Scroll-Wheel Controls**: Added middle-click instant parameter reset, hover scroll-wheel parameter scrubbing (with `Shift` / `Ctrl+Shift` modifiers), and middle-click cell bypass in the PatchGrid matrix.

---

### 🛠️ Under-the-Hood Architectural Changes

#### 1. `SessionContext` Refactor
- Replaced direct singleton access across UI components (`UIManager`, `PatchGridPanel`, `CellConfigPanel`, `MixerMonitorPanel`, `AssetBrowserPanel`, `AudioEnginePanel`, `SettingsPanel`, `PopupManager`, etc.) with `SessionContext` dependency injection.
- Cleaned up coupling between the UI layer, parameter resolvers, and audio/CV registries.
- Updated UI developer documentation ([`docs/developer/ui.md`](developer/ui.md) and [`.agents/PROJECT.md`](../.agents/PROJECT.md)) to mandate `SessionContext` patterns.

#### 2. Deck Model & Metadata Refactoring
- Added full support for **Empty Deck States** across `Deck`, `PatchGridPanel`, `PatchGridTabs`, and rendering routines.
- Removed deprecated `sourceSelect` modulatable parameters from `Deck`.
- Cleaned up parameter range defaults (`defaultMin` and `defaultMax`) in visual source metadata definitions (`presets/sources/kifs/meta.json` and `presets/sources/mandala/meta.json`).

#### 3. Real-Time CV Parameter Randomization
- Added CV-triggerable randomization parameters:
  - `Deck A Param Rand`
  - `Deck B Param Rand`
  - `Deck C Param Rand`
  - `All Decks Param Rand`
- Allows performers to route audio transients, LFO spikes, or beat clock events directly to parameter randomization.

---

### 🎨 UI & UX Redesign

#### 1. Workspace Layout & Theming
- Built `UITheme` theming subsystem allowing custom theme selection and dynamic color token overrides.
- Implemented a resizable three-column UI layout with smooth drag handles and configurable panel column visibility.
- Improved `SettingsPanel` popup sizing, dynamic centering, and positioning rules.

#### 2. PatchGrid & Parameter Navigation Polish
- **Seamless Outline**: Connected side tabs seamlessly into the PatchGrid parameter container with card outline styling.
- **Inline Sub-Tabs & Alignment**: Relocated sub-tabs inline with column headers and aligned tab vertical offsets relative to column header heights.
- **Logical Category Grouping**: Moved Visual Source parameters ahead of FX controls and renamed the parameter category header from `Feedback` to `FX`.
- **Display Formatting**: Corrected `Rotate X` and `Rotate Y` parameter degree formatting in Mandala parameter tabs.

#### 3. Mandala Quick Performance Controls
- **Lobe Selection Pills**: Added quick-access pills (`2`, `3`, `4`, `6`, `8`, `12`) in `FinalParamSection` for instantly changing mandala lobe symmetry.
- **Recipe Stepper**: Added stepper buttons to quickly iterate through preset geometric recipes live during a performance.

#### 4. Performance & Media Browser Hardening
- Padded frame time values in `MenuBar` to 3 digits to eliminate layout jitter when frame times fluctuate.
- Reordered media browser sidebar to place **Patches** before **Playlists**.

#### 5. Power-User Mouse & Scroll Wheel Controls
- **Middle-Click Reset**: Middle-clicking any parameter slider, text box, `FINAL` knob cell, or row label resets the parameter to default instantly.
- **Hover Scroll Scrubbing**: Hovering over slider tracks or text input fields and turning the scroll wheel adjusts values dynamically (unmodified = `±0.001`, `Shift` = `±0.01`, `Ctrl+Shift` = `±0.1`).
- **Middle-Click Cell Bypass**: Middle-clicking CV or MIDI cells in PatchGrid toggles modulator `bypassed` state.

---

### 📜 Commit History (v1.0.0-beta.17 → v1.0.0-beta.19)

- `700495c` Refine PatchGridPanel card background and outline border styling
- `43157cc` ui: adjust left tabs vertical alignment relative to column header height
- `0eb72b1` ui: relocate sub-tabs inline and refine patchgrid tab styling
- `0079aff` Fix Rotate X and Rotate Y display in PatchGridTabs for Mandala and cleanup defaultMin/defaultMax preset metadata
- `d7cb193` Add CV-triggered randomization parameters for Decks A/B/C and All
- `69abb47` feat(ui): add quick lobe selection pills and recipe stepper for Mandala visual source
- `aa6af97` feat(ui): add support for empty deck state and refine patchgrid layout
- `ef84af5` Reorder PatchGrid parameters to show Visual Source before FX
- `7726d1f` Refine PatchGrid visual hierarchy and pad performance monitor
- `b61c883` docs: update UI developer documentation and PROJECT.md for SessionContext dependency injection
- `d80f487` ui: Adjust settings panel size and center positioning conditions
- `9117aa5` ui: Implement resizable three-column layout and toggleable mixer panel columns
- `fb19654` refactor(ui): introduce SessionContext and refactor UI components to use context instead of global singletons
- `0da9057` feat: implement UI theming support and theme configuration
