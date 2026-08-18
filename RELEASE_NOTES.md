# Liquid LSD — Release Notes

## Version 1.0.0-beta.26

> [!NOTE]
> **Release 1.0.0-beta.26** replaces the redundant floating `DeckPresetBrowser` modal with a clean, single-purpose `SavePresetModal` and removes orphaned deck UI code.

---

### Key Highlights

- **Dedicated SavePresetModal**: Replaced the floating `DeckPresetBrowser` popup with a clean, dedicated `SavePresetModal` for entering preset names and comma-separated tags directly when selecting "Save As..." (or "Save" on an untitled deck). Dynamic action titles (`Save Preset As`, `Rename / Edit Preset Tags`, `Duplicate Preset`) render cleanly in the ImGui modal title bar without redundant body text.
- **Context-Aware Metadata Naming**: Standardized preset creation terminology between live saving (`Save As...` on Deck toolbars to preserve active deck state) and library management (**"Rename / Edit Tags..."** & **"Duplicate Preset..."** in Asset Browser context menus to copy/edit files on disk).
- **Universal Overwrite Safety**: Added file existence detection and overwrite protection across all preset modal flows (`Save As...`, `Rename`, `Duplicate`). `Save As...` defaults to `${activeName}_copy` to create new files by default, and typing an existing file name prompts with an explicit amber warning badge and `[ Overwrite ]` confirmation button.
- **Asset Browser New Preset Creation**: Added a dedicated `[Create new preset...]` row with `[ A ] [ B ] [ C ]` buttons positioned above the preset list in the Asset Browser. Clicking a deck button ejects/resets the deck (handling dirty states according to user preferences) and switches Preset Grid focus directly to that deck.
- **Empty Deck Launchpad Font Scaling**: Fixed empty deck launchpad card, margins, button dimensions, and frame rounding to scale dynamically with font size (`UITheme.baseSize` / `fontScale`), and migrated raw multi-byte button emojis to standard Lucide icons.
- **Cell Config Tab Row Scaling**: Fixed hardcoded height and width values on the CV tab buttons in `CellConfigPanel` and quick-select pills in `FinalParamSection` to dynamically scale based on font line height and text metrics.
- **Redundant Browser Removal**: Removed `DeckPresetBrowser.kt` and its duplicated search, tag filtering, and preset list UI; preset browsing and loading remain centralized in the docked **Asset Browser** (`F3`).
- **Deck Control Cleanup**: Removed orphaned `drawDeckPresetDropdown` code, renamed `drawDeckBottomBar` to `drawDeckMonitorToolbar`, and streamlined deck preview headers to act as clean display indicators with rich tooltips.
- **Mixer Monitor Left-Click Deck Focus**: Left-clicking any deck preview monitor (`Deck A`, `Deck B`, or `Deck C`) in the Mixer Monitor panel now directly focuses the Preset Grid to that deck (`activeTopTab`), complementing existing click-and-drag deck routing and preset drop loading.
- **Preset Grid Knob Indicators**: Refined circular knob meters across `MONOPOLAR`, `BIPOLAR`, `ENDLESS`, and `DISCRETE` modes in `PresetGridRenderer` by replacing the solid value circle with an elongated inward radial needle pointer (`trackRadius * 0.3f`), boosting background track arc/circle brightness for clear visual differentiation between endless 360° and open 270° tracks, and replacing the base preset value dot with a vibrant yellow cross-track tick mark (`trackRadius - 3.5px` to `trackRadius + 2.5px`).

---

## Version 1.0.0-beta.25

> [!NOTE]
> **Release 1.0.0-beta.25** overhauls the real-time beat detection engine, phase tracking flywheel, and beat sine generator to deliver stable, responsive, and phase-aligned beat synchronization.

---

### Key Highlights

- **Spectral Flux Onset Beat Analysis**: Replaced raw RMS amplitude beat input with half-wave rectified multi-band spectral flux. Beat detection now operates on sharp transient impulses, preventing false triggers on sustained drones or synth bass notes.
- **Sub-Block Parabolic Peak Interpolation**: Added parabolic peak interpolation to STFT Comb and Autocorrelation analysis tasks. Eliminates discrete block-quantization BPM jumps (e.g. 117.4 $\leftrightarrow$ 120.2) for smooth, floating-point tempo tracking.
- **Background Phase Anchor Alignment**: Background analysis now computes cross-correlation beat phase alignment anchors, ensuring beat-synced oscillators (`beatSine`, beat LFOs) lock their peaks directly to audio transient hits.
- **Dual-Time-Constant Peak-Triggered PLL**: Refined Phase-Locked Loop (`BeatDetectionMode.PLL`) to evaluate only on local onset peaks with fast phase correction ($\alpha$) and damped period inertia ($\beta$), eliminating tempo wobble on syncopated hits.
- **Smooth Flywheel Phase Slewing**: Upgraded `AudioEngine` flywheel accumulation to apply second-order phase slewing, smoothly nudging beat phase over audio blocks without instantaneous phase jumps or visual pops.
- **Beat Detection Confidence Metric**: Added a peak-to-average energy confidence metric $C \in [0, 1]$ to stabilize tempo during ambient breakdowns or silent sections.

---

## Version 1.0.0-beta.24

> [!NOTE]
> **Release 1.0.0-beta.24** streamlines the codebase by removing legacy migration logic, obsolete file fallbacks, backwards compatibility shims, and deprecated helpers.

---

### Key Highlights

- **Codebase Streamlining & Legacy Code Removal**: Removed legacy backwards compatibility shims across data models, serialization, session management, and UI browsers.
- **Direct DTO Serialization**: Standardized `ModulatorDto` serialization to directly serialize `depth`, `depthMin`, `depthMax`, and `randomizeDepth` without legacy `@SerialName("weight")` aliases.
- **Clean Preset Loading**: Removed obsolete parameter name mapping (`Scale` -> `Zoom`, etc.), legacy Mandala recipe auto-calculation fallbacks, and monopolar-to-bipolar version branching.
- **Unified Path Resolution**: Removed obsolete `presets/patches` and `.json` extension fallbacks; all user assets resolve directly and cleanly from the unified `library/` folder (`library/presets/*.lsd`, `library/midi/*.json`, `library/playlists/*.lsdset`).
- **Clean Settings Persistence**: Removed `spirals-settings.properties` migration code and deprecated font helper functions in `UITheme`.

---

## Version 1.0.0-beta.23

> [!NOTE]
> **Release 1.0.0-beta.23** introduces industry-standard "Preset" terminology, unified `library/` folder storage structure, automatic legacy directory migration, and full backward compatibility.

---

### Key Highlights

- **Frame-Synced LFO Generator (LFO 1 & LFO 2)**: Added a third frequency clocking mode, `FRAME`, alongside `TIME` and `BEAT` in the unified LFO generator. Frame-synced LFOs oscillate deterministically based on elapsed render frame count (1 to 10,000 integer frames), enabling artifact-free feedback buffer harmonization, per-frame stroboscopic/flicker effects, sample-and-hold per-frame noise, and deterministic video frame captures. Both primary carrier (LFO 1) and modulator (LFO 2) support independent frame sync with integer-locked range sliders and duration readouts (e.g. `120 frames (2.00s)`).
- **Mixer Monitor Vertical Scrollbar Elimination & Responsive Scaling**: Overhauled `MixerMonitorLayoutCalculator` to comprehensively calculate non-aspect vertical chrome (master crossfader controls, Deck A/B preset bars, Deck C preset bar, separator bands, and sub-pixel safety margins). Removed unconditional scrollbar width reservations and faulty cursor overshoot logic in `MixerMonitorPanel`. Monitor preview heights now automatically scale to fit the available pane height without displaying vertical scrollbars, while preserving 16:9 aspect ratios and utilizing the full pane width. Scrollbars appear only when window height drops below minimum preview dimensions on extremely small screens.
- **Robust Splitter Hover & Drag Interaction**: Replaced dummy ImGui splitter windows with direct mouse hit-testing and foreground draw list rendering, adding `ImGuiWindowFlags.NoBringToFrontOnFocus` to docked layout panels. This prevents panels from capturing mouse hover or occluding the splitters in the window z-stack, ensuring the double-arrow resize cursor and drag interactions between panels remain active at all times.
- **CV Modulation "Depth" Terminology Standardization**: Standardized the term for the value assigned to a CV modulator from "amplitude" (and legacy JSON "weight") to **"Depth"** across domain models (`CvModulator.depth`, `depthMin`, `depthMax`, `randomizeDepth`), evaluation logic, UI controls (Cell Config Depth range slider, LFO 2 AM Depth mode/tooltips), and documentation, while maintaining full backward serialization compatibility with existing presets.
- **Preset Grid Resolution Independence & User-Adjustable Knob Scale**: Replaced hardcoded cell pixel dimensions with a dynamic `GridMetrics` geometry token system that scales Preset Grid cells and circular readout knobs automatically with global UI font size (`baseSize`). Added a **"Grid Knob Cell Scale"** setting slider (0.70x to 2.00x) in **Settings -> Preset Grid** for resolution-independent visual customization across display setups (e.g. 1080p, 1280x800, 1440p, 4K).
- **Industry Standard 'Preset' Terminology Refactor**: Refactored visual parameter snapshots across the codebase from 'Patch' to 'Preset' (`PresetManager`, `DeckPresetDto`, `GlobalPresetDto`, `PresetGridPanel`, `PresetGridState`, `PresetGridRenderer`).
- **Unified `library/` User Storage Directory**: Renamed the top-level user data directory from `presets/` to `library/` (`library/presets/`, `library/midi/`, `library/playlists/`, `library/sources/`, `library/last_session.json`), establishing `library/` as the single root directory for all user content.
- **Automatic Migration & Seamless Backward Compatibility**: Implemented automatic disk migration logic that moves any legacy `presets/` directory contents to `library/` on application startup, alongside typealiases (`PatchManager`, `PatchGridPanel`, `DeckPatchDto`, `GlobalPatchDto`) and fallback file resolution paths for legacy preset JSON files.

---

## Version 1.0.0-beta.22

> [!NOTE]
> **Release 1.0.0-beta.22** rolls up all recent UI enhancements, media browser auto-refresh, Settings font scaling dynamic adjustments, monitor overlay badges, and playlist menu bar streamlining into right-click context menus.

---

### Key Highlights

- **Complete Save/Load & Session Persistence Overhaul**: Resolved preset save failures, parameter slider dirty state detection flaws (`ParameterDto.equals`), disk filename vs DTO name alignment, Deck C preset routing, false startup queue advance triggers when AutoVJ is disabled, and `lsd-settings.properties` persistence for font size, column visibility toggles, and clean mode.
- **New `Dynamic Spiral` Visual Source**: Introduced a multi-point logarithmic spiral generator with integrated phase tracking for glitch-free speed/shear modulation and fragment shader radial culling for high-FPS rendering.
- **`SessionContext` Dependency Injection Architecture**: Refactored UI panels and core managers to use `SessionContext` dependency injection for improved modularity, testability, and state isolation.
- **PatchGrid Expressive Controls**: Added middle-click parameter reset to default, modulator bypass toggles, scroll-wheel hover guards, mandala lobe selection pills (`2`, `3`, `4`, `6`, `8`, `12`), and live recipe steppers.
- **Dynamic UI Theming Engine**: Added customizable UI color themes, base font scaling hotkeys (`Ctrl-` / `Ctrl=`), clean mode (`F`), and persistent theme settings in `lsd-settings.properties`.
- **Resizable 3-Column Layout & Seamless Cards**: Added resizable workspace column splits, toggleable Mixer Panel columns, and connected side tabs into parameter cards with inline sub-tabs.
- **CV-Triggered Parameter Randomization**: Added CV-modulatable trigger parameters (`Deck A/B/C Param Rand` and `All Decks Param Rand`) for real-time audio- or beat-driven parameter randomization.
- **Typography Font Sizing & Layout Alignment Overhaul**: Increased global base font limit from 28px to 36px. Added automatic vertical resizing for the main Menu Bar, Master Controls (Crossfader/Fade Speed), and Deck A/B monitors with patch name labels. Resolved UI text clipping and vertical misalignment across large font sizes, including left/sub tab button heights, Cell Config "Current" slider readouts, Mixer Monitor preset Save & Eject icons, Deck A/B header vertical padding, and Playlist preview buttons A, B, C.
- **Deck Preview Monitor Area Redesign**: Combined the top preset header row and bottom patch label across Deck A, Deck B, and Deck C preview monitors into a unified interactive bottom bar (`[Save Button] [Eject Button] [Preset Bar]`). Removed top header clutter above monitors to maximize visual render preview size. Added dynamic vertical scaling for font zoom and pixel-perfect bottom baseline alignment between buttons and the Preset Bar.
- **Media Browser Live Auto-Refresh**: Added real-time filesystem change monitoring across `AssetBrowserPanel`, `DeckPresetBrowser`, and `ImGuiFileBrowser`, removing redundant manual Refresh buttons and automatically updating file listings when on-disk files change.
- **Playlist Menu Bar Streamlining**: Removed action buttons ("Rename Playlist", "Delete Playlist", "Clone Playlist", "Add to queue", "Save") from the playlist editor menu bar in Media Browser and consolidated them into the right-click context menu.
- **Toolchain Upgrade**: Upgraded build toolchain to Gradle **9.7.0**.

---

### 🛠️ Detailed Update Summary (v1.0.0-beta.17 → v1.0.0-beta.20)

#### 1. Save/Load & Session Persistence Overhaul
- **Session State Fix**: Prevented empty decks from restoring stale sources (such as KIFS) on application restart. Empty decks now serialize and restore cleanly with default Mandala baseline.
- **Dirty State Tracking**: Corrected `ParameterDto.equals()` and `ModulatorDto.equals()` so static parameter base value slider edits correctly mark the deck dirty (`isDeckDirty = true`) and display dirty indicators (`*`).
- **Filename vs DTO Alignment**: Aligned loaded DTO names with disk filenames in `loadDeckPresetAsync()`, ensuring **Save** updates the exact file loaded rather than fallback DTO names.
- **Deck C Routing**: Plumbed `isDeckC` routing through preset loading, active preset resolution, and save flows.
- **Startup Queue Trigger Fix**: Prevented false queue advance triggers on startup when `queueNext` base values restore from `last_session.json`. Suppressed automated CV queue advance triggers when AutoVJ is disabled.
- **Filename Sanitization**: Sanitized preset filenames (removing redundant `.lsd`/`.json` suffixes) and automatically cleaned up obsolete legacy `.json` files upon `.lsd` save.

#### 2. Dynamic Spiral Visual Source & Shader Optimizations
- Added `DynamicSpiral` visual source with parameters for points, scale, damping, wave frequency/amplitude, shear, speed, dot size, glow, hue offset/sweep, and trail decay.
- **Integrated Phase Tracking**: Added continuous phase integration across speed and shear changes to prevent visual popping or phase jump glitches during modulation.
- **Shader Culling**: Implemented per-fragment radial range culling in `dynamic_spiral.frag` to bypass out-of-bounds fragment calculations.
- **Tuned Ranges**: Optimized point cloud distribution and default parameter ranges for smooth rendering.

#### 3. PatchGrid Performance Controls
- **Middle-Click Reset**: Middle-clicking any parameter control in the Patch Grid instantly resets its value to default.
- **Modulator Bypass Toggle**: Added a bypass toggle for individual modulators in parameter cards without requiring modulator deletion.
- **Scroll-Wheel Hover Guard**: Prevented parent panel scroll wheel conflicts when hovering over parameter sliders.
- **Mandala Lobe Pills & Stepper**: Added quick lobe selection pills (`2`, `3`, `4`, `6`, `8`, `12`) and a recipe stepper in `FinalParamSection`.
- **CV Parameter Randomization**: Added CV-triggerable parameters `Deck A Param Rand`, `Deck B Param Rand`, `Deck C Param Rand`, and `All Decks Param Rand`.

#### 4. UI Architecture & Dynamic Theming
- **`SessionContext` Refactor**: Replaced singleton access across 28+ UI panels with `SessionContext` dependency injection.
- **UI Theme Engine**: Built `UITheme` theming subsystem for dynamic UI color themes, clean mode (`F`), font scaling, and persistent settings.
- **Auto-Resizing Workspace Layout**: Implemented auto-fitting Left Panel (Patch Grid) width based on active CV columns and font zoom (`CTRL-` / `CTRL=`). Rebalanced vertical splitters so Splitter 1 is a static auto-repositioning divider and Splitter 2 resizes Middle vs Right panels.
- **MIDI Cell Tooltip Fix**: Resolved pre-existing tooltip overlay bug where hiding the MIDI column caused the `FINAL` cell tooltips to be covered by the hidden MIDI cell.
- **Seamless Cards**: Connected active side tabs seamlessly into parameter cards with inline sub-tabs and logical category grouping (Visual Source before FX).
- **Empty Deck Handling**: Added full support for inert empty deck states across `Deck`, `PatchGridPanel`, `PatchGridTabs`, and render loops.

#### 5. Build & Toolchain
- Upgraded Gradle wrapper to **Gradle 9.7.0**.
- Added middle-click flags and input guards to `PatchGridRenderer`.

---

### 📜 Commit History (v1.0.0-beta.17 → v1.0.0-beta.20)

- `bb03c2d` fix(patches): resolve save/load, preset name alignment, and startup queue advance bugs
- `1b85a39` feat(spiral): implement integrated phase tracking for smooth speed and shear transitions
- `ec093ff` perf(shader): implement per-fragment radial range culling in Dynamic Spiral
- `114abfc` Optimize dynamic spiral point distribution, fix hotkey text input guard, adjust default fbDecay, and add patch tags scratchpad plan
- `7973616` fix: tune Dynamic Spiral parameter ranges
- `5b5b082` build & ui: upgrade Gradle wrapper to 9.7.0 and enable middle-click flags in PatchGridRenderer
- `def9c6b` feat: add Dynamic Spiral visual source
- `c12c23e` ui: refine PatchGrid stroke layout and middle-click/bypass behaviors
- `08c4cb6` Implement middle-click parameter reset, modulator bypass toggle, and quick-creation in Patch Grid; prevent panel scroll-wheel conflict when hovering sliders; update docs and release notes
- `c9c9af6` docs: add beta 19 release notes detailing changes since v1.0.0-beta.17
- `700495c` Refine PatchGridPanel card background and outline border styling
- `43157cc` ui: adjust left tabs vertical alignment relative to column header height
- `0eb72b1` ui: relocate sub-tabs inline and refine patchgrid tab styling
- `0079aff` Fix Rotate X and Rotate Y display in PatchGridTabs for Mandala and cleanup defaultMin/defaultMax preset metadata
- `d7cb193` Add CV-triggered randomization parameters for Decks A/B/C and All
- `69abb47` feat(ui): add quick lobe selection pills and recipe stepper for Mandala visual source
- `aa6af97` feat(ui): add support for empty deck state and refine patchgrid layout
- `ef84af5` Reorder PatchGrid parameters to show Visual Source before FX
- `7726d1f` Refine PatchGrid visual hierarchy and pad performance monitor
- `f37ce6b` feat(ui): connect active side tab to patch grid parameter container with seamless border outline
- `b61c883` docs: update UI developer documentation and PROJECT.md for SessionContext dependency injection
- `d80f487` ui: Adjust settings panel size and center positioning conditions
- `9117aa5` ui: Implement resizable three-column layout and toggleable mixer panel columns
- `fb19654` refactor(ui): introduce SessionContext and refactor UI components to use context instead of global singletons
- `0da9057` feat: implement UI theming support and theme configuration
